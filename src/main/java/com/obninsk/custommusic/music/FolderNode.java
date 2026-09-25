package com.obninsk.custommusic.music;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FolderNode {
    private final File folder;
    private final FolderNode parent;
    private final List<FolderNode> children = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();
    private final String name;

    public FolderNode(File folder, FolderNode parent) {
        this.folder = folder;
        this.parent = parent;
        this.name = folder.getName().isEmpty() ? folder.getAbsolutePath() : folder.getName();
    }

    public File getFolder() { return folder; }
    public FolderNode getParent() { return parent; }
    public List<FolderNode> getChildren() { return Collections.unmodifiableList(children); }
    public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }
    public String getName() { return name; }

    public void addChild(FolderNode child) { children.add(child); }
    public void addTrack(Track t) { tracks.add(t); }

    public int getTotalTrackCountRecursive() {
        int count = tracks.size();
        for (FolderNode c : children) count += c.getTotalTrackCountRecursive();
        return count;
    }

    public List<Track> getAllTracksRecursive() {
        List<Track> all = new ArrayList<>(tracks);
        for (FolderNode c : children) all.addAll(c.getAllTracksRecursive());
        return all;
    }

    public String getRelativePath(File root) {
        try {
            String rootPath = root.getCanonicalPath();
            String thisPath = folder.getCanonicalPath();
            if (thisPath.equals(rootPath)) return "/";
            if (thisPath.startsWith(rootPath)) return thisPath.substring(rootPath.length());
            return thisPath;
        } catch (Exception e) {
            return folder.getName();
        }
    }
}
