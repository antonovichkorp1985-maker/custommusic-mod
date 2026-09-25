package com.obninsk.custommusic.music;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Собственный потоковый декодер Ogg Vorbis -> PCM (16 bit, little-endian).
 *
 * <p>Зачем он нужен: библиотека {@code com.googlecode.soundlibs:vorbisspi} (javazoom) не
 * обновлялась с 2012 года и на современных JVM (Java 17/21) отдаёт поток, из которого
 * читается 0 байт, т.е. OGG фактически молча не играется. Формат файла при этом
 * определяется правильно (VORBISENC), поэтому проблема не видна до попытки чтения.
 *
 * <p>Здесь используется тот же самый декодер JOrbis ({@code com.jcraft.jorbis}),
 * который уже лежит в fat jar, но напрямую, по классической схеме
 * SyncState -> StreamState -> Packet -> Block -> DspState -> PCM.
 *
 * <p>Всё декодирование ленивое: данные читаются по страницам Ogg по мере запроса,
 * поэтому трек не грузится в память целиком.
 */
public final class OggVorbisDecoder {

    private OggVorbisDecoder() {
    }

    /** Быстрая проверка магических байтов "OggS". */
    public static boolean isOgg(File file) {
        if (file == null || !file.isFile()) return false;
        try (InputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int read = 0;
            while (read < 4) {
                int n = in.read(magic, read, 4 - read);
                if (n < 0) break;
                read += n;
            }
            return read == 4 && magic[0] == 'O' && magic[1] == 'g' && magic[2] == 'g' && magic[3] == 'S';
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Открывает .ogg как {@link AudioInputStream} с PCM_SIGNED 16 bit.
     *
     * @throws IOException если файл не является Ogg Vorbis (например Ogg Opus / Ogg FLAC)
     *                     или повреждён - вызывающий код может после этого попробовать SPI.
     */
    public static AudioInputStream open(File file) throws IOException {
        VorbisPcmInputStream pcm = new VorbisPcmInputStream(file);
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                pcm.getSampleRate(),
                16,
                pcm.getChannels(),
                pcm.getChannels() * 2,
                pcm.getSampleRate(),
                false);
        return new AudioInputStream(pcm, format, AudioSystem.NOT_SPECIFIED);
    }

    // ------------------------------------------------------------------

    private static final class VorbisPcmInputStream extends InputStream {

        private static final int OGG_BUFFER = 16384;
        private static final int CONV_FRAMES = 4096;

        private final InputStream in;

        private final SyncState sync = new SyncState();
        private final StreamState stream = new StreamState();
        private final Page page = new Page();
        private final Packet packet = new Packet();
        private final Info info = new Info();
        private final Comment comment = new Comment();
        private final DspState dsp = new DspState();
        private final Block block;

        private final float[][][] pcmf = new float[1][][];
        private final int[] pcmIndex;
        private final int channels;
        private final int sampleRate;
        private final byte[] convBuffer;

        private final ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        private byte[] pending;
        private int pendingPos;
        private int pendingLen;
        private boolean eof;
        private boolean closed;

        VorbisPcmInputStream(File file) throws IOException {
            this.in = new BufferedInputStream(new FileInputStream(file), 32768);
            this.sync.init();
            readHeaders();
            this.channels = Math.max(1, info.channels);
            this.sampleRate = info.rate > 0 ? info.rate : 44100;
            this.pcmIndex = new int[channels];
            this.convBuffer = new byte[2 * channels * CONV_FRAMES];
            if (dsp.synthesis_init(info) != 0) {
                close();
                throw new IOException("Cannot initialise Vorbis DSP state");
            }
            this.block = new Block(dsp);
        }

        int getChannels() {
            return channels;
        }

        int getSampleRate() {
            return sampleRate;
        }

        // --- Ogg/Vorbis headers (identification, comment, setup) ----------
        private void readHeaders() throws IOException {
            if (!fill()) throw new IOException("Empty or unreadable file");

            // first page
            int result;
            while ((result = sync.pageout(page)) != 1) {
                if (!fill()) throw new IOException("Not an Ogg stream");
            }
            stream.init(page.serialno());
            info.init();
            comment.init();
            if (stream.pagein(page) < 0) throw new IOException("Bad first Ogg page");

            int got = 0;
            if (stream.packetout(packet) != 1) throw new IOException("Bad first Vorbis packet");
            if (info.synthesis_headerin(comment, packet) < 0) throw new IOException("Not an Ogg Vorbis stream");
            got = 1;

            while (got < 3) {
                while (got < 3) {
                    result = sync.pageout(page);
                    if (result == 0) break;      // need more data
                    if (result != 1) continue;   // lost sync - data already consumed
                    stream.pagein(page);
                    while (got < 3) {
                        int packetResult = stream.packetout(packet);
                        if (packetResult == 0) break;      // need more data
                        if (packetResult == -1) continue;  // corrupt packet, skip
                        if (info.synthesis_headerin(comment, packet) < 0) {
                            throw new IOException("Corrupt Ogg Vorbis header #" + (got + 1));
                        }
                        got++;
                    }
                }
                if (got >= 3) break;
                if (!fill()) throw new IOException("Unexpected end of file in Vorbis headers");
            }
        }

        /** Дочитывает данные файла в буфер SyncState. false == EOF. */
        private boolean fill() throws IOException {
            int offset = sync.buffer(OGG_BUFFER);
            int read = in.read(sync.data, offset, OGG_BUFFER);
            if (read <= 0) {
                sync.wrote(0);
                return false;
            }
            sync.wrote(read);
            return true;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n <= 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) throw new IOException("Stream closed");
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;

            if (pending == null || pendingPos >= pendingLen) {
                if (!decodeChunk()) return -1;
            }
            int available = pendingLen - pendingPos;
            int n = Math.min(available, len);
            System.arraycopy(pending, pendingPos, b, off, n);
            pendingPos += n;
            return n;
        }

        @Override
        public int available() {
            if (pending == null) return 0;
            return Math.max(0, pendingLen - pendingPos);
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) return 0;
            // обычное "прочитать и выбросить" - seek в Ogg Vorbis всё равно требует декодирования
            long left = n;
            byte[] discard = new byte[(int) Math.min(65536, Math.max(1, n))];
            while (left > 0) {
                int read = read(discard, 0, (int) Math.min(discard.length, left));
                if (read < 0) break;
                left -= read;
            }
            return n - left;
        }

