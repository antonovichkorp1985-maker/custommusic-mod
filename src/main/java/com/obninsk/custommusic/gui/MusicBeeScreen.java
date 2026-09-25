package com.obninsk.custommusic.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obninsk.custommusic.music.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Единый экран плеера в стиле MusicBee.
 * Layout:
 *   Top bar: search, view switch, rescan, open folder
 *   Left: folder/artist/album tree
 *   Center: track table with column headers
 *   Right: now playing queue + track info
 *   Bottom: playback controls + progress + volume
 */
public class MusicBeeScreen extends Screen implements AudioPlayerManager.PlaybackListener, MusicLibraryManager.LibraryListener {

    private MusicLibraryManager library;
    private PlaylistManager playlistManager;
    private AudioPlayerManager playerManager;

    // Layout
    private static final int TOP_BAR_H = 26;
    private static final int BOTTOM_BAR_H = 34;
    private static final int HEADER_H = 13;
    private static final int ROW_H = 12;
    private static final int MARGIN = 4;
    private static final int GAP = 4;
    private static final int TREE_INDENT = 10;
    private static final int LEFT_PANEL_MIN = 130;
    private static final int LEFT_PANEL_MAX = 280;
    private static final int RIGHT_PANEL_MIN = 160;
    private static final int RIGHT_PANEL_MAX = 300;

    // Colors (ARGB)
    private static final int C_BG = 0xFF151515;
    private static final int C_PANEL = 0xFF1E1E1E;
    private static final int C_HEADER = 0xFF2A2A2A;
    private static final int C_BORDER = 0xFF3A3A3A;
    private static final int C_ROW_EVEN = 0xFF222222;
    private static final int C_ROW_ODD = 0xFF1A1A1A;
    private static final int C_HOVER = 0xFF333333;
    private static final int C_SELECTED = 0xFF3E4A5E;
    private static final int C_PLAYING = 0xFF2D5A3D;
    private static final int C_TEXT = 0xFFE0E0E0;
    private static final int C_TEXT_DIM = 0xFFAAAAAA;
    private static final int C_ACCENT = 0xFF5A9FD4;
    private static final int C_GREEN = 0xFF55FF55;
    private static final int C_YELLOW = 0xFFFFFF55;

    private int leftPanelW, rightPanelW;
    private int centerX, centerW, centerY, centerH;
    private int treeY, treeH, tableY, tableH;
    private int rightY, rightH, infoY, queueH;

    // Tree
    private TreeNode rootNode;
    private TreeNode selectedNode;
    private final List<TreeNode> flatTree = new ArrayList<>();
    private int treeScroll = 0;

    // Table
    private enum ViewMode { FOLDERS, ARTISTS, ALBUMS, ALL }
    private ViewMode viewMode = ViewMode.FOLDERS;
    private final List<Track> displayedTracks = new ArrayList<>();
    private int tableScroll = 0;

    private enum SortColumn { TRACK, TITLE, ARTIST, FOLDER, ALBUM, YEAR, RATING, TIME, FORMAT }
    private SortColumn sortColumn = SortColumn.TRACK;
    private boolean sortAsc = true;

    // Right panel
    private int queueScroll = 0;

    // Widgets
    private EditBox searchBox;
    private Button playPauseButton;
    private Button modeButton;
    private String statusMessage = "";
    private long statusMessageTime = 0;

