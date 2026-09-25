package com.obninsk.custommusic.client;

import com.mojang.brigadier.CommandDispatcher;
import com.obninsk.custommusic.CustomMusicMod;
import com.obninsk.custommusic.gui.MusicBeeScreen;
import com.obninsk.custommusic.music.MusicLibraryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-side /custommusic commands.
 *
 * <p>NeoForge posts {@link RegisterClientCommandsEvent} on the GAME bus
 * (see net.neoforged.neoforge.client.ClientCommandHandler).
 */
@EventBusSubscriber(modid = CustomMusicMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("custommusic")
                .then(Commands.literal("open").executes(ctx -> {
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new MusicBeeScreen()));
                    return 1;
                }))
                .then(Commands.literal("folder").executes(ctx -> {
                    var folder = MusicLibraryManager.getInstance().getMusicFolder();
                    // 1.20.5+: sendSuccess takes a Supplier<Component>
                    ctx.getSource().sendSuccess(() -> Component.literal("Music folder: " + folder.getAbsolutePath()), false);
                    return 1;
                }))
                .then(Commands.literal("rescan").executes(ctx -> {
                    MusicLibraryManager.getInstance().scanAsync()
                            .thenRun(() -> ctx.getSource().sendSuccess(() -> Component.literal("Rescan complete"), false));
                    return 1;
                }))
        );
    }
}