        /** Декодирует страницы до тех пор, пока не появятся PCM-данные или не кончится файл. */
        private boolean decodeChunk() throws IOException {
            chunk.reset();
            while (chunk.size() == 0 && !eof) {
                int result = sync.pageout(page);
                if (result == 1) {
                    decodePage();
                    continue;
                }
                if (result == -1) {
                    continue; // потеря синхронизации, данные уже съедены
                }
                if (!fill()) {
                    eof = true;
                }
            }
            if (chunk.size() == 0) return false;
            pending = chunk.toByteArray();
            pendingPos = 0;
            pendingLen = pending.length;
            return true;
        }

        private void decodePage() {
            stream.pagein(page);
            boolean pageEos = page.eos() != 0;

            while (true) {
                int result = stream.packetout(packet);
                if (result == 0) break;      // нужна следующая страница
                if (result == -1) continue;  // битый пакет

                if (block.synthesis(packet) != 0) continue;
                if (dsp.synthesis_blockin(block) != 0) continue;

                int samples;
                while ((samples = dsp.synthesis_pcmout(pcmf, pcmIndex)) > 0) {
                    float[][] pcm = pcmf[0];
                    if (pcm == null) break;
                    int frames = Math.min(samples, CONV_FRAMES);
                    for (int ch = 0; ch < channels; ch++) {
                        if (ch >= pcm.length) break;
                        int ptr = ch * 2;
                        int mono = pcmIndex[ch];
                        float[] channelData = pcm[ch];
                        for (int i = 0; i < frames; i++) {
                            int pos = mono + i;
                            if (pos >= channelData.length) break;
                            int value = (int) (channelData[pos] * 32767.0f);
                            if (value > 32767) value = 32767;
                            if (value < -32768) value = -32768;
                            if (value < 0) value = value | 0x8000;
                            convBuffer[ptr] = (byte) value;
                            convBuffer[ptr + 1] = (byte) (value >>> 8);
                            ptr += 2 * channels;
                        }
                    }
                    chunk.write(convBuffer, 0, 2 * channels * frames);
                    dsp.synthesis_read(frames);
                }
            }

            if (pageEos) eof = true;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                block.clear();
            } catch (Throwable ignored) {
            }
            try {
                dsp.clear();
            } catch (Throwable ignored) {
            }
            try {
                stream.clear();
            } catch (Throwable ignored) {
            }
            try {
                sync.clear();
            } catch (Throwable ignored) {
            }
            try {
                info.clear();
            } catch (Throwable ignored) {
            }
            // Comment.clear() в jorbis не публичный - освобождать там нечего
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
