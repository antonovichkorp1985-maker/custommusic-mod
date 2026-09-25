package com.obninsk.custommusic;

import com.obninsk.custommusic.client.ClientCommands;
import com.obninsk.custommusic.client.ClientEvents;
import com.obninsk.custommusic.config.ModConfig;
import com.obninsk.custommusic.music.MusicLibraryManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("custommusic")
public class CustomMusicMod {
    public static final String MODID = "custommusic";
    public static final Logger LOGGER = LogManager.getLogger("CustomMusic");

    public CustomMusicMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(ClientEvents::onRegisterKeyMappings);

        ModLoadingContext.get().registerConfig(Type.CLIENT, ModConfig.CLIENT_SPEC);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(ClientEvents.ForgeEvents.class);
            MinecraftForge.EVENT_BUS.register(ClientCommands.class);
        });

        LOGGER.info("CustomMusic mod initialized");
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("CustomMusic common setup");
        event.enqueueWork(() -> {
            MusicLibraryManager.getInstance().init();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("CustomMusic client setup");
    }
}
