package com.obninsk.custommusic.gui;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * Исправленный GUI - без наложений, с адаптивной версткой, без setHint (1.19.2)
 */
public class MusicPlayerScreen extends Screen implements AudioPlayerManager.PlaybackListener {

    private MusicLibraryManager library;
    private PlaylistManager playlistManager;
    private AudioPlayerManager playerManager;

    private String selectedArtist = null;
    private String selectedAlbum = null;

    private final List<String> artistDisplay = new ArrayList<>();
    private final List<String> albumDisplay = new ArrayList<>();
    private List<Track> trackDisplay = new ArrayList<>();

    private int artistScroll = 0;
    private int albumScroll = 0;
    private int trackScroll = 0;
    private static final int ROW_HEIGHT = 12;
    private static final int HEADER_H = 12;
    private static final int MARGIN = 6;

    private EditBox searchBox;
    private Button playPauseButton;
    private String statusMessage = "";
    private long statusMessageTime = 0;

    private int colTop, colBottom, colHeight;
    private int leftX, midX, rightX;
    private int colW;

    public MusicPlayerScreen() {
        super(Component.literal("Custom Music Player"));
        this.library = MusicLibraryManager.getInstance();
        this.playlistManager = PlaylistManager.getInstance();
        this.playerManager = AudioPlayerManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        playerManager.setListener(this);
        refreshLists();
        int bottomY = this.height - 24;

        int searchW = Math.min(160, this.width / 3 - 10);
        searchBox = new EditBox(this.font, MARGIN, MARGIN, searchW, 16, Component.literal("Search"));
        searchBox.setValue("");
        searchBox.setSuggestion("Поиск...");
        searchBox.setResponder(s -> {
            if (s != null && !s.isEmpty()) searchBox.setSuggestion(null);
            else searchBox.setSuggestion("Поиск...");
            filterTracks();
        });
        addRenderableWidget(searchBox);

        int topBtnW = 60;
        int topBtnH = 16;
        int topY = MARGIN;
        addRenderableWidget(new Button(this.width - (topBtnW*2 + MARGIN + 4) - topBtnW - 4, topY, topBtnW, topBtnH,
                Component.literal("Обновить"), b -> {
            setStatus("Сканирование...");
            library.scanAsync().thenRun(() -> {
                Minecraft.getInstance().execute(() -> {
                    refreshLists();
                    setStatus("Найдено " + library.getAllTracks().size() + " треков");
                });
            });
        }));
        addRenderableWidget(new Button(this.width - (topBtnW + MARGIN + 2), topY, topBtnW, topBtnH,
                Component.literal("Папка"), b -> {
            try {
                net.minecraft.Util.getPlatform().openUri(library.getMusicFolder().toURI());
            } catch (Exception e) {
                setStatus("Папка: " + library.getMusicFolder().getAbsolutePath());
            }
        }));

        int volX = searchW + MARGIN + 8;
        addRenderableWidget(new Button(volX, topY, 28, topBtnH, Component.literal("-"), b -> {
            float v = playerManager.getVolume() - 0.1f;
            playerManager.setVolume(v);
            setStatus(String.format("Громкость: %d%%", (int)(playerManager.getVolume()*100)));
        }));
        addRenderableWidget(new Button(volX + 30, topY, 28, topBtnH, Component.literal("+"), b -> {
            float v = playerManager.getVolume() + 0.1f;
            playerManager.setVolume(v);
            setStatus(String.format("Громкость: %d%%", (int)(playerManager.getVolume()*100)));
        }));
        // Кнопка переключения в режим папок
        addRenderableWidget(new Button(volX + 62, topY, 50, topBtnH, Component.literal("Папки"), b -> {
            Minecraft.getInstance().setScreen(new FolderBrowserScreen());
        }));

        int btnW = 62;
        int btnH = 18;
        int gap = 4;
        int totalW = btnW*4 + gap*3;
        int startX = (this.width - totalW)/2;
        if (startX < MARGIN) startX = MARGIN;

        addRenderableWidget(new Button(startX, bottomY, btnW, btnH, Component.literal("<< Назад"), b -> {
            Track prev = playlistManager.previous();
            if (prev != null) playerManager.play(prev);
            else setStatus("Нет предыдущего");
        }));
        playPauseButton = addRenderableWidget(new Button(startX + btnW + gap, bottomY, btnW, btnH,
                Component.literal(playerManager.isPlaying() && !playerManager.isPaused() ? "Пауза" : "Играть"), b -> {
            if (playerManager.isPlaying()) {
                playerManager.togglePause();
                updatePlayPauseButton();
            } else {
                Track cur = playlistManager.getCurrentTrack();
                if (cur != null) playerManager.play(cur);
                else if (!trackDisplay.isEmpty()) playTrack(trackDisplay.get(0));
                else setStatus("Нет треков");
            }
        }));
        addRenderableWidget(new Button(startX + (btnW+gap)*2, bottomY, btnW, btnH, Component.literal("Далее >>"), b -> {
            Track next = playlistManager.next();
            if (next != null) playerManager.play(next);
            else setStatus("Конец очереди");
        }));
        Button modeBtn = addRenderableWidget(new Button(startX + (btnW+gap)*3, bottomY, btnW, btnH,
                Component.literal(playlistManager.getPlayMode().getDisplayName()), b -> {
            PlayMode nextMode = playlistManager.getPlayMode().next();
            playlistManager.setPlayMode(nextMode);
            b.setMessage(Component.literal(nextMode.getDisplayName()));
            setStatus("Режим: " + nextMode.getDisplayName());
        }));
        modeBtn.setMessage(Component.literal(playlistManager.getPlayMode().getDisplayName()));

        setInitialFocus(searchBox);
        recalcLayout();
    }

