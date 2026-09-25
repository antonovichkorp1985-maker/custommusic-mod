package com.obninsk.custommusic.music;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Управляет очередью воспроизведения и режимами.
 *
 * Важно: при запуске трека очередь всегда принимает порядок треков,
 * который показан на экране. Режимы влияют только на поведение
 * кнопок "вперед/назад" и автоперехода (повтор, перемешивание при зацикливании).
 */
public class PlaylistManager {
    private static final PlaylistManager INSTANCE = new PlaylistManager();

    private List<Track> currentQueue = new ArrayList<>();
    private List<Track> originalQueue = new ArrayList<>();
    private int currentIndex = -1;
    private PlayMode playMode = PlayMode.SEQUENTIAL;
    private final RandomSource random = RandomSource.create();

    private PlaylistManager() {}

    public static PlaylistManager getInstance() { return INSTANCE; }

    public synchronized void setQueue(List<Track> tracks, int startIndex) {
        originalQueue = new ArrayList<>(tracks);
        applyModeToQueue();
        Track startTrack = (startIndex >= 0 && startIndex < originalQueue.size()) ? originalQueue.get(startIndex) : null;
        if (startTrack != null) {
            int idx = currentQueue.indexOf(startTrack);
            currentIndex = idx >= 0 ? idx : 0;
        } else {
            currentIndex = currentQueue.isEmpty() ? -1 : 0;
        }
    }

    public synchronized void setQueue(List<Track> tracks) {
        setQueue(tracks, tracks.isEmpty() ? -1 : 0);
    }

    private void applyModeToQueue() {
        currentQueue = new ArrayList<>(originalQueue);
        if (playMode == PlayMode.SHUFFLE) {
            Collections.shuffle(currentQueue, new java.util.Random());
        }
    }

    public synchronized void setPlayMode(PlayMode mode) {
        Track current = getCurrentTrack();
        this.playMode = mode;
        if (mode == PlayMode.SHUFFLE) {
            currentQueue = new ArrayList<>(originalQueue);
            Collections.shuffle(currentQueue, new java.util.Random());
        } else {
            currentQueue = new ArrayList<>(originalQueue);
        }
        if (current != null) {
            int idx = currentQueue.indexOf(current);
            currentIndex = idx >= 0 ? idx : 0;
        }
    }

    public synchronized PlayMode getPlayMode() { return playMode; }

    public synchronized Track getCurrentTrack() {
        if (currentIndex < 0 || currentIndex >= currentQueue.size()) return null;
        return currentQueue.get(currentIndex);
    }

    public synchronized Track next() {
        if (currentQueue.isEmpty()) return null;
        if (playMode == PlayMode.REPEAT_ONE) {
            return getCurrentTrack();
        }
        currentIndex++;
        if (currentIndex >= currentQueue.size()) {
            if (playMode == PlayMode.REPEAT_ALL) {
                currentIndex = 0;
            } else if (playMode == PlayMode.SHUFFLE) {
                Collections.shuffle(currentQueue, new java.util.Random());
                currentIndex = 0;
            } else {
                currentIndex = currentQueue.size() - 1;
                return null; // end
            }
        }
        return getCurrentTrack();
    }

    public synchronized Track previous() {
        if (currentQueue.isEmpty()) return null;
        if (playMode == PlayMode.REPEAT_ONE) return getCurrentTrack();
        currentIndex--;
        if (currentIndex < 0) {
            if (playMode == PlayMode.REPEAT_ALL || playMode == PlayMode.SHUFFLE) {
                currentIndex = currentQueue.size() - 1;
            } else {
                currentIndex = 0;
                return getCurrentTrack();
            }
        }
        return getCurrentTrack();
    }

    public synchronized List<Track> getCurrentQueue() {
        return Collections.unmodifiableList(currentQueue);
    }

    public synchronized int getCurrentIndex() { return currentIndex; }

    public synchronized void setCurrentIndex(int idx) {
        if (idx >= 0 && idx < currentQueue.size()) currentIndex = idx;
    }

    public synchronized boolean hasNext() {
        if (playMode == PlayMode.REPEAT_ALL || playMode == PlayMode.REPEAT_ONE || playMode == PlayMode.SHUFFLE) return !currentQueue.isEmpty();
        return currentIndex + 1 < currentQueue.size();
    }

    public synchronized void clear() {
        currentQueue.clear();
        originalQueue.clear();
        currentIndex = -1;
    }
}