    public MusicBeeScreen() {
        super(Component.literal("Custom Music — MusicBee"));
        this.library = MusicLibraryManager.getInstance();
        this.playlistManager = PlaylistManager.getInstance();
        this.playerManager = AudioPlayerManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        playerManager.setListener(this);
        library.addListener(this);

        // Force sequential order on open so the queue matches the displayed list by default
        playlistManager.setPlayMode(PlayMode.SEQUENTIAL);

        String cfgMode = com.obninsk.custommusic.config.ModConfig.defaultViewMode();
        try {
            viewMode = ViewMode.valueOf(cfgMode.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            viewMode = ViewMode.FOLDERS;
        }

        recalcLayout();
        buildTree();
        sortColumn = defaultSortForView(viewMode);
        sortAsc = true;
        refreshDisplayedTracks();

        if (library.isScanning()) {
            setStatus("Сканирование библиотеки...");
        } else if (displayedTracks.isEmpty()) {
            setStatus("Нет треков. Нажмите Обновить");
        }

        // Top bar widgets
        int topY = (TOP_BAR_H - 16) / 2;
        searchBox = new EditBox(this.font, MARGIN, topY, Math.min(150, leftPanelW - MARGIN * 2 - 50), 16, Component.literal("Search"));
        searchBox.setValue("");
        searchBox.setSuggestion("Поиск...");
        searchBox.setResponder(s -> {
            if (s != null && !s.isEmpty()) searchBox.setSuggestion(null);
            else searchBox.setSuggestion("Поиск...");
            refreshDisplayedTracks();
        });
        addRenderableWidget(searchBox);

        int clearX = MARGIN + searchBox.getWidth() + 2;
        addRenderableWidget(new Button(clearX, topY, 44, 16, Component.literal("Сброс"), b -> {
            searchBox.setValue("");
            refreshDisplayedTracks();
        }));

        int btnH = 16;
        int btnY = topY;
        int x = leftPanelW + MARGIN;
        addRenderableWidget(new Button(x, btnY, 50, btnH, Component.literal("Папки"), b -> setViewMode(ViewMode.FOLDERS)));
        x += 54;
        addRenderableWidget(new Button(x, btnY, 60, btnH, Component.literal("Артисты"), b -> setViewMode(ViewMode.ARTISTS)));
        x += 64;
        addRenderableWidget(new Button(x, btnY, 60, btnH, Component.literal("Альбомы"), b -> setViewMode(ViewMode.ALBUMS)));
        x += 64;
        addRenderableWidget(new Button(x, btnY, 50, btnH, Component.literal("Все"), b -> setViewMode(ViewMode.ALL)));
        x += 54 + 10;
        addRenderableWidget(new Button(x, btnY, 60, btnH, Component.literal("Обновить"), b -> rescan()));
        x += 64;
        addRenderableWidget(new Button(x, btnY, 60, btnH, Component.literal("Папка"), b -> openMusicFolder()));

        // Bottom bar widgets
        int by = this.height - BOTTOM_BAR_H + (BOTTOM_BAR_H - 20) / 2;
        int cx = MARGIN;
        addRenderableWidget(new Button(cx, by, 34, 20, Component.literal("|<<"), b -> prevTrack()));
        cx += 38;
        playPauseButton = addRenderableWidget(new Button(cx, by, 50, 20,
                Component.literal(playerManager.isPlaying() && !playerManager.isPaused() ? "||" : ">"), b -> togglePlayPause()));
        cx += 54;
        addRenderableWidget(new Button(cx, by, 34, 20, Component.literal(">>|"), b -> nextTrack()));
        cx += 42;
        modeButton = addRenderableWidget(new Button(cx, by, 70, 20,
                Component.literal(playlistManager.getPlayMode().getDisplayName()), b -> cycleMode()));
        cx += 78;
        addRenderableWidget(new Button(cx, by, 28, 20, Component.literal("-") , b -> changeVolume(-0.1f)));
        cx += 32;
        addRenderableWidget(new Button(cx, by, 28, 20, Component.literal("+") , b -> changeVolume(0.1f)));

        setInitialFocus(searchBox);
    }

    private void recalcLayout() {
        int usableW = this.width - MARGIN * 2;
        leftPanelW = Mth.clamp((int)(usableW * 0.18), LEFT_PANEL_MIN, LEFT_PANEL_MAX);
        rightPanelW = Mth.clamp((int)(usableW * 0.23), RIGHT_PANEL_MIN, RIGHT_PANEL_MAX);
        centerW = usableW - leftPanelW - rightPanelW - GAP * 2;
        centerX = MARGIN + leftPanelW + GAP;
        centerY = TOP_BAR_H + MARGIN;
        centerH = this.height - TOP_BAR_H - BOTTOM_BAR_H - MARGIN * 2;

        treeY = centerY;
        treeH = centerH;

        tableY = centerY + 56; // header with cover/info
        tableH = centerH - 56;

        rightY = centerY;
        rightH = centerH;
        queueH = rightH / 2;
        infoY = rightY + queueH + GAP;
    }

    // ==================== Tree ====================

    private static class TreeNode {
        String name;
        TreeNode parent;
        final List<TreeNode> children = new ArrayList<>();
        boolean expanded = true;
        Object data; // FolderNode, Artist, Album name, or null
        int trackCount = 0;
        boolean isAllTracks = false;
        boolean isSection = false;

        TreeNode(String name, TreeNode parent) {
            this.name = name;
            this.parent = parent;
        }

        void addChild(TreeNode child) { children.add(child); }
    }

    private void buildTree() {
        String prevPath = selectedNode != null ? getNodePath(selectedNode) : null;

        rootNode = new TreeNode("Музыка", null);
        rootNode.isSection = true;
        rootNode.trackCount = library.getAllTracks().size();

        TreeNode allTracks = new TreeNode("Все треки", rootNode);
        allTracks.isAllTracks = true;
        allTracks.trackCount = library.getAllTracks().size();
        rootNode.addChild(allTracks);
        selectedNode = allTracks;

        if (viewMode == ViewMode.FOLDERS || viewMode == ViewMode.ALL) {
            FolderNode folderRoot = library.getFolderTree();
            if (folderRoot != null) {
                TreeNode folders = new TreeNode("Папки", rootNode);
                folders.isSection = true;
                folders.trackCount = folderRoot.getTotalTrackCountRecursive();
                rootNode.addChild(folders);
                for (FolderNode child : folderRoot.getChildren()) {
                    folders.addChild(buildFolderNode(child, folders));
                }
            }
        }

        if (viewMode == ViewMode.ARTISTS || viewMode == ViewMode.ALL) {
            TreeNode artists = new TreeNode("Исполнители", rootNode);
            artists.isSection = true;
            rootNode.addChild(artists);
            for (String name : library.getArtistNamesSorted()) {
                Artist artist = library.getArtist(name);
                if (artist == null) continue;
                TreeNode an = new TreeNode(name, artists);
                an.data = artist;
                an.trackCount = artist.getAllTracks().size();
                artists.addChild(an);
            }
        }

        if (viewMode == ViewMode.ALBUMS || viewMode == ViewMode.ALL) {
            TreeNode albums = new TreeNode("Альбомы", rootNode);
            albums.isSection = true;
            rootNode.addChild(albums);
            for (String name : library.getAlbumNames()) {
                TreeNode aln = new TreeNode(name, albums);
                aln.data = name;
                aln.trackCount = (int) library.getAllTracks().stream().filter(t -> t.getAlbum().equals(name)).count();
                albums.addChild(aln);
            }
        }

        flattenTree();

        // Try to restore previous selection by path
        if (prevPath != null) {
            TreeNode restored = findNodeByPath(rootNode, prevPath);
            if (restored != null) selectedNode = restored;
        }
    }

    private String getNodePath(TreeNode node) {
        StringBuilder sb = new StringBuilder();
        TreeNode cur = node;
        while (cur != null) {
            if (!sb.isEmpty()) sb.insert(0, "/");
            sb.insert(0, cur.name);
            cur = cur.parent;
        }
        return sb.toString();
    }

    private TreeNode findNodeByPath(TreeNode root, String path) {
        if (root == null || path == null) return null;
        if (getNodePath(root).equals(path)) return root;
        for (TreeNode child : root.children) {
            TreeNode found = findNodeByPath(child, path);
            if (found != null) return found;
        }
        return null;
    }

    private TreeNode buildFolderNode(FolderNode folder, TreeNode parent) {
        TreeNode node = new TreeNode(folder.getName(), parent);
        node.data = folder;
        node.trackCount = folder.getTotalTrackCountRecursive();
        for (FolderNode child : folder.getChildren()) {
            node.addChild(buildFolderNode(child, node));
        }
        return node;
    }

    private void flattenTree() {
        flatTree.clear();
        for (TreeNode child : rootNode.children) {
            flattenRecursive(child, 0);
        }
    }

    private void flattenRecursive(TreeNode node, int depth) {
        flatTree.add(node);
        if (node.expanded) {
            for (TreeNode child : node.children) {
                flattenRecursive(child, depth + 1);
            }
        }
    }

    // ==================== Display / Sort ====================

    private void setViewMode(ViewMode mode) {
        this.viewMode = mode;
        buildTree();
        sortColumn = defaultSortForView(mode);
        sortAsc = true;
        refreshDisplayedTracks();
    }

    private SortColumn defaultSortForView(ViewMode mode) {
        return switch (mode) {
            case FOLDERS -> SortColumn.FOLDER;
            case ARTISTS -> SortColumn.ARTIST;
            case ALBUMS -> SortColumn.ALBUM;
            case ALL -> SortColumn.FOLDER;
        };
    }

    private void refreshDisplayedTracks() {
        displayedTracks.clear();
        List<Track> base;
        if (selectedNode == null || selectedNode.isAllTracks) {
            base = new ArrayList<>(library.getAllTracks());
        } else if (selectedNode.data instanceof FolderNode fn) {
            base = fn.getAllTracksRecursive();
        } else if (selectedNode.data instanceof Artist artist) {
            base = new ArrayList<>(artist.getAllTracks());
        } else if (selectedNode.data instanceof String albumName) {
            base = library.getAllTracks().stream()
                    .filter(t -> t.getAlbum().equals(albumName))
                    .collect(Collectors.toList());
        } else {
            base = new ArrayList<>(library.getAllTracks());
        }

        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT).trim() : "";
        if (!query.isEmpty()) {
            base = base.stream().filter(t ->
                    t.getTitle().toLowerCase(Locale.ROOT).contains(query) ||
                    t.getArtist().toLowerCase(Locale.ROOT).contains(query) ||
                    t.getAlbum().toLowerCase(Locale.ROOT).contains(query) ||
                    t.getFile().getName().toLowerCase(Locale.ROOT).contains(query)
            ).collect(Collectors.toList());
        }

        displayedTracks.addAll(base);
        sortTracks();
        tableScroll = 0;
    }

