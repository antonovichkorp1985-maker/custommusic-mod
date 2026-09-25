package com.obninsk.custommusic.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    public static class Client {
        public final ForgeConfigSpec.ConfigValue<String> musicFolder;
        public final ForgeConfigSpec.DoubleValue defaultVolume;
        public final ForgeConfigSpec.BooleanValue pauseVanillaMusic;
        public final ForgeConfigSpec.BooleanValue autoScanOnStartup;
        public final ForgeConfigSpec.ConfigValue<String> defaultPlayMode;
        public final ForgeConfigSpec.BooleanValue enableDSF;
        public final ForgeConfigSpec.ConfigValue<String> defaultViewMode;

        public Client(ForgeConfigSpec.Builder builder) {
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
    // Безопасные геттеры: CLIENT-конфиг грузится только на клиенте, на выделенном сервере
    // ConfigValue.get() бросает IllegalStateException. Те же геттеры, что в NeoForge-версии.
    // ---------------------------------------------------------------------------------
    public static String musicFolder()        { return safe(CLIENT.musicFolder, "custommusic"); }
    public static double defaultVolume()      { return safe(CLIENT.defaultVolume, 0.7d); }
    public static boolean pauseVanillaMusic() { return safe(CLIENT.pauseVanillaMusic, true); }
    public static boolean autoScanOnStartup() { return safe(CLIENT.autoScanOnStartup, true); }
    public static String defaultPlayMode()    { return safe(CLIENT.defaultPlayMode, "SEQUENTIAL"); }
    public static boolean enableDSF()         { return safe(CLIENT.enableDSF, false); }
    public static String defaultViewMode()    { return safe(CLIENT.defaultViewMode, "FOLDERS"); }

    private static <T> T safe(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T v = value.get();
            return v != null ? v : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