    private void recalcLayout() {
        colTop = 28;
        colBottom = this.height - 32;
        colHeight = colBottom - colTop;
        int totalWidth = this.width - MARGIN*2;
        colW = (totalWidth - MARGIN*2) / 3;
        if (colW < 80) colW = 80;
        leftX = MARGIN;
        midX = leftX + colW + MARGIN;
        rightX = midX + colW + MARGIN;
        if (rightX + colW > this.width - MARGIN) {
            colW = (totalWidth - MARGIN*2) / 3;
            leftX = MARGIN;
            midX = leftX + colW + MARGIN;
            rightX = midX + colW + MARGIN;
        }
    }

    private void refreshLists() {
        artistDisplay.clear();
        artistDisplay.add("[Все исполнители]");
        artistDisplay.addAll(library.getArtistNamesSorted());
        albumDisplay.clear();
        albumDisplay.add("[Все альбомы]");
        if (selectedArtist != null) {
            for (var album : library.getAlbumsForArtist(selectedArtist)) {
                albumDisplay.add(album.getName());
            }
        } else {
            albumDisplay.addAll(library.getAlbumNames());
        }
        filterTracks();
    }

    private void filterTracks() {
        List<Track> base;
        if (selectedArtist != null && selectedAlbum != null) {
            base = library.getTracksForAlbum(selectedArtist, selectedAlbum);
        } else if (selectedArtist != null) {
            base = library.getTracksForArtist(selectedArtist);
        } else if (selectedAlbum != null) {
            base = library.getAllTracks().stream()
                    .filter(t -> t.getAlbum().equals(selectedAlbum))
                    .collect(Collectors.toList());
        } else {
            base = library.getAllTracks();
        }
        String query = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        if (!query.isEmpty()) {
            base = base.stream().filter(t ->
                    t.getTitle().toLowerCase().contains(query) ||
                    t.getArtist().toLowerCase().contains(query) ||
                    t.getAlbum().toLowerCase().contains(query) ||
                    t.getFile().getName().toLowerCase().contains(query)
            ).collect(Collectors.toList());
        }
        trackDisplay = base;
        trackScroll = 0;
    }