    private void sortTracks() {
        Comparator<Track> comp;
        switch (sortColumn) {
            case TRACK -> comp = Comparator.comparingInt(Track::getTrackNumber);
            case TITLE -> comp = Comparator.comparing(Track::getTitle, String.CASE_INSENSITIVE_ORDER);
            case ARTIST -> comp = Comparator.comparing(Track::getArtist, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Track::getAlbum, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Track::getTrackNumber);
            case FOLDER -> comp = Comparator.comparing(Track::getFolderPath, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Track::getAlbum, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Track::getTrackNumber);
            case ALBUM -> comp = Comparator.comparing(Track::getAlbum, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Track::getTrackNumber);
            case YEAR -> comp = Comparator.comparing(t -> t.getYear() == null ? "" : t.getYear());
            case RATING -> comp = Comparator.comparingInt(Track::getRating);
            case TIME -> comp = Comparator.comparingLong(Track::getDurationSeconds);
            case FORMAT -> comp = Comparator.comparing(t -> t.getFormat().getExt());
            default -> comp = Comparator.comparingInt(Track::getTrackNumber);
        }
        if (!sortAsc) comp = comp.reversed();
        displayedTracks.sort(comp);
    }

    // ==================== Actions ====================

    private void rescan() {
        setStatus("Сканирование...");
        library.scanAsync().thenRun(() -> Minecraft.getInstance().execute(() -> {
            buildTree();
            refreshDisplayedTracks();
            setStatus("Найдено " + library.getAllTracks().size() + " треков");
        }));
    }

