package com.obninsk.custommusic.client;

import com.mojang.brigadier.CommandDispatcher;
import com.obninsk.custommusic.gui.MusicBeeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("custommusic")
                .then(Commands.literal("open").executes(ctx -> {
                    Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new MusicBeeScreen()));
                    return 1;
                }))
                .then(Commands.literal("folder").executes(ctx -> {
                    var folder = com.obninsk.custommusic.music.MusicLibraryManager.getInstance().getMusicFolder();
                    ctx.getSource().sendSuccess(Component.literal("Music folder: " + folder.getAbsolutePath()), false);
                    return 1;
                }))
                .then(Commands.literal("rescan").executes(ctx -> {
                    com.obninsk.custommusic.music.MusicLibraryManager.getInstance().scanAsync()
                            .thenRun(() -> ctx.getSource().sendSuccess(Component.literal("Rescan complete"), false));
                    return 1;
                }))
        );
    }
}