    private void playTrack(Track track) {
        playlistManager.setQueue(trackDisplay, trackDisplay.indexOf(track));
        playerManager.play(track);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        recalcLayout();
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        String title = "Custom Music Player";
        drawCenteredString(poseStack, font, title, width/2, MARGIN + 20, 0xFFFFFF);
        Track now = playerManager.getNowPlaying();
        String nowText;
        if (now != null) {
            String state = playerManager.isPaused() ? "[Пауза] " : playerManager.isPlaying() ? "[Играет] " : "[Стоп] ";
            nowText = state + now.getArtist() + " - " + now.getTitle() + " | " + now.getAlbum() + " | " + now.getDurationFormatted() + " " + now.getFormat();
        } else {
            nowText = "Нет воспроизведения. Выбери трек справа.";
        }
        int maxNowWidth = this.width - 20;
        if (font.width(nowText) > maxNowWidth) {
            nowText = font.plainSubstrByWidth(nowText, maxNowWidth - 10) + "...";
        }
        drawString(poseStack, font, nowText, MARGIN, MARGIN + 36, 0x55FF55);
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 4000) {
            drawString(poseStack, font, statusMessage, MARGIN, height - 12, 0xFFFF55);
        }
        String volText = String.format("Громк. %d%%", (int)(playerManager.getVolume()*100));
        drawString(poseStack, font, volText, MARGIN + 220, MARGIN + 4, 0xAAAAAA);
        fill(poseStack, leftX -1, colTop -1, leftX + colW -1, colBottom, 0xAA000000);
        fill(poseStack, midX -1, colTop -1, midX + colW -1, colBottom, 0xAA000000);
        fill(poseStack, rightX -1, colTop -1, Math.min(rightX + colW -1, this.width - MARGIN), colBottom, 0xAA000000);
        drawString(poseStack, font, "Исполнители (" + (artistDisplay.size()-1) + ")", leftX +2, colTop - HEADER_H +2, 0xAAAAFF);
        drawString(poseStack, font, "Альбомы (" + (albumDisplay.size()-1) + ")", midX +2, colTop - HEADER_H +2, 0xFFAAAA);
        drawString(poseStack, font, "Треки (" + trackDisplay.size() + ")", rightX +2, colTop - HEADER_H +2, 0xAAFFAA);
        renderArtistList(poseStack, leftX, colTop, colW, colHeight, mouseX, mouseY);
        renderAlbumList(poseStack, midX, colTop, colW, colHeight, mouseX, mouseY);
        renderTrackList(poseStack, rightX, colTop, colW, colHeight, mouseX, mouseY);
    }

    private void renderArtistList(PoseStack ps, int x, int y, int w, int h, int mx, int my) {
        int visible = Math.max(1, h / ROW_HEIGHT);
        artistScroll = Mth.clamp(artistScroll, 0, Math.max(0, artistDisplay.size() - visible));
        for (int i=0;i<visible;i++) {
            int idx = artistScroll + i;
            if (idx >= artistDisplay.size()) break;
            String name = artistDisplay.get(idx);
            int rowY = y + i*ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > colBottom) break;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_HEIGHT;
            boolean selected = (idx==0 && selectedArtist==null) || (selectedArtist!=null && selectedArtist.equals(name));
            int bg = selected ? 0xFF333388 : hovered ? 0xFF333333 : 0x00000000;
            if (bg!=0) fill(ps, x, rowY, x + w -2, rowY + ROW_HEIGHT -1, bg);
            int col = selected ? 0xFFFFFF : hovered ? 0xFFFFAA : 0xCCCCCC;
            String disp = name;
            if (font.width(disp) > w - 6) disp = font.plainSubstrByWidth(disp, w - 12) + "..";
            drawString(ps, font, disp, x+2, rowY+2, col);
        }
    }

    private void renderAlbumList(PoseStack ps, int x, int y, int w, int h, int mx, int my) {
        int visible = Math.max(1, h / ROW_HEIGHT);
        albumScroll = Mth.clamp(albumScroll, 0, Math.max(0, albumDisplay.size() - visible));
        for (int i=0;i<visible;i++) {
            int idx = albumScroll + i;
            if (idx >= albumDisplay.size()) break;
            String name = albumDisplay.get(idx);
            int rowY = y + i*ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > colBottom) break;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_HEIGHT;
            boolean selected = (idx==0 && selectedAlbum==null) || (selectedAlbum!=null && selectedAlbum.equals(name));
            int bg = selected ? 0xFF883333 : hovered ? 0xFF333333 : 0x00000000;
            if (bg!=0) fill(ps, x, rowY, x + w -2, rowY + ROW_HEIGHT -1, bg);
            int col = selected ? 0xFFFFFF : hovered ? 0xFFFFAA : 0xCCCCCC;
            String disp = name;
            if (font.width(disp) > w - 6) disp = font.plainSubstrByWidth(disp, w - 12) + "..";
            drawString(ps, font, disp, x+2, rowY+2, col);
        }
    }

    private void renderTrackList(PoseStack ps, int x, int y, int w, int h, int mx, int my) {
        int visible = Math.max(1, h / ROW_HEIGHT);
        int maxW = this.width - x - MARGIN - 2;
        if (maxW < w) w = maxW;
        trackScroll = Mth.clamp(trackScroll, 0, Math.max(0, trackDisplay.size() - visible));
        Track current = playerManager.getNowPlaying();
        for (int i=0;i<visible;i++) {
            int idx = trackScroll + i;
            if (idx >= trackDisplay.size()) break;
            Track t = trackDisplay.get(idx);
            int rowY = y + i*ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > colBottom) break;
            boolean hovered = mx >= x && mx < x + w && my >= rowY && my < rowY + ROW_HEIGHT;
            boolean isPlaying = current != null && current.equals(t);
            int bg = isPlaying ? 0xFF336633 : hovered ? 0xFF333333 : (i%2==0?0xFF222222:0x00000000);
            fill(ps, x, rowY, x + w -2, rowY + ROW_HEIGHT -1, bg);
            int col = isPlaying ? 0x55FF55 : hovered ? 0xFFFFAA : 0xEEEEEE;
            String line = String.format("%s - %s [%s]", t.getArtist(), t.getTitle(), t.getFormat().getExt().toUpperCase());
            if (font.width(line) > w - 6) line = font.plainSubstrByWidth(line, w - 12) + "..";
            drawString(ps, font, line, x+2, rowY+2, col);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftX && mouseX < leftX + colW && mouseY >= colTop && mouseY < colBottom) {
            int row = (int)((mouseY - colTop)/ROW_HEIGHT);
            int idx = artistScroll + row;
            if (idx>=0 && idx < artistDisplay.size()) {
                String chosen = artistDisplay.get(idx);
                selectedArtist = (idx==0)? null : chosen;
                selectedAlbum = null;
                refreshLists();
                setStatus("Исполнитель: " + (selectedArtist==null?"Все":selectedArtist));
            }
            return true;
        }
        if (mouseX >= midX && mouseX < midX + colW && mouseY >= colTop && mouseY < colBottom) {
            int row = (int)((mouseY - colTop)/ROW_HEIGHT);
            int idx = albumScroll + row;
            if (idx>=0 && idx < albumDisplay.size()) {
                String chosen = albumDisplay.get(idx);
                selectedAlbum = (idx==0)? null : chosen;
                filterTracks();
                setStatus("Альбом: " + (selectedAlbum==null?"Все":selectedAlbum));
            }
            return true;
        }
        if (mouseX >= rightX && mouseX < rightX + colW && mouseY >= colTop && mouseY < colBottom) {
            int row = (int)((mouseY - colTop)/ROW_HEIGHT);
            int idx = trackScroll + row;
            if (idx>=0 && idx < trackDisplay.size()) {
                Track t = trackDisplay.get(idx);
                if (button==0) playTrack(t);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY < colTop || mouseY >= colBottom) return super.mouseScrolled(mouseX, mouseY, delta);
        if (mouseX >= leftX && mouseX < leftX + colW) {
            artistScroll -= (int)delta;
            return true;
        }
        if (mouseX >= midX && mouseX < midX + colW) {
            albumScroll -= (int)delta;
            return true;
        }
        if (mouseX >= rightX && mouseX < rightX + colW) {
            trackScroll -= (int)delta;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updatePlayPauseButton() {
        if (playPauseButton != null) {
            playPauseButton.setMessage(Component.literal(playerManager.isPlaying() && !playerManager.isPaused() ? "Пауза" : "Играть"));
        }
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusMessageTime = System.currentTimeMillis();
    }

    @Override public void onTrackStarted(Track track) {
        Minecraft.getInstance().execute(() -> { setStatus("Играет: " + track.getDisplayName()); updatePlayPauseButton(); });
    }
    @Override public void onTrackFinished(Track track) {
        Minecraft.getInstance().execute(this::updatePlayPauseButton);
    }
    @Override public void onError(Track track, String error) {
        Minecraft.getInstance().execute(() -> setStatus("Ошибка: " + error));
    }
    @Override public void onPaused(Track track) {
        Minecraft.getInstance().execute(() -> { setStatus("Пауза"); updatePlayPauseButton(); });
    }
    @Override public void onResumed(Track track) {
        Minecraft.getInstance().execute(() -> { setStatus("Продолжение"); updatePlayPauseButton(); });
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { playerManager.setListener(null); super.onClose(); }
}