    private void openMusicFolder() {
        try {
            net.minecraft.Util.getPlatform().openUri(library.getMusicFolder().toURI());
        } catch (Exception e) {
            setStatus("Папка: " + library.getMusicFolder().getAbsolutePath());
        }
    }

    private void playTrack(Track track, int index) {
        playlistManager.setQueue(new ArrayList<>(displayedTracks), index);
        playerManager.play(track);
    }

    private void togglePlayPause() {
        if (playerManager.isPlaying()) {
            playerManager.togglePause();
        } else {
            Track cur = playlistManager.getCurrentTrack();
            if (cur != null) playerManager.play(cur);
            else if (!displayedTracks.isEmpty()) playTrack(displayedTracks.get(0), 0);
            else setStatus("Нет треков");
        }
    }

    private void nextTrack() {
        Track next = playlistManager.next();
        if (next != null) playerManager.play(next);
        else setStatus("Конец очереди");
    }

    private void prevTrack() {
        Track prev = playlistManager.previous();
        if (prev != null) playerManager.play(prev);
        else setStatus("Нет предыдущего");
    }

    private void cycleMode() {
        PlayMode next = playlistManager.getPlayMode().next();
        playlistManager.setPlayMode(next);
        if (modeButton != null) modeButton.setMessage(Component.literal(next.getDisplayName()));
        setStatus("Режим: " + next.getDisplayName());
    }

    private void changeVolume(float delta) {
        playerManager.setVolume(playerManager.getVolume() + delta);
        setStatus(String.format("Громкость: %d%%", (int)(playerManager.getVolume() * 100)));
    }

    // ==================== Rendering ====================

