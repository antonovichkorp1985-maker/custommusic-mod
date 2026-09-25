package com.obninsk.custommusic.music;

import com.obninsk.custommusic.CustomMusicMod;
import com.obninsk.custommusic.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Сканирует папку, строит индексы по исполнителям/альбомам.
 *
 * Теперь сканирование выполняется без удержания общей блокировки:
 * локально собираются треки/артисты/дерево папок, и только готовые
 * снимки атомарно заменяются. Экран открывается сразу, не дожидаясь
 * окончания сканирования большой библиотеки.
 */
public class MusicLibraryManager {
    private static final MusicLibraryManager INSTANCE = new MusicLibraryManager();

    private final List<LibraryListener> listeners = new ArrayList<>();
    private File musicFolder;

    private volatile boolean initialized = false;
    private volatile boolean scanning = false;
    private volatile List<Track> allTracksSnapshot = List.of();
    private volatile Map<String, Artist> artistsSnapshot = Map.of();
    private volatile FolderNode folderTreeSnapshot = null;

    public interface LibraryListener {
        void onLibraryUpdated();
    }

    private MusicLibraryManager() {}

    public static MusicLibraryManager getInstance() { return INSTANCE; }

    public void addListener(LibraryListener listener) {
        synchronized (listeners) { listeners.add(listener); }
    }

    public void removeListener(LibraryListener listener) {
        synchronized (listeners) { listeners.remove(listener); }
    }

    private void notifyListeners() {
        synchronized (listeners) {
            for (LibraryListener l : new ArrayList<>(listeners)) {
                try { l.onLibraryUpdated(); } catch (Exception ignored) {}
            }
        }
    }

    /** Инициализация идемпотентна: её можно звать и из client setup, и лениво из GUI. */
    public synchronized void init() {
        if (initialized) return;
        String cfgFolder = ModConfig.musicFolder();
        Path gameDir = FMLPaths.GAMEDIR.get();
        if (cfgFolder == null || cfgFolder.isBlank()) cfgFolder = "custommusic";
        musicFolder = gameDir.resolve(cfgFolder).toFile();
        if (!musicFolder.exists()) {
            boolean ok = musicFolder.mkdirs();
            CustomMusicMod.LOGGER.info("Created music folder {}: {}", musicFolder.getAbsolutePath(), ok);
            try {
                File readme = new File(musicFolder, "README.txt");
                if (!readme.exists()) {
                    String txt = """
                            Поместите сюда свою музыку:
                            - Поддерживаются mp3, wav, flac, ogg, dsf (экспериментально)
                            - Можно создавать подпапки: Artist/Album/track.mp3
                            - Имена файлов могут быть в формате "Artist - Album - Title.mp3"
                            После добавления нажмите "Обновить" в интерфейсе плеера или перезапустите игру.
                            """;
                    java.nio.file.Files.writeString(readme.toPath(), txt);
                }
            } catch (Exception e) {
                CustomMusicMod.LOGGER.warn("Failed to write README: {}", e.getMessage());
            }
        }
        CustomMusicMod.LOGGER.info("Music folder: {}", musicFolder.getAbsolutePath());
        initialized = true;

        if (ModConfig.autoScanOnStartup()) {
            scanAsync();
        }
    }

    public File getMusicFolder() { ensureInit(); return musicFolder; }

    public boolean isScanning() { return scanning; }

    public CompletableFuture<Void> scanAsync() {
        ensureInit();
        return CompletableFuture.runAsync(this::scan);
    }

    /** Если init() ещё не вызывали (например, мод поднят на сервере или GUI открыт раньше) - инициализируемся. */
    private void ensureInit() {
        if (!initialized) init();
    }

