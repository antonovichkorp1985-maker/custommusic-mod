package com.obninsk.custommusic.music;

import java.util.*;

public class Artist {
    private final String name;
    private final Map<String, Album> albums = new LinkedHashMap<>();

    public Artist(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public Album getOrCreateAlbum(String albumName) {
        if (albumName == null || albumName.isBlank()) albumName = "Unknown Album";
        return albums.computeIfAbsent(albumName, k -> new Album(name, k));
    }

    public Collection<Album> getAlbums() { return albums.values(); }
    public Map<String, Album> getAlbumMap() { return Collections.unmodifiableMap(albums); }

    public List<Track> getAllTracks() {
        List<Track> all = new ArrayList<>();
        for (Album a : albums.values()) all.addAll(a.getTracks());
        return all;
    }

    public int getTotalTracks() {
        int c = 0;
        for (Album a : albums.values()) c += a.getTrackCount();
        return c;
    }

    @Override
    public String toString() { return name + " [" + getTotalTracks() + "]"; }
}
