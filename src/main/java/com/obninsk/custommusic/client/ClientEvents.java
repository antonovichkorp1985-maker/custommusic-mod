package com.obninsk.custommusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.obninsk.custommusic.CustomMusicMod;
import com.obninsk.custommusic.gui.MusicBeeScreen;
import com.obninsk.custommusic.music.AudioPlayerManager;
import com.obninsk.custommusic.music.MusicLibraryManager;
import com.obninsk.custommusic.music.PlaylistManager;
import com.obninsk.custommusic.music.Track;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CustomMusicMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientEvents {

    public static KeyMapping OPEN_PLAYER_KEY;
    public static KeyMapping OPEN_ARTISTS_KEY;
    public static KeyMapping NEXT_TRACK_KEY;
    public static KeyMapping PREV_TRACK_KEY;
    public static KeyMapping PLAY_PAUSE_KEY;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CustomMusicMod.LOGGER.info("CustomMusic client setup");
        // создаёт папку custommusic/ и запускает фоновое сканирование библиотеки
        event.enqueueWork(() -> MusicLibraryManager.getInstance().init());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_PLAYER_KEY = new KeyMapping(
                "key.custommusic.open_player",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "key.categories.custommusic"
        );
        OPEN_ARTISTS_KEY = new KeyMapping(
                "key.custommusic.open_artists",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "key.categories.custommusic"
        );
        NEXT_TRACK_KEY = new KeyMapping(
                "key.custommusic.next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "key.categories.custommusic"
        );
        PREV_TRACK_KEY = new KeyMapping(
                "key.custommusic.prev",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "key.categories.custommusic"
        );
        PLAY_PAUSE_KEY = new KeyMapping(
                "key.custommusic.play_pause",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "key.categories.custommusic"
        );
        event.register(OPEN_PLAYER_KEY);
        event.register(OPEN_ARTISTS_KEY);
        event.register(NEXT_TRACK_KEY);
        event.register(PREV_TRACK_KEY);
        event.register(PLAY_PAUSE_KEY);
        CustomMusicMod.LOGGER.info("Keybindings registered: M (folders), O (artists), N, B, P");
    }

    /**
     * Game bus events (NeoForge.EVENT_BUS).
     */
    @EventBusSubscriber(modid = CustomMusicMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class GameEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;

            if (OPEN_PLAYER_KEY != null && OPEN_PLAYER_KEY.consumeClick()) {
                mc.setScreen(new MusicBeeScreen());
            }
            if (OPEN_ARTISTS_KEY != null && OPEN_ARTISTS_KEY.consumeClick()) {
                mc.setScreen(new MusicBeeScreen());
            }
            if (NEXT_TRACK_KEY != null && NEXT_TRACK_KEY.consumeClick()) {
                PlaylistManager pm = PlaylistManager.getInstance();
                Track next = pm.next();
                if (next != null) AudioPlayerManager.getInstance().play(next);
            }
            if (PREV_TRACK_KEY != null && PREV_TRACK_KEY.consumeClick()) {
                PlaylistManager pm = PlaylistManager.getInstance();
                Track prev = pm.previous();
                if (prev != null) AudioPlayerManager.getInstance().play(prev);
            }
            if (PLAY_PAUSE_KEY != null && PLAY_PAUSE_KEY.consumeClick()) {
                AudioPlayerManager.getInstance().togglePause();
            }
        }
    }
}