    public void scan() {
        if (scanning) {
            CustomMusicMod.LOGGER.warn("Library scan already in progress, skipping");
            return;
        }
        scanning = true;
        CustomMusicMod.LOGGER.info("Scanning music library in {}", musicFolder);

        try {
            if (musicFolder == null || !musicFolder.exists()) {
                CustomMusicMod.LOGGER.warn("Music folder does not exist");
                replaceSnapshots(List.of(), Map.of(), null);
                return;
            }

            List<File> files = listMusicFiles(musicFolder);
            CustomMusicMod.LOGGER.info("Found {} potential music files", files.size());

            boolean enableDSF = ModConfig.enableDSF();

            // локальные коллекции, заполняем без блокировок
            List<Track> tracks = new ArrayList<>(files.size());
            Map<String, Artist> artists = new LinkedHashMap<>();

            for (File f : files) {
                try {
                    AudioFormatType type = AudioFormatType.fromFileName(f.getName());
                    if (type == AudioFormatType.UNKNOWN) continue;
                    if (type.isDSD() && !enableDSF) {
                        CustomMusicMod.LOGGER.debug("Skipping DSF file (disabled in config): {}", f.getName());
                        continue;
                    }
                    Track track = new Track(f);
                    MetadataParser.enrich(track);
                    track.setFolderPath(getFolderRelativePath(f));
                    tracks.add(track);

                    String artistName = track.getArtist();
                    String albumName = track.getAlbum();
                    Artist artist = artists.computeIfAbsent(artistName, Artist::new);
                    Album album = artist.getOrCreateAlbum(albumName);
                    album.addTrack(track);
                } catch (Exception e) {
                    CustomMusicMod.LOGGER.warn("Failed to process file {}: {}", f.getName(), e.getMessage());
                }
            }

            tracks.sort(Comparator.comparing(Track::getArtist)
                    .thenComparing(Track::getAlbum)
                    .thenComparing(Track::getTitle));

            FolderNode folderTree = buildFolderTree(musicFolder, tracks);

            replaceSnapshots(tracks, artists, folderTree);
            CustomMusicMod.LOGGER.info("Library scan complete: {} tracks, {} artists", tracks.size(), artists.size());
        } catch (Exception e) {
            CustomMusicMod.LOGGER.error("Library scan failed", e);
        } finally {
            scanning = false;
        }
    }

    private void replaceSnapshots(List<Track> tracks, Map<String, Artist> artists, FolderNode folderTree) {
        // сортировка и финальные неизменяемые обёртки
        List<Track> tracksCopy = Collections.unmodifiableList(new ArrayList<>(tracks));
        Map<String, Artist> artistsCopy = Collections.unmodifiableMap(new LinkedHashMap<>(artists));

        allTracksSnapshot = tracksCopy;
        artistsSnapshot = artistsCopy;
        folderTreeSnapshot = folderTree;

        notifyListeners();
    }

