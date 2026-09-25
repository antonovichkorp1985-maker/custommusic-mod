package com.obninsk.custommusic.music;

import com.obninsk.custommusic.CustomMusicMod;
import com.obninsk.custommusic.config.ModConfig;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ядро плеера - декодирует MP3/WAV/FLAC/OGG через Java Sound SPI и играет через SourceDataLine.
 * Потоковая архитектура с паузой/громкостью/перемоткой.
 *
 * Для DSF: экспериментальный путь - либо пытаемся прочитать как PCM (если библиотека конвертирует),
 * либо сообщаем что формат пока не поддерживается.
 */
public class AudioPlayerManager {
    private static final AudioPlayerManager INSTANCE = new AudioPlayerManager();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CustomMusic-Player");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private final AtomicReference<Track> nowPlaying = new AtomicReference<>(null);
    private volatile SourceDataLine currentLine;
    private volatile AudioFormat currentFormat = null;
    private volatile float volume = 0.7f;

    // all accessed from player thread and UI thread - must be volatile
    private volatile long startOffsetSeconds = 0L;
    private volatile long bytesWrittenTotal = 0L;
    private volatile long playbackStartMillis = 0L;
    private volatile long pausedAccumulatedMillis = 0L;
    private volatile long pauseStartMillis = 0L;

    private volatile long playbackId = 0L;
    private volatile long currentPlaybackId = 0L;

    private final Object pauseLock = new Object();

    public interface PlaybackListener {
        void onTrackStarted(Track track);
        void onTrackFinished(Track track);
        void onError(Track track, String error);
        void onPaused(Track track);
        void onResumed(Track track);
    }

    private PlaybackListener listener;

    private AudioPlayerManager() {
        volume = (float) ModConfig.defaultVolume();
    }

    public static AudioPlayerManager getInstance() { return INSTANCE; }

    public void setListener(PlaybackListener l) { this.listener = l; }

    public void setVolume(float vol) {
        volume = Math.max(0f, Math.min(1f, vol));
        applyVolumeToLine();
    }

    public float getVolume() { return volume; }

    public boolean isPlaying() { return isPlaying.get(); }
    public boolean isPaused() { return isPaused.get(); }
    public Track getNowPlaying() { return nowPlaying.get(); }

    public long getCurrentPositionSeconds() {
        Track track = nowPlaying.get();
        if (track == null || !isPlaying.get()) return 0;
        long duration = track.getDurationSeconds();
        if (duration <= 0) return 0;

        long elapsed;
        if (playbackStartMillis == 0L) {
            elapsed = 0L;
        } else if (isPaused.get()) {
            elapsed = pausedAccumulatedMillis + (pauseStartMillis - playbackStartMillis);
        } else {
            elapsed = pausedAccumulatedMillis + (System.currentTimeMillis() - playbackStartMillis);
        }
        long pos = startOffsetSeconds * 1000L + elapsed;
        return Math.max(0L, Math.min(pos / 1000L, duration));
    }

    public float getProgressRatio() {
        Track track = nowPlaying.get();
        if (track == null) return 0f;
        long duration = track.getDurationSeconds();
        if (duration <= 0) return 0f;
        return Math.min(1f, (float) getCurrentPositionSeconds() / (float) duration);
    }

    public void play(Track track) {
        play(track, 0L);
    }

    public void play(Track track, long offsetSeconds) {
        if (track == null) return;

        long id = ++playbackId;
        currentPlaybackId = id;

        // signal old thread to stop and close its line, but keep nowPlaying/playing state
        stopCurrentPlayback();

        nowPlaying.set(track);
        stopRequested.set(false);
        isPaused.set(false);
        isPlaying.set(true);
        startOffsetSeconds = Math.max(0, offsetSeconds);
        bytesWrittenTotal = 0L;
        pausedAccumulatedMillis = 0L;
        pauseStartMillis = 0L;
        playbackStartMillis = System.currentTimeMillis() - startOffsetSeconds * 1000L;

        CustomMusicMod.LOGGER.info("Playback requested: {} offset={}s id={}", track.getFile().getName(), startOffsetSeconds, id);
        executor.submit(() -> playInternal(track, id));
    }

