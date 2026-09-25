package com.obninsk.custommusic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config for CustomMusic (config/custommusic-client.toml).
 *
 * <p>1.19.2 Forge used {@code ForgeConfigSpec}; NeoForge renamed it to
 * {@code net.neoforged.neoforge.common.ModConfigSpec} - the builder API is the same.
 */
public class ModConfig {
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    public static class Client {
        public final ModConfigSpec.ConfigValue<String> musicFolder;
        public final ModConfigSpec.DoubleValue defaultVolume;
        public final ModConfigSpec.BooleanValue pauseVanillaMusic;
        public final ModConfigSpec.BooleanValue autoScanOnStartup;
        public final ModConfigSpec.ConfigValue<String> defaultPlayMode;
        public final ModConfigSpec.BooleanValue enableDSF;
        public final ModConfigSpec.ConfigValue<String> defaultViewMode;

        public Client(ModConfigSpec.Builder builder) {
            builder.push("general");
            musicFolder = builder
                    .comment("Папка с музыкой относительно директории игры. Если пусто - используется ./custommusic/")
                    .define("musicFolder", "custommusic");
            defaultVolume = builder
                    .comment("Громкость по умолчанию 0.0 - 1.0")
                    .defineInRange("defaultVolume", 0.7, 0.0, 1.0);
            pauseVanillaMusic = builder
                    .comment("Приглушать ванильную музыку Minecraft когда играет кастомная")
                    .define("pauseVanillaMusic", true);
            autoScanOnStartup = builder
                    .comment("Автоматически сканировать папку при запуске")
                    .define("autoScanOnStartup", true);
            defaultPlayMode = builder
                    .comment("Режим по умолчанию: SEQUENTIAL, SHUFFLE, REPEAT_ALL, REPEAT_ONE")
                    .define("defaultPlayMode", "SEQUENTIAL");
            enableDSF = builder
                    .comment("Включить экспериментальную поддержку DSF (требует DoP или конвертации, может быть тяжелым)")
                    .define("enableDSF", false);
            defaultViewMode = builder
                    .comment("Вид по умолчанию при открытии по M: FOLDERS или ARTISTS")
                    .define("defaultViewMode", "FOLDERS");
            builder.pop();
        }
    }

    // ---------------------------------------------------------------------------------
    // Безопасные геттеры.
    //
    // CLIENT-конфиг загружается только на клиенте: на выделенном сервере
    // ConfigValue.get() бросает IllegalStateException ("Cannot get config value before
    // config is loaded"). Раньше это роняло загрузку мода при обращении к конфигу
    // из FMLCommonSetupEvent. Теперь везде используются эти геттеры с дефолтами.
    // ---------------------------------------------------------------------------------
    public static String musicFolder()        { return safe(CLIENT.musicFolder, "custommusic"); }
    public static double defaultVolume()      { return safe(CLIENT.defaultVolume, 0.7d); }
    public static boolean pauseVanillaMusic() { return safe(CLIENT.pauseVanillaMusic, true); }
    public static boolean autoScanOnStartup() { return safe(CLIENT.autoScanOnStartup, true); }
    public static String defaultPlayMode()    { return safe(CLIENT.defaultPlayMode, "SEQUENTIAL"); }
    public static boolean enableDSF()         { return safe(CLIENT.enableDSF, false); }
    public static String defaultViewMode()    { return safe(CLIENT.defaultViewMode, "FOLDERS"); }

    private static <T> T safe(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T v = value.get();
            return v != null ? v : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
