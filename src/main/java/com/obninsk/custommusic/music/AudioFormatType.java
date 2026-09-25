package com.obninsk.custommusic.music;

public enum AudioFormatType {
    MP3("mp3"),
    WAV("wav"),
    FLAC("flac"),
    OGG("ogg"),
    DSF("dsf"),
    DSDIFF("dff"),
    UNKNOWN("unknown");

    private final String ext;
    AudioFormatType(String ext) { this.ext = ext; }

    public static AudioFormatType fromFileName(String name) {
        if (name == null) return UNKNOWN;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".mp3")) return MP3;
        if (lower.endsWith(".wav")) return WAV;
        if (lower.endsWith(".flac")) return FLAC;
        if (lower.endsWith(".ogg")) return OGG;
        if (lower.endsWith(".dsf")) return DSF;
        if (lower.endsWith(".dff")) return DSDIFF;
        return UNKNOWN;
    }

    public String getExt() { return ext; }
    public boolean isLossless() { return this == WAV || this == FLAC || this == DSF || this == DSDIFF; }
    public boolean isDSD() { return this == DSF || this == DSDIFF; }
}
