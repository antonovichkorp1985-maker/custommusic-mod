package com.obninsk.custommusic.gui;

import net.minecraft.client.gui.GuiGraphics;
import com.obninsk.custommusic.music.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Режим отображения чисто по папкам - без артистов/альбомов
 */
public class FolderBrowserScreen extends Screen implements AudioPlayerManager.PlaybackListener {

    private MusicLibraryManager library;
    private PlaylistManager playlistManager;
    private AudioPlayerManager playerManager;

    private FolderNode rootNode;
    private FolderNode currentNode;

    private final List<FolderNode> currentSubfolders = new ArrayList<>();
    private List<Track> currentTracks = new ArrayList<>();
    private List<Track> filteredTracks = new ArrayList<>();

    private int folderScroll = 0;
    private int trackScroll = 0;
    private static final int ROW_HEIGHT = 12;
    private static final int MARGIN = 6;

    private EditBox searchBox;
    private Button playPauseButton;
    private String statusMessage = "";
    private long statusMessageTime = 0;

    private int colTop, colBottom, leftX, rightX, colW, midGap = 6;

    public FolderBrowserScreen() {
        super(Component.literal("Custom Music - Папки"));
        this.library = MusicLibraryManager.getInstance();
        this.playlistManager = PlaylistManager.getInstance();
        this.playerManager = AudioPlayerManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        playerManager.setListener(this);

        rootNode = library.getFolderTree();
        if (rootNode == null) rootNode = new FolderNode(library.getMusicFolder(), null);
        if (currentNode == null) currentNode = rootNode;

        refreshCurrentFolder();

        int bottomY = this.height - 24;
        int searchW = Math.min(180, this.width / 3);
        searchBox = new EditBox(this.font, MARGIN, MARGIN, searchW, 16, Component.literal("Search"));
        searchBox.setValue("");
        searchBox.setSuggestion("Поиск по файлам...");
        searchBox.setResponder(s -> {
            if (s != null && !s.isEmpty()) searchBox.setSuggestion(null);
            else searchBox.setSuggestion("Поиск по файлам...");
            filterTracks();
        });
        addRenderableWidget(searchBox);

        int topBtnW = 60;
        int topBtnH = 16;
        int topY = MARGIN;
        addRenderableWidget(GuiWidgets.button(this.width - (topBtnW*2 + MARGIN + 6) - topBtnW - 6, topY, topBtnW, topBtnH,
                Component.literal("Обновить"), b -> {
            setStatus("Сканирование...");
            library.scanAsync().thenRun(() -> {
                Minecraft.getInstance().execute(() -> {
                    rootNode = library.getFolderTree();
                    if (currentNode != null) {
                        FolderNode found = findNodeByPath(rootNode, currentNode.getFolder());
                        if (found != null) currentNode = found;
                        else currentNode = rootNode;
                    } else currentNode = rootNode;
                    refreshCurrentFolder();
                    setStatus("Найдено " + library.getAllTracks().size() + " треков");
                });
            });
        }));
        addRenderableWidget(GuiWidgets.button(this.width - (topBtnW + MARGIN + 4), topY, topBtnW, topBtnH,
                Component.literal("Папка"), b -> {
            try {
                net.minecraft.Util.getPlatform().openUri(library.getMusicFolder().toURI());
            } catch (Exception e) {
                setStatus("Папка: " + library.getMusicFolder().getAbsolutePath());
            }
        }));

        int volX = searchW + MARGIN + 8;
        addRenderableWidget(GuiWidgets.button(volX, topY, 28, topBtnH, Component.literal("-"), b -> {
            float v = playerManager.getVolume() - 0.1f;
            playerManager.setVolume(v);
            setStatus(String.format("Громкость: %d%%", (int)(playerManager.getVolume()*100)));
        }));
        addRenderableWidget(GuiWidgets.button(volX + 30, topY, 28, topBtnH, Component.literal("+"), b -> {
            float v = playerManager.getVolume() + 0.1f;
            playerManager.setVolume(v);
            setStatus(String.format("Громкость: %d%%", (int)(playerManager.getVolume()*100)));
        }));

        int btnW = 62;
        int btnH = 18;
        int gap = 4;
        int totalW = btnW*4 + gap*3;
        int startX = (this.width - totalW)/2;
        if (startX < MARGIN) startX = MARGIN;

        addRenderableWidget(GuiWidgets.button(startX, bottomY, btnW, btnH, Component.literal("Вверх"), b -> {
            if (currentNode != null && currentNode.getParent() != null) {
                currentNode = currentNode.getParent();
                refreshCurrentFolder();
            }
        }));
        playPauseButton = addRenderableWidget(GuiWidgets.button(startX + btnW + gap, bottomY, btnW, btnH,
                Component.literal(playerManager.isPlaying() && !playerManager.isPaused() ? "Пауза" : "Играть"), b -> {
            if (playerManager.isPlaying()) {
                playerManager.togglePause();
                updatePlayPauseButton();
            } else {
                Track cur = playlistManager.getCurrentTrack();
                if (cur != null) playerManager.play(cur);
                else if (!filteredTracks.isEmpty()) playTrack(filteredTracks.get(0));
                else setStatus("Нет треков");
            }
        }));
        addRenderableWidget(GuiWidgets.button(startX + (btnW+gap)*2, bottomY, btnW, btnH, Component.literal("Далее >>"), b -> {
            Track next = playlistManager.next();
            if (next != null) playerManager.play(next);
            else setStatus("Конец очереди");
        }));
        Button modeBtn = addRenderableWidget(GuiWidgets.button(startX + (btnW+gap)*3, bottomY, btnW, btnH,
                Component.literal(playlistManager.getPlayMode().getDisplayName()), b -> {
            PlayMode nextMode = playlistManager.getPlayMode().next();
            playlistManager.setPlayMode(nextMode);
            b.setMessage(Component.literal(nextMode.getDisplayName()));
            setStatus("Режим: " + nextMode.getDisplayName());
        }));
        modeBtn.setMessage(Component.literal(playlistManager.getPlayMode().getDisplayName()));

        addRenderableWidget(GuiWidgets.button(MARGIN, bottomY, 60, btnH, Component.literal("Артисты"), b -> {
            Minecraft.getInstance().setScreen(new MusicPlayerScreen());
        }));

        setInitialFocus(searchBox);
        recalcLayout();
    }

