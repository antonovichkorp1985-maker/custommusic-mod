package com.obninsk.custommusic.music;

import com.obninsk.custommusic.CustomMusicMod;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;

/**
 * Пытается прочитать ID3/FLAC tags через jaudiotagger.
 * Если не получается - оставляет fallback из имени файла.
 */
public class MetadataParser {

    public static void enrich(Track track) {
        File file = track.getFile();
        AudioFormatType fmt = track.getFormat();
        // DSD мы пока не парсим через jaudiotagger - пропускаем
        if (fmt.isDSD()) {
            // Попытка базовой поддержки DSF через имя
            return;
        }

        try {
            // jaudiotagger не всегда поддерживает все форматы, обернем
            AudioFile audioFile = AudioFileIO.read(file);
            Tag tag = audioFile.getTag();
            long duration = audioFile.getAudioHeader().getTrackLength();

            if (tag != null) {
                String title = tag.getFirst(FieldKey.TITLE);
                String artist = tag.getFirst(FieldKey.ARTIST);
                String album = tag.getFirst(FieldKey.ALBUM);
                String year = tag.getFirst(FieldKey.YEAR);
                String trackNo = tag.getFirst(FieldKey.TRACK);
                String ratingStr = tag.getFirst(FieldKey.RATING); // may not exist in all formats

                int trackNumber = 0;
                try {
                    trackNumber = Integer.parseInt(trackNo.replaceAll("\\D.*", ""));
                } catch (Exception ignored) {}

                int rating = 0;
                try {
                    if (!ratingStr.isEmpty()) {
                        int raw = Integer.parseInt(ratingStr.replaceAll("\\D.*", ""));
                        // Common tag ratings: 0-255 or 0-5 or 0-100
                        if (raw > 5 && raw <= 255) rating = Math.round(raw / 51f);
                        else if (raw > 100) rating = Math.round(raw / 20f);
                        else rating = raw;
                    }
                } catch (Exception ignored) {}

                track.applyMetadata(
                        title.isEmpty() ? null : title,
                        artist.isEmpty() ? null : artist,
                        album.isEmpty() ? null : album,
                        year.isEmpty() ? null : year,
                        duration,
                        trackNumber,
                        rating
                );
            } else {
                if (duration > 0) track.setDurationSeconds(duration);
            }
            long bitrateLong = audioFile.getAudioHeader().getBitRateAsNumber();
            if (bitrateLong > 0 && bitrateLong <= Integer.MAX_VALUE) track.setBitrate((int) bitrateLong);
        } catch (Throwable e) {
            // Тихо логируем - фолбэк уже есть
            CustomMusicMod.LOGGER.debug("Failed to parse tags for {}: {}", file.getName(), e.getMessage());
        }
    }
}
