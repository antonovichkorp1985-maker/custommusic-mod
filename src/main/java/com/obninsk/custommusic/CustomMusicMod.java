package com.obninsk.custommusic;

import com.obninsk.custommusic.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CustomMusic for Minecraft 1.21.1 / NeoForge.
 *
 * <p>The mod is client-side only: every client specific listener lives in a class
 * annotated with {@code @EventBusSubscriber(value = Dist.CLIENT)}, so nothing client
 * related is touched from this constructor (the mod can also be present on a
 * dedicated server without crashing it).
 */
@Mod(CustomMusicMod.MODID)
public class CustomMusicMod {
    public static final String MODID = "custommusic";
    public static final Logger LOGGER = LogManager.getLogger("CustomMusic");

    // FML injects these parameters automatically.
    public CustomMusicMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);

        // NeoForge config (creates config/custommusic-client.toml)
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ModConfig.CLIENT_SPEC);

        LOGGER.info("CustomMusic mod initialized (NeoForge / Minecraft 1.21.1)");
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("CustomMusic common setup");
        // Библиотеку сканируем только на клиенте (см. ClientEvents#onClientSetup):
        // на выделенном сервере CLIENT-конфиг не загружен, да и музыка там не нужна.
    }
}
