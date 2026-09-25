package com.obninsk.custommusic.music;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.FormatConversionProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;

/**
 * Детерминированный выбор Java Sound SPI для открытия файла и перевода его в PCM.
 *
 * <p>Зачем это нужно (и почему нельзя просто звать {@link AudioSystem}):
 * <ol>
 *   <li>{@code AudioSystem.getAudioInputStream(target, source)} берёт ПЕРВОГО провайдера,
 *       который ответил {@code isConversionSupported(...) == true}, и <b>не откатывается</b>,
 *       если тот бросил исключение. Для FLAC у мода две SPI (jse-spi-flac и jflac-codec),
 *       а порядок их перечисления зависит от порядка jar'ов в {@code META-INF/jarjar} —
 *       в игре он не гарантирован. Получалась пара «reader из одной библиотеки,
 *       converter из другой»: {@code IllegalArgumentException: conversion not supported}.</li>
 *   <li>Для .ogg библиотека javazoom vorbisspi (2012) на Java 17/21 формат определяет,
 *       но отдаёт поток, из которого читается 0 байт — трек молча не играет.
 *       Поэтому OGG декодируется своим {@link OggVorbisDecoder} поверх ванильного
 *       jorbis, который поставляет сам Minecraft.</li>
 * </ol>
 *
 * <p>Класс намеренно не зависит от Minecraft/NeoForge — его можно тестировать отдельно.
 */
public final class AudioSpiSelector {

    /** Порядок важен: сначала SPI, умеющая hi-res 96/24 и даунсэмплинг. */
    public static final String[] FLAC_READERS = {
            "io.github.jseproject.FlacAudioFileReader",
            "org.jflac.sound.spi.FlacAudioFileReader"
    };

    /** Конвертер берётся с тем же индексом, что и reader (пара из одной библиотеки). */
    public static final String[] FLAC_CONVERTERS = {
            "io.github.jseproject.FlacFormatConversionProvider",
            "org.jflac.sound.spi.FlacFormatConversionProvider"
    };

    private AudioSpiSelector() {
    }

    /** Результат открытия: поток + какой FLAC-провайдер его открыл (-1 если не FLAC). */
    public static final class Opened {
        public final AudioInputStream stream;
        public final int flacProvider;
        public final String openedBy;

        Opened(AudioInputStream stream, int flacProvider, String openedBy) {
            this.stream = stream;
            this.flacProvider = flacProvider;
            this.openedBy = openedBy;
        }
    }

    // ---------------------------------------------------------------- открытие файла

    public static Opened open(File file) throws Exception {
        if (OggVorbisDecoder.isOgg(file)) {
            try {
                AudioInputStream pcm = OggVorbisDecoder.open(file);
                return new Opened(pcm, -1, "OggVorbisDecoder");
            } catch (Throwable t) {
                // не Ogg Vorbis (например Ogg Opus) или битый файл - пробуем штатный SPI
            }
        }

        if (isFlac(file)) {
            for (int i = 0; i < FLAC_READERS.length; i++) {
                try {
                    AudioFileReader reader = (AudioFileReader) instantiate(FLAC_READERS[i]);
                    AudioInputStream in = reader.getAudioInputStream(file);
                    return new Opened(in, i, FLAC_READERS[i]);
                } catch (Throwable ignored) {
                    // пробуем следующую SPI
                }
            }
        }

        AudioInputStream in = AudioSystem.getAudioInputStream(file);
        return new Opened(in, -1, "AudioSystem");
    }

    // ---------------------------------------------------------------- конвертация в PCM

    /**
     * Переводит поток в целевой PCM-формат.
     * Если форматы уже совпадают - конвертация не делается вовсе.
     */
    public static AudioInputStream toPcm(AudioInputStream in, AudioFormat target, int flacProvider) throws Exception {
        AudioFormat source = in.getFormat();
        if (source.matches(target)) return in;

        // 1) конвертер из той же библиотеки, что открыла файл
        if (flacProvider >= 0 && flacProvider < FLAC_CONVERTERS.length) {
            try {
                FormatConversionProvider conv = (FormatConversionProvider) instantiate(FLAC_CONVERTERS[flacProvider]);
                if (conv.isConversionSupported(target, source)) {
                    return conv.getAudioInputStream(target, in);
                }
            } catch (Throwable ignored) {
                // ниже переберём остальных
            }
        }

        // 2) все SPI-провайдеры по очереди (AudioSystem при ошибке не откатывается, мы - откатываемся)
        try {
            for (FormatConversionProvider provider : ServiceLoader.load(FormatConversionProvider.class)) {
                try {
                    if (provider.isConversionSupported(target, source)) {
                        return provider.getAudioInputStream(target, in);
                    }
                } catch (Throwable ignored) {
                    // пробуем следующего
                }
            }
        } catch (Throwable ignored) {
        }

        // 3) штатный путь - там есть и встроенные конвертеры JDK (PCM->PCM, ALAW/ULAW и т.д.)
        return AudioSystem.getAudioInputStream(target, in);
    }

    // ---------------------------------------------------------------- утилиты

    private static Object instantiate(String className) throws Exception {
        return Class.forName(className).getDeclaredConstructor().newInstance();
    }

    public static boolean isFlac(File file) {
        return hasMagic(file, "fLaC");
    }

    public static boolean isOgg(File file) {
        return OggVorbisDecoder.isOgg(file);
    }

    /** Формат, в который умеют конвертировать обе FLAC-SPI: PCM_SIGNED 16 bit little-endian. */
    public static AudioFormat pcm(float sampleRate, int channels) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels,
                channels * 2, sampleRate, false);
    }

    private static boolean hasMagic(File file, String magic) {
        if (file == null || !file.isFile()) return false;
        byte[] want = magic.getBytes(StandardCharsets.US_ASCII);
        try (InputStream in = new FileInputStream(file)) {
            byte[] got = new byte[want.length];
            int read = 0;
            while (read < got.length) {
                int n = in.read(got, read, got.length - read);
                if (n < 0) return false;
                read += n;
            }
            for (int i = 0; i < want.length; i++) {
                if (got[i] != want[i]) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Читабельное имя формата для логов. */
    public static String describe(AudioFileFormat format) {
        return format == null ? "null" : format.getType() + " " + format.getFormat();
    }
}