    public void seekTo(long seconds) {
        Track track = nowPlaying.get();
        if (track == null || !isPlaying.get()) return;
        long duration = track.getDurationSeconds();
        if (duration <= 0) return;
        long target = Math.max(0L, Math.min(seconds, duration));
        CustomMusicMod.LOGGER.info("Seek requested: {}s / {}s for {}", target, duration, track.getFile().getName());
        play(track, target);
    }

    public void pause() {
        if (!isPlaying.get() || isPaused.get()) return;
        isPaused.set(true);
        pauseStartMillis = System.currentTimeMillis();
        SourceDataLine line = currentLine;
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
        }
        if (listener != null) listener.onPaused(nowPlaying.get());
    }

    public void resume() {
        if (!isPlaying.get() || !isPaused.get()) return;
        if (pauseStartMillis > 0L) {
            pausedAccumulatedMillis += (System.currentTimeMillis() - pauseStartMillis);
            pauseStartMillis = 0L;
        }
        isPaused.set(false);
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        SourceDataLine line = currentLine;
        if (line != null) {
            try { line.start(); } catch (Exception ignored) {}
        }
        if (listener != null) listener.onResumed(nowPlaying.get());
    }

    public void togglePause() {
        if (isPaused.get()) resume(); else pause();
    }

    public void stop() {
        stopRequested.set(true);
        isPaused.set(false);
        isPlaying.set(false);
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        SourceDataLine line = currentLine;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {}
            currentLine = null;
        }
        currentFormat = null;
        startOffsetSeconds = 0L;
        bytesWrittenTotal = 0L;
        playbackStartMillis = 0L;
        pausedAccumulatedMillis = 0L;
        pauseStartMillis = 0L;
    }

    private void stopCurrentPlayback() {
        stopRequested.set(true);
        isPaused.set(false);
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        SourceDataLine line = currentLine;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {}
            currentLine = null;
        }
    }

    private void playInternal(Track track, long playbackId) {
        File file = track.getFile();
        CustomMusicMod.LOGGER.info("Starting playback thread: {} (id={})", file.getAbsolutePath(), playbackId);

        if (track.getFormat().isDSD()) {
            handleDSD(track, playbackId);
            return;
        }

        AudioInputStream in = null;
        AudioInputStream decoded = null;
        SourceDataLine line = null;

        try {
            // First peek at base format to build candidates
            AudioInputStream probe = openAudioStream(file);
            AudioFormat baseFormat = probe.getFormat();
            probe.close();

            List<AudioFormat> candidates = buildTargetCandidates(baseFormat);
            CustomMusicMod.LOGGER.info("Base format: {} | trying {} target format(s)", baseFormat, candidates.size());

            for (AudioFormat candidate : candidates) {
                if (in != null) { try { in.close(); } catch (Exception ignored) {} }
                if (decoded != null) { try { decoded.close(); } catch (Exception ignored) {} }
                if (line != null && line.isOpen()) { try { line.close(); } catch (Exception ignored) {} }

                try {
                    in = openAudioStream(file);
                    decoded = toPcm(in, candidate);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, candidate);
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(candidate);
                } catch (Exception e) {
                    CustomMusicMod.LOGGER.debug("Format candidate failed: {} - {}", candidate, e.getMessage());
                    decoded = null;
                    line = null;
                    continue;
                }
                currentLine = line;
                currentFormat = candidate;
                CustomMusicMod.LOGGER.info("Selected target format: {}", candidate);
                break;
            }

            if (line == null) {
                throw new LineUnavailableException("Не найдено поддерживаемого PCM формата для этого файла");
            }

            // Apply seek offset BEFORE starting the line, so audio starts at requested position
            applySeekOffset(decoded, currentFormat);

            applyVolumeToLine();
            line.start();

            if (listener != null) listener.onTrackStarted(track);

            byte[] buffer = new byte[8192];
            int read;
            while (!stopRequested.get() && playbackId == currentPlaybackId && (read = decoded.read(buffer, 0, buffer.length)) != -1) {
                // Handle pause
                while (isPaused.get() && !stopRequested.get() && playbackId == currentPlaybackId) {
                    synchronized (pauseLock) {
                        try { pauseLock.wait(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    }
                }
                if (stopRequested.get() || playbackId != currentPlaybackId) break;
                line.write(buffer, 0, read);
                bytesWrittenTotal += read;
            }

            if (playbackId == currentPlaybackId) {
                try { line.drain(); } catch (Exception ignored) {}
            }
            try { line.stop(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
            if (currentLine == line) currentLine = null;

            if (!stopRequested.get() && playbackId == currentPlaybackId) {
                CustomMusicMod.LOGGER.info("Track finished: {}", track.getTitle());
                isPlaying.set(false);
                if (listener != null) listener.onTrackFinished(track);
                handleAutoNext(track, playbackId);
            }

        } catch (UnsupportedAudioFileException e) {
            if (playbackId != currentPlaybackId) return;
            if (stopRequested.get()) { isPlaying.set(false); return; }
            String err = "Неподдерживаемый аудио формат: " + file.getName() + " (" + e.getMessage() + ")";
            if (track.getFormat() != AudioFormatType.WAV) {
                err += "\nДля MP3/FLAC/OGG нужен fat jar (custommusic-*-all.jar), а не slim jar.";
            }
            CustomMusicMod.LOGGER.error(err, e);
            isPlaying.set(false);
            if (listener != null) listener.onError(track, err);
        } catch (LineUnavailableException e) {
            if (playbackId != currentPlaybackId) return;
            if (stopRequested.get()) { isPlaying.set(false); return; }
            String err = "Аудио линия недоступна: " + e.getMessage();
            CustomMusicMod.LOGGER.error(err, e);
            isPlaying.set(false);
            if (listener != null) listener.onError(track, err);
        } catch (IOException e) {
            if (playbackId != currentPlaybackId) return;
            if (stopRequested.get()) { isPlaying.set(false); return; }
            String err = "Ошибка чтения файла: " + e.getMessage();
            CustomMusicMod.LOGGER.error(err, e);
            isPlaying.set(false);
            if (listener != null) listener.onError(track, err);
        } catch (Exception e) {
            if (playbackId != currentPlaybackId) return;
            if (stopRequested.get()) { isPlaying.set(false); return; }
            String err = "Ошибка воспроизведения: " + e.getMessage();
            CustomMusicMod.LOGGER.error(err, e);
            isPlaying.set(false);
            if (listener != null) listener.onError(track, err);
        } finally {
            try { if (decoded != null) decoded.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (line != null && line.isOpen()) {
                try { line.close(); } catch (Exception ignored) {}
            }
            if (currentLine == line) currentLine = null;
        }
    }

    /**
     * Открывает аудио-файл.
     *
     * <p>Для .ogg используется собственный декодер ({@link OggVorbisDecoder}): библиотека
     * javazoom vorbisspi (2012 год) на Java 17/21 определяет формат, но отдаёт поток,
     * из которого читается 0 байт - трек молча "не играет".
     */
    private AudioInputStream openAudioStream(File file) throws Exception {
        if (OggVorbisDecoder.isOgg(file)) {
            try {
                AudioInputStream pcm = OggVorbisDecoder.open(file);
                CustomMusicMod.LOGGER.info("OGG Vorbis: встроенный декодер, формат {}", pcm.getFormat());
                return pcm;
            } catch (Throwable t) {
                CustomMusicMod.LOGGER.warn("Встроенный OGG-декодер не справился ({}), пробуем SPI", t.toString());
            }
        }
        return AudioSystem.getAudioInputStream(file);
    }

    /** Конвертирует поток в целевой PCM-формат; если форматы уже совпадают - конвертация не нужна. */
    private AudioInputStream toPcm(AudioInputStream in, AudioFormat target) throws Exception {
        if (in.getFormat().matches(target)) return in;
        return AudioSystem.getAudioInputStream(target, in);
    }
    private List<AudioFormat> buildTargetCandidates(AudioFormat baseFormat) {
        int channels = baseFormat.getChannels() > 0 ? baseFormat.getChannels() : 2;
        int sourceBits = baseFormat.getSampleSizeInBits() > 0 ? baseFormat.getSampleSizeInBits() : 16;
        float sourceRate = baseFormat.getSampleRate() > 0 ? baseFormat.getSampleRate() : 44100f;

        LinkedHashSet<AudioFormat> set = new LinkedHashSet<>();
        // prefer native rate/bit depth if line supports it
        addFormat(set, sourceRate, sourceBits, channels);
        // high-res FLAC often needs down-conversion; try same rate / 16 bit next
        addFormat(set, sourceRate, 16, channels);
        // common sound card friendly formats
        addFormat(set, 48000f, 16, channels);
        addFormat(set, 44100f, 16, channels);
        // some cards support 24/32 bit at standard rates
        addFormat(set, 48000f, 24, channels);
        addFormat(set, 44100f, 24, channels);
        // fallback: force stereo if weird channel count
        if (channels != 2) {
            addFormat(set, 44100f, 16, 2);
        }
        return new ArrayList<>(set);
    }

    private void addFormat(Set<AudioFormat> set, float rate, int bits, int channels) {
        if (rate <= 0 || bits <= 0 || channels <= 0) return;
        int frameSize = channels * (bits / 8);
        if (frameSize <= 0) return;
        set.add(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, bits, channels, frameSize, rate, false));
    }

    private void applySeekOffset(AudioInputStream decoded, AudioFormat targetFormat) {
        if (startOffsetSeconds <= 0) return;
        long bytesPerSec = getBytesPerSecond(targetFormat);
        if (bytesPerSec <= 0) {
            startOffsetSeconds = 0L;
            return;
        }

        long bytesToSkip = startOffsetSeconds * bytesPerSec;
        long skipped = 0L;

        // Try efficient skip first
        try {
            skipped = decoded.skip(bytesToSkip);
        } catch (IOException e) {
            CustomMusicMod.LOGGER.warn("Seek skip failed: {}", e.getMessage());
        }
        if (skipped < 0) skipped = 0;

        // Fallback: read and discard remaining bytes
        if (skipped < bytesToSkip) {
            byte[] discard = new byte[65536];
            while (skipped < bytesToSkip) {
                int need = (int) Math.min(discard.length, bytesToSkip - skipped);
                int read;
                try {
                    read = decoded.read(discard, 0, need);
                } catch (IOException e) {
                    CustomMusicMod.LOGGER.warn("Seek read-discard failed: {}", e.getMessage());
                    break;
                }
                if (read <= 0) break;
                skipped += read;
            }
        }

        long actualOffsetSec = skipped / bytesPerSec;
        playbackStartMillis = System.currentTimeMillis() - actualOffsetSec * 1000L;
        CustomMusicMod.LOGGER.info("Seek requested {}s ({} bytes), skipped {} bytes, actual ~{}s",
                startOffsetSeconds, bytesToSkip, skipped, actualOffsetSec);
        startOffsetSeconds = 0L;
    }

    private long getBytesPerSecond(AudioFormat format) {
        int frameSize = format.getFrameSize();
        float frameRate = format.getFrameRate();
        if (frameSize > 0 && frameRate > 0) {
            return (long) (frameSize * frameRate);
        }
        int bits = format.getSampleSizeInBits();
        if (bits <= 0) bits = 16;
        int channels = format.getChannels();
        if (channels <= 0) channels = 2;
        float rate = format.getSampleRate();
        if (rate <= 0) rate = 44100f;
        return (long) (channels * (bits / 8) * rate);
    }

    private void handleDSD(Track track, long playbackId) {
        CustomMusicMod.LOGGER.warn("Attempting to play DSD file: {}. This is experimental.", track.getFile().getName());
        try {
            // Some SPI may convert DSD to PCM
            AudioInputStream in = AudioSystem.getAudioInputStream(track.getFile());
            CustomMusicMod.LOGGER.info("DSF SPI found, format: {}", in.getFormat());
            in.close();
            playInternalBypassDSDCheck(track, playbackId);
        } catch (Exception e) {
            if (playbackId != currentPlaybackId) return;
            if (stopRequested.get()) { isPlaying.set(false); return; }
            String err = "DSF/DSD воспроизведение пока не поддерживается без конвертации. Конвертируйте файл '" +
                    track.getFile().getName() + "' в FLAC/WAV/MP3.";
            CustomMusicMod.LOGGER.error(err, e);
            isPlaying.set(false);
            if (listener != null) listener.onError(track, err);
        }
    }

    private void playInternalBypassDSDCheck(Track track, long playbackId) {
        File file = track.getFile();
        AudioInputStream in = null;
        AudioInputStream decoded = null;
        SourceDataLine line = null;
        try {
            in = openAudioStream(file);
            AudioFormat baseFormat = in.getFormat();
            List<AudioFormat> candidates = buildTargetCandidates(baseFormat);
            for (AudioFormat candidate : candidates) {
                if (in != null) try { in.close(); } catch (Exception ignored) {}
                try {
                    in = openAudioStream(file);
                    decoded = toPcm(in, candidate);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, candidate);
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(candidate);
                } catch (Exception e) {
                    if (line != null) try { line.close(); } catch (Exception ignored) {}
                    line = null;
                    continue;
                }
                currentLine = line;
                currentFormat = candidate;
                break;
            }
            if (line == null) throw new LineUnavailableException("No line for DSD bypass");

            applySeekOffset(decoded, currentFormat);
            applyVolumeToLine();
            line.start();
            if (listener != null) listener.onTrackStarted(track);
            byte[] buffer = new byte[8192];
            int read;
            while (!stopRequested.get() && playbackId == currentPlaybackId && (read = decoded.read(buffer, 0, buffer.length)) != -1) {
                while (isPaused.get() && !stopRequested.get() && playbackId == currentPlaybackId) {
                    synchronized (pauseLock) { pauseLock.wait(200); }
                }
                if (stopRequested.get() || playbackId != currentPlaybackId) break;
                line.write(buffer, 0, read);
                bytesWrittenTotal += read;
            }
            try { line.drain(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
            if (currentLine == line) currentLine = null;
            if (!stopRequested.get() && playbackId == currentPlaybackId) {
                isPlaying.set(false);
                if (listener != null) listener.onTrackFinished(track);
                handleAutoNext(track, playbackId);
            }
        } catch (Exception e) {
            if (playbackId != currentPlaybackId) return;
            if (stopRequested.get()) { isPlaying.set(false); return; }
            CustomMusicMod.LOGGER.error("Error in DSF bypass play", e);
            isPlaying.set(false);
            if (listener != null) listener.onError(track, e.getMessage());
        } finally {
            try { if (decoded != null) decoded.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (line != null && line.isOpen()) try { line.close(); } catch (Exception ignored) {}
            if (currentLine == line) currentLine = null;
        }
    }

    private void handleAutoNext(Track finishedTrack, long playbackId) {
        if (playbackId != currentPlaybackId) {
            CustomMusicMod.LOGGER.info("Auto-next skipped: playback {} was superseded by {}", playbackId, currentPlaybackId);
            return;
        }
        Track next = PlaylistManager.getInstance().next();
        if (next != null) {
            CustomMusicMod.LOGGER.info("Auto playing next: {}", next.getTitle());
            play(next);
        } else {
            CustomMusicMod.LOGGER.info("Playlist ended");
        }
    }

    private void applyVolumeToLine() {
        SourceDataLine line = currentLine;
        if (line == null) return;
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        try {
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float range = gain.getMaximum() - gain.getMinimum();
            float gainValue;
            if (volume <= 0f) {
                gainValue = gain.getMinimum();
            } else {
                float dB = (float) (20 * Math.log10(volume));
                gainValue = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
                if (gainValue < gain.getMinimum()) gainValue = gain.getMinimum() + range * volume;
            }
            gain.setValue(gainValue);
        } catch (Exception e) {
            CustomMusicMod.LOGGER.debug("Failed to set volume: {}", e.getMessage());
        }
    }

    public void shutdown() {
        stop();
        executor.shutdownNow();
    }
}