    private List<File> listMusicFiles(File root) {
        List<File> result = new ArrayList<>();
        File[] files = root.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isDirectory()) {
                if (f.getName().startsWith(".")) continue;
                result.addAll(listMusicFiles(f));
            } else {
                AudioFormatType t = AudioFormatType.fromFileName(f.getName());
                if (t != AudioFormatType.UNKNOWN) result.add(f);
            }
        }
        return result;
    }

    public List<Track> getAllTracks() {
        return new ArrayList<>(allTracksSnapshot);
    }

    public Collection<Artist> getArtists() {
        return new ArrayList<>(artistsSnapshot.values());
    }

    public List<String> getArtistNamesSorted() {
        return artistsSnapshot.keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public Artist getArtist(String name) { return artistsSnapshot.get(name); }

    public List<Album> getAlbumsForArtist(String artistName) {
        Artist a = artistsSnapshot.get(artistName);
        if (a == null) return List.of();
        List<Album> list = new ArrayList<>(a.getAlbums());
        list.sort(Comparator.comparing(Album::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public List<Track> getTracksForAlbum(String artistName, String albumName) {
        Artist a = artistsSnapshot.get(artistName);
        if (a == null) return List.of();
        Album album = a.getAlbumMap().get(albumName);
        if (album == null) return List.of();
        return new ArrayList<>(album.getTracks());
    }

    public List<Track> getTracksForArtist(String artistName) {
        Artist a = artistsSnapshot.get(artistName);
        if (a == null) return List.of();
        return a.getAllTracks();
    }

    public List<String> getAlbumNames() {
        Set<String> set = new LinkedHashSet<>();
        for (Artist a : artistsSnapshot.values()) {
            for (Album al : a.getAlbums()) set.add(al.getName());
        }
        List<String> list = new ArrayList<>(set);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public long getTotalDurationSeconds() {
        long total = 0;
        for (Track t : allTracksSnapshot) total += Math.max(0, t.getDurationSeconds());
        return total;
    }

    public int getAlbumCount() {
        Set<String> set = new LinkedHashSet<>();
        for (Artist a : artistsSnapshot.values()) {
            for (Album al : a.getAlbums()) set.add(al.getName());
        }
        return set.size();
    }

    public String getDurationFormatted(long seconds) {
        if (seconds <= 0) return "0:00";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) return String.format("%dд %02d:%02d:%02d", days, hours, mins, secs);
        if (hours > 0) return String.format("%d:%02d:%02d", hours, mins, secs);
        return String.format("%d:%02d", mins, secs);
    }

    public FolderNode getFolderTree() {
        return folderTreeSnapshot;
    }

    private String getFolderRelativePath(File file) {
        if (musicFolder == null) {
            String parent = file.getParent();
            return parent != null ? parent : "";
        }
        try {
            String rootPath = musicFolder.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(rootPath)) {
                String rel = filePath.substring(rootPath.length());
                int idx = rel.lastIndexOf(File.separator);
                if (idx >= 0) rel = rel.substring(0, idx);
                if (rel.startsWith(File.separator)) rel = rel.substring(1);
                return rel.isEmpty() ? "/" : rel;
            }
        } catch (Exception ignored) {}
        String parent = file.getParent();
        return parent != null ? parent : "";
    }

    private FolderNode buildFolderTree(File rootFolder, List<Track> tracks) {
        // индекс по пути для быстрого поиска
        Map<String, Track> pathToTrack = new HashMap<>();
        for (Track t : tracks) {
            try {
                pathToTrack.put(t.getFile().getCanonicalPath(), t);
            } catch (Exception e) {
                pathToTrack.put(t.getFile().getAbsolutePath(), t);
            }
        }
        return buildFolderTreeRecursive(rootFolder, null, pathToTrack);
    }

    private FolderNode buildFolderTreeRecursive(File folder, FolderNode parent, Map<String, Track> pathToTrack) {
        FolderNode node = new FolderNode(folder, parent);
        File[] files = folder.listFiles();
        if (files == null) return node;

        List<File> dirs = new ArrayList<>();
        List<File> musicFiles = new ArrayList<>();
        for (File f : files) {
            if (f.isDirectory()) {
                if (f.getName().startsWith(".")) continue;
                dirs.add(f);
            } else {
                AudioFormatType t = AudioFormatType.fromFileName(f.getName());
                if (t != AudioFormatType.UNKNOWN) {
                    if (t.isDSD() && !ModConfig.enableDSF()) continue;
                    musicFiles.add(f);
                }
            }
        }
        dirs.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        musicFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File d : dirs) {
            FolderNode child = buildFolderTreeRecursive(d, node, pathToTrack);
            if (child.getTotalTrackCountRecursive() > 0 || !child.getChildren().isEmpty()) {
                node.addChild(child);
            }
        }

        for (File mf : musicFiles) {
            try {
                Track existing = pathToTrack.get(mf.getCanonicalPath());
                if (existing == null) existing = pathToTrack.get(mf.getAbsolutePath());
                if (existing != null) {
                    node.addTrack(existing);
                } else {
                    Track tr = new Track(mf);
                    MetadataParser.enrich(tr);
                    tr.setFolderPath(getFolderRelativePath(mf));
                    node.addTrack(tr);
                }
            } catch (Exception e) {
                Track tr = new Track(mf);
                tr.setFolderPath(getFolderRelativePath(mf));
                node.addTrack(tr);
            }
        }
        return node;
    }
}
