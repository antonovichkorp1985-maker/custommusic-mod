package com.obninsk.custommusic.music;

import java.io.File;
import java.util.Objects;

/**
 * Модель одного трека
 */
public class Track {
    private final File file;
    private String title;
    private String artist;
    private String album;
    private String year;
    private AudioFormatType format;
    private long durationSeconds = -1;
    private int bitrate = -1;
    private int trackNumber = 0;
    private int rating = 0; // 0-5 stars
    private String folderPath = "";

    public Track(File file) {
        this.file = file;
        this.format = AudioFormatType.fromFileName(file.getName());
        // defaults from filename parsing
        parseFallbackMetadata();
    }

    private void parseFallbackMetadata() {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        // Try pattern "Artist - Album - Title" or "Artist - Title" or just Title
        String[] parts = name.split(" - ");
        if (parts.length == 3) {
            this.artist = parts[0].trim();
            this.album = parts[1].trim();
            this.title = parts[2].trim();
        } else if (parts.length == 2) {
            this.artist = parts[0].trim();
            this.title = parts[1].trim();
            this.album = "Unknown Album";
        } else {
            this.title = name.trim();
            this.artist = "Unknown Artist";
            this.album = "Unknown Album";
        }

        if (artist == null || artist.isEmpty()) artist = "Unknown Artist";
        if (album == null || album.isEmpty()) album = "Unknown Album";
        if (title == null || title.isEmpty()) title = file.getName();
    }

    public void applyMetadata(String title, String artist, String album, String year, long durationSec) {
        if (title != null && !title.isBlank()) this.title = title;
        if (artist != null && !artist.isBlank()) this.artist = artist;
        if (album != null && !album.isBlank()) this.album = album;
        if (year != null) this.year = year;
        if (durationSec > 0) this.durationSeconds = durationSec;
    }

    public void applyMetadata(String title, String artist, String album, String year, long durationSec, int trackNumber, int rating) {
        applyMetadata(title, artist, album, year, durationSec);
        if (trackNumber > 0) this.trackNumber = trackNumber;
        if (rating >= 0 && rating <= 5) this.rating = rating;
    }

    public File getFile() { return file; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getYear() { return year; }
    public AudioFormatType getFormat() { return format; }
    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
    public int getTrackNumber() { return trackNumber; }
    public void setTrackNumber(int trackNumber) { this.trackNumber = trackNumber; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = Math.max(0, Math.min(5, rating)); }
    public int getBitrate() { return bitrate; }
    public void setBitrate(int bitrate) { this.bitrate = bitrate; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath != null ? folderPath : ""; }

    public String getDisplayName() {
        return String.format("%s - %s", artist, title);
    }

    public String getDurationFormatted() {
        if (durationSeconds <= 0) return "--:--";
        long m = durationSeconds / 60;
        long s = durationSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Track track = (Track) o;
        return Objects.equals(file.getAbsolutePath(), track.file.getAbsolutePath());
    }

    @Override
    public int hashCode() {
        return Objects.hash(file.getAbsolutePath());
    }

    @Override
    public String toString() {
        return "Track{" + artist + " - " + album + " - " + title + " [" + format + "]}";
    }
}
