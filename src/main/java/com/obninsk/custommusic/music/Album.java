package com.obninsk.custommusic.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Группа треков в рамках одного альбома
 */
public class Album {
    private final String name;
    private final String artist;
    private final List<Track> tracks = new ArrayList<>();

    public Album(String artist, String name) {
        this.artist = artist;
        this.name = name;
    }

    public void addTrack(Track t) { tracks.add(t); }
    public void clear() { tracks.clear(); }
    public String getName() { return name; }
    public String getArtist() { return artist; }
    public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }
    public int getTrackCount() { return tracks.size(); }

    @Override
    public String toString() { return name + " (" + tracks.size() + ")"; }
}