    @Override
    public void render(PoseStack ps, int mx, int my, float partialTicks) {
        recalcLayout();
        renderBackground(ps);

        // Draw backgrounds first, then widgets on top
        renderTopBar(ps, mx, my);
        renderBottomBar(ps, mx, my);
        drawPanel(ps, MARGIN, TOP_BAR_H, leftPanelW, this.height - TOP_BAR_H - BOTTOM_BAR_H, C_PANEL);
        drawPanel(ps, centerX, centerY, centerW, centerH, C_PANEL);
        drawPanel(ps, this.width - MARGIN - rightPanelW, centerY, rightPanelW, centerH, C_PANEL);

        super.render(ps, mx, my, partialTicks);

        renderTreePanel(ps, mx, my);
        renderCenterHeader(ps);
        renderTable(ps, mx, my);
        renderRightPanel(ps, mx, my);

        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 4000) {
            drawString(ps, font, statusMessage, MARGIN, this.height - BOTTOM_BAR_H - 12, C_YELLOW);
        }
    }

    private void drawPanel(PoseStack ps, int x, int y, int w, int h, int color) {
        fill(ps, x, y, x + w, y + h, color);
        fill(ps, x, y, x + w, y + 1, C_BORDER);
        fill(ps, x, y + h - 1, x + w, y + h, C_BORDER);
        fill(ps, x, y, x + 1, y + h, C_BORDER);
        fill(ps, x + w - 1, y, x + w, y + h, C_BORDER);
    }

    private void renderTopBar(PoseStack ps, int mx, int my) {
        fill(ps, 0, 0, this.width, TOP_BAR_H, C_HEADER);
        fill(ps, 0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_BORDER);
        String title = "Custom Music — MusicBee";
        drawString(ps, font, title, this.width / 2 - font.width(title) / 2, 9, C_ACCENT);
    }

    private void renderTreePanel(PoseStack ps, int mx, int my) {
        int x = MARGIN;
        int y = treeY;
        int w = leftPanelW;
        int h = treeH;

        // Header
        fill(ps, x, y, x + w, y + HEADER_H, C_HEADER);
        drawString(ps, font, "Библиотека", x + 4, y + 3, C_TEXT);
        y += HEADER_H;
        h -= HEADER_H;

        int visible = Math.max(1, h / ROW_H);
        treeScroll = Mth.clamp(treeScroll, 0, Math.max(0, flatTree.size() - visible));

        for (int i = 0; i < visible; i++) {
            int idx = treeScroll + i;
            if (idx >= flatTree.size()) break;
            TreeNode node = flatTree.get(idx);
            int rowY = y + i * ROW_H;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_H;
            boolean selected = node == selectedNode;
            int bg = selected ? C_SELECTED : hovered ? C_HOVER : (i % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            fill(ps, x, rowY, x + w, rowY + ROW_H, bg);

            int indent = getNodeDepth(node) * TREE_INDENT;
            int textX = x + 4 + indent;

            // expand icon
            if (!node.children.isEmpty()) {
                String icon = node.expanded ? "-" : "+";
                drawString(ps, font, icon, textX, rowY + 2, C_TEXT_DIM);
                textX += 10;
            }

            String name = node.name;
            if (!node.children.isEmpty() || node.trackCount > 0) {
                name += " (" + node.trackCount + ")";
            }
            if (font.width(name) > w - (textX - x) - 6) {
                name = font.plainSubstrByWidth(name, w - (textX - x) - 12) + "..";
            }
            int col = node.isSection ? C_ACCENT : (selected ? 0xFFFFFFFF : C_TEXT);
            drawString(ps, font, name, textX, rowY + 2, col);
        }
    }

    private int getNodeDepth(TreeNode node) {
        int d = 0;
        TreeNode p = node.parent;
        while (p != null && p != rootNode) {
            d++;
            p = p.parent;
        }
        return d;
    }

    private void renderCenterHeader(PoseStack ps) {
        int x = centerX;
        int y = centerY;
        int w = centerW;
        int hh = 56;

        fill(ps, x, y, x + w, y + hh, C_HEADER);
        // Cover placeholder
        fill(ps, x + 6, y + 6, x + 44, y + 44, 0xFF333333);
        drawString(ps, font, "♪", x + 18, y + 16, C_ACCENT);

        String header = selectedNode != null ? selectedNode.name : "Все треки";
        String searchText = (searchBox != null && !searchBox.getValue().isBlank())
                ? " [Поиск: \"" + searchBox.getValue() + "\"]" : "";
        String stats = String.format("%d треков · %s%s",
                displayedTracks.size(),
                library.getDurationFormatted(displayedTracks.stream().mapToLong(Track::getDurationSeconds).sum()),
                searchText);
        drawString(ps, font, header, x + 50, y + 10, 0xFFFFFFFF);
        drawString(ps, font, stats, x + 50, y + 24, C_TEXT_DIM);
        String viewText = "Вид: " + switch (viewMode) {
            case FOLDERS -> "Папки";
            case ARTISTS -> "Артисты";
            case ALBUMS -> "Альбомы";
            case ALL -> "Все";
        };
        drawString(ps, font, viewText, x + 50, y + 38, C_TEXT_DIM);
    }

    private void renderTable(PoseStack ps, int mx, int my) {
        int x = centerX;
        int y = tableY;
        int w = centerW;
        int h = tableH;

        // Column headers
        fill(ps, x, y, x + w, y + HEADER_H, C_HEADER);
        int[] colWidths = computeColumnWidths(w);
        int[] colX = new int[COLUMNS.length];
        int cx = x;
        for (int i = 0; i < COLUMNS.length; i++) {
            colX[i] = cx;
            String label = COLUMNS[i].label;
            if (sortColumn == COLUMNS[i].col) {
                label += (sortAsc ? " ▲" : " ▼");
            }
            drawString(ps, font, label, cx + 3, y + 3, C_TEXT);
            cx += colWidths[i];
            fill(ps, cx - 1, y, cx, y + HEADER_H, C_BORDER);
        }
        y += HEADER_H;
        h -= HEADER_H;

        // Rows
        int visible = Math.max(1, h / ROW_H);
        tableScroll = Mth.clamp(tableScroll, 0, Math.max(0, displayedTracks.size() - visible));
        Track current = playerManager.getNowPlaying();

        for (int i = 0; i < visible; i++) {
            int idx = tableScroll + i;
            if (idx >= displayedTracks.size()) break;
            Track t = displayedTracks.get(idx);
            int rowY = y + i * ROW_H;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_H;
            boolean playing = current != null && current.equals(t);
            int bg = playing ? C_PLAYING : hovered ? C_HOVER : (i % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            fill(ps, x, rowY, x + w, rowY + ROW_H, bg);

            cx = x;
            String[] cells = formatCells(t, idx + 1);
            for (int c = 0; c < cells.length; c++) {
                String txt = cells[c];
                int cw = colWidths[c];
                if (font.width(txt) > cw - 6) txt = font.plainSubstrByWidth(txt, cw - 12) + "..";
                int col = playing ? C_GREEN : (hovered ? 0xFFFFFFFF : C_TEXT);
                drawString(ps, font, txt, cx + 3, rowY + 2, col);
                cx += cw;
            }
        }
    }

    private record ColumnDef(SortColumn col, String label, int minW, int weight) {}
    private static final ColumnDef[] COLUMNS = {
            new ColumnDef(SortColumn.TRACK, "№", 28, 0),
            new ColumnDef(SortColumn.TITLE, "Название", 120, 3),
            new ColumnDef(SortColumn.ARTIST, "Исполнитель", 90, 2),
            new ColumnDef(SortColumn.FOLDER, "Папка", 70, 1),
            new ColumnDef(SortColumn.ALBUM, "Альбом", 90, 2),
            new ColumnDef(SortColumn.YEAR, "Год", 36, 0),
            new ColumnDef(SortColumn.RATING, "★", 40, 0),
            new ColumnDef(SortColumn.TIME, "Время", 48, 0),
            new ColumnDef(SortColumn.FORMAT, "Формат", 50, 0)
    };

    private int[] computeColumnWidths(int totalW) {
        int[] widths = new int[COLUMNS.length];
        int fixed = 0;
        int totalWeight = 0;
        for (int i = 0; i < COLUMNS.length; i++) {
            if (COLUMNS[i].weight == 0) {
                widths[i] = COLUMNS[i].minW;
                fixed += widths[i];
            } else {
                totalWeight += COLUMNS[i].weight;
            }
        }
        int remaining = Math.max(0, totalW - fixed);
        for (int i = 0; i < COLUMNS.length; i++) {
            if (COLUMNS[i].weight > 0) {
                widths[i] = COLUMNS[i].minW + (int) (remaining * (COLUMNS[i].weight / (float) totalWeight));
            }
        }
        return widths;
    }

    private String[] formatCells(Track t, int displayNo) {
        String folder = t.getFolderPath();
        if (folder.isEmpty()) folder = t.getFile().getParentFile() != null ? t.getFile().getParentFile().getName() : "";
        return new String[] {
                t.getTrackNumber() > 0 ? String.valueOf(t.getTrackNumber()) : String.valueOf(displayNo),
                t.getTitle(),
                t.getArtist(),
                folder,
                t.getAlbum(),
                t.getYear() != null ? t.getYear() : "",
                "★".repeat(t.getRating()) + "☆".repeat(Math.max(0, 5 - t.getRating())),
                t.getDurationFormatted(),
                t.getFormat().getExt().toUpperCase(Locale.ROOT)
        };
    }

    private void renderRightPanel(PoseStack ps, int mx, int my) {
        int x = this.width - MARGIN - rightPanelW;
        int y = rightY;
        int w = rightPanelW;

        // Now playing queue
        fill(ps, x, y, x + w, y + HEADER_H, C_HEADER);
        drawString(ps, font, "Очередь", x + 4, y + 3, C_TEXT);
        y += HEADER_H;
        int qh = queueH - HEADER_H;
        fill(ps, x, y, x + w, y + qh, C_ROW_ODD);

        List<Track> queue = playlistManager.getCurrentQueue();
        int currentIdx = playlistManager.getCurrentIndex();
        int visible = Math.max(1, qh / ROW_H);
        queueScroll = Mth.clamp(queueScroll, 0, Math.max(0, queue.size() - visible));

        for (int i = 0; i < visible; i++) {
            int idx = queueScroll + i;
            if (idx >= queue.size()) break;
            Track t = queue.get(idx);
            int rowY = y + i * ROW_H;
            boolean isCurrent = idx == currentIdx;
            int bg = isCurrent ? C_PLAYING : (i % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            fill(ps, x, rowY, x + w, rowY + ROW_H, bg);
            String txt = (idx + 1) + ". " + t.getArtist() + " - " + t.getTitle();
            if (font.width(txt) > w - 8) txt = font.plainSubstrByWidth(txt, w - 14) + "..";
            int col = isCurrent ? C_GREEN : C_TEXT;
            drawString(ps, font, txt, x + 3, rowY + 2, col);
        }

        // Track info
        y = infoY;
        fill(ps, x, y, x + w, y + HEADER_H, C_HEADER);
        drawString(ps, font, "О треке", x + 4, y + 3, C_TEXT);
        y += HEADER_H;
        int infoH = rightH - queueH - GAP - HEADER_H;
        fill(ps, x, y, x + w, y + infoH, C_ROW_ODD);

        Track now = playerManager.getNowPlaying();
        if (now != null) {
            // Cover placeholder
            fill(ps, x + (w - 64) / 2, y + 6, x + (w - 64) / 2 + 64, y + 70, 0xFF333333);
            drawString(ps, font, "♪", x + (w - 64) / 2 + 26, y + 26, C_ACCENT);
            int ty = y + 78;
            drawCentered(ps, now.getTitle(), x + w / 2, ty, 0xFFFFFFFF, w - 8);
            ty += 12;
            drawCentered(ps, now.getArtist(), x + w / 2, ty, C_TEXT_DIM, w - 8);
            ty += 12;
            drawCentered(ps, now.getAlbum(), x + w / 2, ty, C_TEXT_DIM, w - 8);
            ty += 14;
            String meta = now.getFormat().getExt().toUpperCase(Locale.ROOT) + " · " + now.getDurationFormatted();
            if (now.getBitrate() > 0) meta += " · " + now.getBitrate() + "kbps";
            drawCentered(ps, meta, x + w / 2, ty, C_TEXT_DIM, w - 8);
        } else {
            drawCentered(ps, "Ничего не играет", x + w / 2, y + infoH / 2, C_TEXT_DIM, w - 8);
        }
    }

    private void drawCentered(PoseStack ps, String text, int cx, int y, int color, int maxW) {
        if (font.width(text) > maxW) text = font.plainSubstrByWidth(text, maxW - 8) + "..";
        drawString(ps, font, text, cx - font.width(text) / 2, y, color);
    }

    private void renderBottomBar(PoseStack ps, int mx, int my) {
        int y = this.height - BOTTOM_BAR_H;
        fill(ps, 0, y, this.width, y + BOTTOM_BAR_H, C_HEADER);
        fill(ps, 0, y, this.width, y + 1, C_BORDER);

        Track now = playerManager.getNowPlaying();
        String trackLine = now != null ? now.getArtist() + " - " + now.getTitle() : "Нет воспроизведения";
        drawString(ps, font, trackLine, 260, y + 10, now != null ? C_GREEN : C_TEXT_DIM);

        // Progress bar
        int px = 260;
        int py = y + 22;
        int pw = Math.max(80, this.width - px - 160);
        int ph = 6;
        fill(ps, px, py, px + pw, py + ph, 0xFF444444);
        if (now != null) {
            float ratio = playerManager.getProgressRatio();
            fill(ps, px, py, px + (int) (pw * ratio), py + ph, C_ACCENT);
            String time = formatTime(playerManager.getCurrentPositionSeconds()) + " / " + now.getDurationFormatted();
            drawString(ps, font, time, px + pw + 6, py - 2, C_TEXT);
        }

        String vol = String.format("Громк: %d%%", (int)(playerManager.getVolume() * 100));
        drawString(ps, font, vol, this.width - 90, y + 10, C_TEXT_DIM);
    }

    private String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // ==================== Input ====================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Tree clicks (left button only)
        int tx = MARGIN;
        int ty = treeY + HEADER_H;
        if (button == 0 && mx >= tx && mx < tx + leftPanelW && my >= ty && my < treeY + treeH) {
            int row = (int) ((my - ty) / ROW_H);
            int idx = treeScroll + row;
            if (idx >= 0 && idx < flatTree.size()) {
                TreeNode node = flatTree.get(idx);
                int indent = getNodeDepth(node) * TREE_INDENT;
                int iconX = tx + 4 + indent;
                boolean clickOnExpandIcon = !node.children.isEmpty() && mx >= iconX - 2 && mx < iconX + 8;
                if (clickOnExpandIcon) {
                    node.expanded = !node.expanded;
                    flattenTree();
                } else {
                    selectedNode = node;
                    refreshDisplayedTracks();
                }
            }
            return true;
        }

        // Table header clicks (sort)
        int cx = centerX;
        if (my >= tableY && my < tableY + HEADER_H && mx >= cx && mx < cx + centerW) {
            int[] colWidths = computeColumnWidths(centerW);
            int acc = cx;
            for (int i = 0; i < COLUMNS.length; i++) {
                if (mx >= acc && mx < acc + colWidths[i]) {
                    if (sortColumn == COLUMNS[i].col) sortAsc = !sortAsc;
                    else { sortColumn = COLUMNS[i].col; sortAsc = true; }
                    sortTracks();
                    return true;
                }
                acc += colWidths[i];
            }
        }

        // Table row clicks (left button only)
        int tableBodyY = tableY + HEADER_H;
        if (button == 0 && mx >= centerX && mx < centerX + centerW && my >= tableBodyY && my < tableBodyY + tableH - HEADER_H) {
            int row = (int) ((my - tableBodyY) / ROW_H);
            int idx = tableScroll + row;
            if (idx >= 0 && idx < displayedTracks.size()) {
                playTrack(displayedTracks.get(idx), idx);
            }
            return true;
        }

        // Queue clicks (left button only)
        int qx = this.width - MARGIN - rightPanelW;
        int qy = rightY + HEADER_H;
        if (button == 0 && mx >= qx && mx < qx + rightPanelW && my >= qy && my < qy + queueH - HEADER_H) {
            int row = (int) ((my - qy) / ROW_H);
            List<Track> queue = playlistManager.getCurrentQueue();
            int idx = queueScroll + row;
            if (idx >= 0 && idx < queue.size()) {
                playlistManager.setCurrentIndex(idx);
                playerManager.play(queue.get(idx));
            }
            return true;
        }

        // Progress bar click (left button only)
        if (button == 0) {
            int by = this.height - BOTTOM_BAR_H;
            int px = 260;
            int py = by + 22;
            int pw = Math.max(80, this.width - px - 160);
            int ph = 6;
            if (mx >= px && mx < px + pw && my >= py && my < py + ph) {
                Track now = playerManager.getNowPlaying();
                if (now != null && now.getDurationSeconds() > 0) {
                    float ratio = (float) (mx - px) / (float) pw;
                    long target = (long) (ratio * now.getDurationSeconds());
                    playerManager.seekTo(target);
                    setStatus("Перемотка на " + formatTime(target));
                }
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Tree
        if (mx >= MARGIN && mx < MARGIN + leftPanelW && my >= treeY && my < treeY + treeH) {
            treeScroll -= (int) delta;
            return true;
        }
        // Table
        if (mx >= centerX && mx < centerX + centerW && my >= tableY && my < tableY + tableH) {
            tableScroll -= (int) delta;
            return true;
        }
        // Queue
        if (mx >= this.width - MARGIN - rightPanelW && mx < this.width - MARGIN && my >= rightY && my < rightY + queueH) {
            queueScroll -= (int) delta;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (searchBox != null && searchBox.isFocused() && !searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
                refreshDisplayedTracks();
                return true;
            } else if (searchBox != null && searchBox.isFocused()) {
                this.setFocused(null);
                return true;
            }
        }
        if (searchBox != null && searchBox.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE -> { togglePlayPause(); return true; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> { nextTrack(); return true; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> { prevTrack(); return true; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> { tableScroll = Math.max(0, tableScroll - 1); return true; }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> { tableScroll = Math.min(Math.max(0, displayedTracks.size() - 1), tableScroll + 1); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== Listener ====================

    private void updateButtons() {
        if (playPauseButton != null) {
            playPauseButton.setMessage(Component.literal(playerManager.isPlaying() && !playerManager.isPaused() ? "||" : ">"));
        }
        if (modeButton != null) {
            modeButton.setMessage(Component.literal(playlistManager.getPlayMode().getDisplayName()));
        }
    }

    private void setStatus(String msg) { statusMessage = msg; statusMessageTime = System.currentTimeMillis(); }

    @Override public void onTrackStarted(Track track) {
        Minecraft.getInstance().execute(() -> { updateButtons(); });
    }
    @Override public void onTrackFinished(Track track) {
        Minecraft.getInstance().execute(this::updateButtons);
    }
    @Override public void onError(Track track, String error) {
        Minecraft.getInstance().execute(() -> setStatus("Ошибка: " + error));
    }
    @Override public void onPaused(Track track) {
        Minecraft.getInstance().execute(() -> { setStatus("Пауза"); updateButtons(); });
    }
    @Override public void onResumed(Track track) {
        Minecraft.getInstance().execute(() -> { setStatus("Продолжение"); updateButtons(); });
    }
    @Override
    public void onLibraryUpdated() {
        if (Minecraft.getInstance().screen == this) {
            Minecraft.getInstance().execute(() -> {
                buildTree();
                refreshDisplayedTracks();
                if (displayedTracks.isEmpty()) {
                    setStatus("Библиотека пуста");
                } else {
                    setStatus("Библиотека обновлена: " + library.getAllTracks().size() + " треков");
                }
            });
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { playerManager.setListener(null); library.removeListener(this); super.onClose(); }
}
