package com.obninsk.custommusic.music;

public enum PlayMode {
    SEQUENTIAL("Последовательно", "▶▶"),
    SHUFFLE("Перемешать", "🔀"),
    REPEAT_ALL("Повтор всех", "🔁"),
    REPEAT_ONE("Повтор одного", "🔂");

    private final String displayName;
    private final String icon;
    PlayMode(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }

    public PlayMode next() {
        return switch (this) {
            case SEQUENTIAL -> SHUFFLE;
            case SHUFFLE -> REPEAT_ALL;
            case REPEAT_ALL -> REPEAT_ONE;
            case REPEAT_ONE -> SEQUENTIAL;
        };
    }

    public static PlayMode fromString(String s) {
        if (s == null) return SHUFFLE;
        try { return valueOf(s.toUpperCase()); } catch (Exception e) { return SHUFFLE; }
    }
}
