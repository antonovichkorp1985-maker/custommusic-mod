package com.obninsk.custommusic.music;

import com.obninsk.custommusic.CustomMusicMod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Самодиагностика аудио-подсистемы. Показывает, какие Java Sound SPI реально видны
 * в рантайме (в т.ч. из вложенных JarJar-библиотек) и какие файлы декодируются в PCM.
 *
 * <p>Запуск из JVM-аргументов (удобно на сервере/в логе запуска):
 * <pre>-Dcustommusic.selftest=/путь/к/папке/с/музыкой</pre>
 * либо командой в игре: {@code /custommusic selftest}
 *
 * <p>Ничего не бросает наружу: любая ошибка только логируется.
 */
public final class AudioSelfTest {

    private AudioSelfTest() {
    }

    /** Вызывается на старте, если задан -Dcustommusic.selftest=<папка>. */
    public static void runIfRequested() {
        String dir = System.getProperty("custommusic.selftest");
        if (dir == null || dir.isBlank()) return;
        try {
            run(new File(dir), 4);
        } catch (Throwable t) {
            CustomMusicMod.LOGGER.error("CustomMusic selftest failed", t);
        }
    }

    /**
     * @param dir      папка с аудиофайлами
     * @param maxFiles сколько файлов проверять
     * @return человекочитаемый отчёт (он же уходит в лог)
     */
    public static String run(File dir, int maxFiles) {
        StringBuilder sb = new StringBuilder();
        line(sb, "=== CustomMusic selftest ===");
        line(sb, "Java: " + System.getProperty("java.version") + " | TCCL: "
                + Thread.currentThread().getContextClassLoader());

        // 1) какие SPI видны через ServiceLoader (именно так их ищет javax.sound)
        line(sb, "-- AudioFileReader (ServiceLoader) --");
        for (javax.sound.sampled.spi.AudioFileReader r :
                ServiceLoader.load(javax.sound.sampled.spi.AudioFileReader.class)) {
            line(sb, "   " + r.getClass().getName() + "  [" + where(r.getClass()) + "]");
        }
        line(sb, "-- FormatConversionProvider (ServiceLoader) --");
        for (javax.sound.sampled.spi.FormatConversionProvider p :
                ServiceLoader.load(javax.sound.sampled.spi.FormatConversionProvider.class)) {
            line(sb, "   " + p.getClass().getName() + "  [" + where(p.getClass()) + "]");
        }

        // 2) что реально отдаёт AudioSystem (это то, чем пользуется плеер)
        line(sb, "-- AudioSystem.getAudioFileTypes() --");
        for (javax.sound.sampled.AudioFileFormat.Type t : javax.sound.sampled.AudioSystem.getAudioFileTypes()) {
            line(sb, "   " + t);
        }

        // 3) jorbis из Minecraft на месте?
        line(sb, "-- jorbis (даёт Minecraft) --");
        line(sb, "   com.jcraft.jorbis.DspState: " + classAvailable("com.jcraft.jorbis.DspState"));

        // 4) декодирование реальных файлов
        List<File> files = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] all = dir.listFiles();
            if (all != null) {
                for (File f : all) {
                    String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                    if (f.isFile() && (n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".ogg")
                            || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".dsf"))) {
                        files.add(f);
                    }
                }
            }
        } else if (dir.isFile()) {
            files.add(dir);
        }
        line(sb, "-- файлы (" + files.size() + " найдено, проверяем до " + maxFiles + ") --");
        if (files.isEmpty()) {
            line(sb, "   в " + dir + " нет аудиофайлов");
        }
        int checked = 0;
        for (File f : files) {
            if (checked++ >= maxFiles) break;
            checkFile(sb, f);
        }

        String report = sb.toString();
        CustomMusicMod.LOGGER.info("\n{}", report);
        return report;
    }

    private static void checkFile(StringBuilder sb, File file) {
        try {
            AudioSpiSelector.Opened opened = AudioSpiSelector.open(file);
            AudioFormat base = opened.stream.getFormat();
            opened.stream.close();

            float rate = base.getSampleRate() > 0 ? base.getSampleRate() : 44100f;
            int channels = base.getChannels() > 0 ? base.getChannels() : 2;
            AudioFormat target = AudioSpiSelector.pcm(rate, channels);

            AudioSpiSelector.Opened second = AudioSpiSelector.open(file);
            AudioInputStream decoded = AudioSpiSelector.toPcm(second.stream, target, second.flacProvider);
            byte[] buffer = new byte[8192];
            long total = 0;
            long sumSq = 0;
            int read;
            // читаем не больше ~20 секунд, чтобы не ждать весь трек
            long limit = (long) (rate * channels * 2 * 20);
            while ((read = decoded.read(buffer)) > 0) {
                total += read;
                for (int i = 0; i + 1 < read; i += 2) {
                    short s = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                    sumSq += (long) s * s;
                }
                if (total >= limit) break;
            }
            decoded.close();

            long samples = total / (2L * channels);
            double seconds = samples / (double) rate;
            double rms = samples > 0 ? Math.sqrt(sumSq / (double) samples) : 0;
            String verdict = (total > 10000 && rms > 1) ? "OK" : "!! ПУСТО/ТИШИНА";
            line(sb, String.format("   %-34s openedBy=%-46s %.2fs read, RMS=%.0f -> %s",
                    file.getName(), opened.openedBy, seconds, rms, verdict));
        } catch (Throwable t) {
            line(sb, "   " + file.getName() + " -> FAIL " + t);
        }
    }

    private static String where(Class<?> c) {
        try {
            var src = c.getProtectionDomain().getCodeSource();
            return src == null ? "?" : String.valueOf(src.getLocation());
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String classAvailable(String name) {
        try {
            Class.forName(name, false, AudioSelfTest.class.getClassLoader());
            return "есть";
        } catch (Throwable t) {
            return "НЕТ (" + t.getClass().getSimpleName() + ")";
        }
    }

    private static void line(StringBuilder sb, String text) {
        sb.append(text).append('\n');
    }
}