    private FolderNode findNodeByPath(FolderNode root, java.io.File target) {
        if (root == null || target == null) return null;
        try {
            if (root.getFolder().getCanonicalPath().equals(target.getCanonicalPath())) return root;
        } catch (Exception e) {
            if (root.getFolder().getAbsolutePath().equals(target.getAbsolutePath())) return root;
        }
        for (FolderNode child : root.getChildren()) {
            FolderNode found = findNodeByPath(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private void recalcLayout() {
        colTop = 28;
        colBottom = this.height - 32;
        int totalWidth = this.width - MARGIN*2;
        colW = (totalWidth - midGap) / 2;
        if (colW < 80) colW = 80;
        leftX = MARGIN;
        rightX = leftX + colW + midGap;
        if (rightX + colW > this.width - MARGIN) {
            colW = (totalWidth - midGap) / 2;
            leftX = MARGIN;
            rightX = leftX + colW + midGap;
        }
    }

    private void refreshCurrentFolder() {
        if (currentNode == null) {
            currentSubfolders.clear();
            currentTracks.clear();
            filteredTracks.clear();
            return;
        }
        currentSubfolders.clear();
        currentSubfolders.addAll(currentNode.getChildren());
        currentSubfolders.sort((a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        currentTracks = new ArrayList<>(currentNode.getTracks());
        currentTracks.sort((a,b) -> a.getFile().getName().compareToIgnoreCase(b.getFile().getName()));
        filterTracks();
        folderScroll = 0;
        trackScroll = 0;
    }

    private void filterTracks() {
        String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        if (q.isEmpty()) filteredTracks = new ArrayList<>(currentTracks);
        else filteredTracks = currentTracks.stream()
                .filter(t -> t.getFile().getName().toLowerCase().contains(q) || t.getTitle().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    private void playTrack(Track track) {
        boolean recursive = hasShiftDown();
        List<Track> queue;
        if (recursive) {
            queue = currentNode.getAllTracksRecursive();
            queue.sort((a,b) -> a.getFile().getAbsolutePath().compareToIgnoreCase(b.getFile().getAbsolutePath()));
            setStatus("Рекурсивно из " + currentNode.getName() + " (" + queue.size() + ")");
        } else {
            queue = new ArrayList<>(filteredTracks);
        }
        int idx = queue.indexOf(track);
        if (idx < 0) idx = 0;
        playlistManager.setQueue(queue, idx);
        playerManager.play(track);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        recalcLayout();
        renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        String path = currentNode != null ? currentNode.getRelativePath(library.getMusicFolder()) : "/";
        if (path.isEmpty()) path = "/";
        String breadcrumb = "Папка: " + path + "  [" + currentNode.getTotalTrackCountRecursive() + " треков]";
        if (font.width(breadcrumb) > width - 200) breadcrumb = font.plainSubstrByWidth(breadcrumb, width - 210) + "...";
        g.drawString(font, breadcrumb, MARGIN, MARGIN + 20, 0xFFFFFF);
        Track now = playerManager.getNowPlaying();
        String nowText = now != null ?
                (playerManager.isPaused() ? "[Пауза] " : playerManager.isPlaying() ? "[Играет] " : "[Стоп] ") + now.getFile().getName() + " | " + now.getDurationFormatted()
                : "Нет воспроизведения.";
        int maxNow = width - 20;
        if (font.width(nowText) > maxNow) nowText = font.plainSubstrByWidth(nowText, maxNow - 10) + "...";
        g.drawString(font, nowText, MARGIN, MARGIN + 32, 0x55FF55);
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 4000) {
            g.drawString(font, statusMessage, MARGIN, height - 12, 0xFFFF55);
        }
        g.fill(leftX -1, colTop -1, leftX + colW -1, colBottom, 0xAA000000);
        g.fill(rightX -1, colTop -1, Math.min(rightX + colW -1, width - MARGIN), colBottom, 0xAA000000);
        String leftHeader = "Папки (" + currentSubfolders.size() + ")";
        if (currentNode != null && currentNode.getParent() != null) leftHeader += " [.. вверх]";
        g.drawString(font, leftHeader, leftX +2, colTop - 12 +2, 0xAAAAFF);
        g.drawString(font, "Треки (" + filteredTracks.size() + "/" + currentTracks.size() + ") [Shift+клик=рекурсивно]", rightX +2, colTop -12 +2, 0xAAFFAA);
        renderFolderList(g, leftX, colTop, colW, colBottom - colTop, mouseX, mouseY);
        renderTrackList(g, rightX, colTop, colW, colBottom - colTop, mouseX, mouseY);
        String volText = String.format("Громк %d%%", (int)(playerManager.getVolume()*100));
        g.drawString(font, volText, MARGIN + 220, MARGIN + 4, 0xAAAAAA);
    }

    private void renderFolderList(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int visible = Math.max(1, h / ROW_HEIGHT);
        folderScroll = Mth.clamp(folderScroll, 0, Math.max(0, currentSubfolders.size() + 1 - visible));
        int totalItems = currentSubfolders.size() + (currentNode != null && currentNode.getParent() != null ? 1 : 0);
        for (int i=0;i<visible;i++) {
            int idx = folderScroll + i;
            if (idx >= totalItems) break;
            int rowY = y + i*ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > colBottom) break;
            boolean isUp = (currentNode != null && currentNode.getParent() != null && idx == 0);
            String name;
            if (isUp) name = ".. (Вверх к " + currentNode.getParent().getName() + ")";
            else {
                int folderIdx = idx - (currentNode != null && currentNode.getParent() != null ? 1 : 0);
                FolderNode fn = currentSubfolders.get(folderIdx);
                name = "📁 " + fn.getName() + " [" + fn.getTotalTrackCountRecursive() + "]";
            }
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_HEIGHT;
            int bg = hovered ? 0xFF333333 : 0x00000000;
            if (bg!=0) g.fill(x, rowY, x + w -2, rowY + ROW_HEIGHT -1, bg);
            int col = isUp ? 0xFFAAAA : hovered ? 0xFFFFAA : 0xCCCCFF;
            String disp = name;
            if (font.width(disp) > w - 6) disp = font.plainSubstrByWidth(disp, w - 12) + "..";
            g.drawString(font, disp, x+2, rowY+2, col);
        }
    }

    private void renderTrackList(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int visible = Math.max(1, h / ROW_HEIGHT);
        int maxW = this.width - x - MARGIN - 2;
        if (maxW < w) w = maxW;
        trackScroll = Mth.clamp(trackScroll, 0, Math.max(0, filteredTracks.size() - visible));
        Track current = playerManager.getNowPlaying();
        for (int i=0;i<visible;i++) {
            int idx = trackScroll + i;
            if (idx >= filteredTracks.size()) break;
            Track t = filteredTracks.get(idx);
            int rowY = y + i*ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > colBottom) break;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_HEIGHT;
            boolean isPlaying = current != null && current.equals(t);
            int bg = isPlaying ? 0xFF336633 : hovered ? 0xFF333333 : (i%2==0?0xFF222222:0x00000000);
            g.fill(x, rowY, x + w -2, rowY + ROW_HEIGHT -1, bg);
            int col = isPlaying ? 0x55FF55 : hovered ? 0xFFFFAA : 0xEEEEEE;
            String line = String.format("%s [%s] %s", t.getFile().getName(), t.getFormat().getExt().toUpperCase(), t.getDurationFormatted());
            if (font.width(line) > w - 6) line = font.plainSubstrByWidth(line, w - 12) + "..";
            g.drawString(font, line, x+2, rowY+2, col);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftX && mouseX < leftX + colW && mouseY >= colTop && mouseY < colBottom) {
            int row = (int)((mouseY - colTop)/ROW_HEIGHT);
            int idx = folderScroll + row;
            int totalItems = currentSubfolders.size() + (currentNode != null && currentNode.getParent() != null ? 1 : 0);
            if (idx>=0 && idx < totalItems) {
                if (currentNode != null && currentNode.getParent() != null && idx == 0) {
                    currentNode = currentNode.getParent();
                    refreshCurrentFolder();
                } else {
                    int folderIdx = idx - (currentNode != null && currentNode.getParent() != null ? 1 : 0);
                    currentNode = currentSubfolders.get(folderIdx);
                    refreshCurrentFolder();
                }
            }
            return true;
        }
        if (mouseX >= rightX && mouseX < rightX + colW && mouseY >= colTop && mouseY < colBottom) {
            int row = (int)((mouseY - colTop)/ROW_HEIGHT);
            int idx = trackScroll + row;
            if (idx>=0 && idx < filteredTracks.size()) {
                playTrack(filteredTracks.get(idx));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (mouseY < colTop || mouseY >= colBottom) return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
        if (mouseX >= leftX && mouseX < leftX + colW) { folderScroll -= (int)delta; return true; }
        if (mouseX >= rightX && mouseX < rightX + colW) { trackScroll -= (int)delta; return true; }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    private void updatePlayPauseButton() {
        if (playPauseButton != null) playPauseButton.setMessage(Component.literal(playerManager.isPlaying() && !playerManager.isPaused() ? "Пауза" : "Играть"));
    }
    private void setStatus(String msg) { statusMessage = msg; statusMessageTime = System.currentTimeMillis(); }
    @Override public void onTrackStarted(Track track) { Minecraft.getInstance().execute(() -> { setStatus("Играет: " + track.getFile().getName()); updatePlayPauseButton(); }); }
    @Override public void onTrackFinished(Track track) { Minecraft.getInstance().execute(this::updatePlayPauseButton); }
    @Override public void onError(Track track, String error) { Minecraft.getInstance().execute(() -> setStatus("Ошибка: " + error)); }
    @Override public void onPaused(Track track) { Minecraft.getInstance().execute(() -> { setStatus("Пауза"); updatePlayPauseButton(); }); }
    @Override public void onResumed(Track track) { Minecraft.getInstance().execute(() -> { setStatus("Продолжение"); updatePlayPauseButton(); }); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { playerManager.setListener(null); super.onClose(); }
}
