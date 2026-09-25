package com.obninsk.custommusic.util;

import com.obninsk.custommusic.CustomMusicMod;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Минимальный парсер DSF заголовка для получения базовой информации.
 * DSF формат: DSD Stream File
 * Структура:
 *  - 0-3: "DSD " magic
 *  - 4-7: chunk size (8 bytes? actually 64-bit)
 *  - и т.д.
 * 
 * Полноценный декодер DSD -> PCM требует сложной обработки (decimation filter).
 * Здесь только чтение метаданных, для полноценного воспроизведения нужен нативный конвертер.
 * 
 * В будущем можно интегрировать:
 *  - https://github.com/dschoon/dsd-lib
 *  - или использовать ffmpeg через ProcessBuilder как внешний конвертер
 */
public class DSFParser {

    public static class DSFInfo {
        public long fileSize;
        public long metadataOffset;
        public long sampleRate; // e.g. 2822400, 5644800 etc (DSD64 = 2822400, DSD128 = 5644800)
        public int channels;
        public int bitsPerSample = 1; // DSD always 1-bit
        public long sampleCount;
        public double durationSeconds;

        @Override
        public String toString() {
            return String.format("DSF: %d Hz, %d ch, %.1f sec, fileSize=%d", sampleRate, channels, durationSeconds, fileSize);
        }
    }

    public static DSFInfo parse(File f) {
        DSFInfo info = new DSFInfo();
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] header = new byte[28];
            raf.readFully(header);
            String magic = new String(header, 0, 4);
            if (!"DSD ".equals(magic)) {
                CustomMusicMod.LOGGER.warn("Not a valid DSF file: {}", f.getName());
                return null;
            }
            ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            bb.position(4);
            long chunkSize = bb.getLong(); // 28
            info.fileSize = bb.getLong(); // total file size
            info.metadataOffset = bb.getLong();

            // fmt chunk
            byte[] fmtChunkHeader = new byte[52];
            raf.readFully(fmtChunkHeader);
            ByteBuffer fmt = ByteBuffer.wrap(fmtChunkHeader).order(ByteOrder.LITTLE_ENDIAN);
            byte[] fmtMagic = new byte[4];
            fmt.get(fmtMagic);
            String fmtStr = new String(fmtMagic);
            long fmtChunkSize = fmt.getLong();
            int formatVersion = fmt.getInt();
            int formatId = fmt.getInt(); // 0 = DSD
            int channelType = fmt.getInt();
            info.channels = fmt.getInt();
            info.sampleRate = Integer.toUnsignedLong(fmt.getInt());
            int bitsPerSample = fmt.getInt();
            long sampleCount = fmt.getLong();
            info.sampleCount = sampleCount;
            int blockSizePerChannel = fmt.getInt();

            // data chunk header 12 bytes
            byte[] dataHeader = new byte[12];
            raf.readFully(dataHeader);
            ByteBuffer dataBb = ByteBuffer.wrap(dataHeader).order(ByteOrder.LITTLE_ENDIAN);
            byte[] dataMagic = new byte[4];
            dataBb.get(dataMagic);
            long dataChunkSize = dataBb.getLong();

            if (info.sampleRate > 0 && info.sampleCount > 0) {
                info.durationSeconds = (double) info.sampleCount / (info.sampleRate / 8.0); // DSD sample -> PCM logic?
                // More accurate: sampleCount = number of DSD samples per channel
                // DSD64: 2822400 samples per second
                // So duration = sampleCount / sampleRate
                info.durationSeconds = (double) info.sampleCount / (double) info.sampleRate;
            }

            return info;

        } catch (Exception e) {
            CustomMusicMod.LOGGER.warn("Failed to parse DSF {}: {}", f.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Идея для будущей реализации DSD -> PCM конвертации:
     * DSD это 1-bit PDM поток. Для воспроизведения через PCM нужно:
     *  - прочитать DSD блоки
     *  - применить low-pass фильтр (decimation) чтобы получить PCM
     *  - типичный алгоритм: 8x 1-bit -> 1x 24-bit через FIR filter, либо использовать библиотеку.
     * 
     * Пока оставляем stub и рекомендуем конвертировать через внешние tools:
     * ffmpeg -i input.dsf -ar 44100 -ac 2 output.flac
     */
}
