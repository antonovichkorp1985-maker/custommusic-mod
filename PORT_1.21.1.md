# Порт CustomMusic: 1.19.2 Forge → 1.21.1 NeoForge

Здесь записано, что именно пришлось поменять. Полезно, если будешь портировать дальше
(например на 1.21.4+ или обратно на Forge).

## 1. Сборка

| | 1.19.2 Forge | 1.21.1 NeoForge |
|---|---|---|
| Плагин | `net.minecraftforge.gradle` 6.0 + ForgeGradle | `net.neoforged.moddev` 2.0.91 (ModDevGradle) |
| Gradle | 8.5 | 8.14.2 |
| Java | 17 | **21** |
| Версия загрузчика | `forge:1.19.2-43.4.0` | `neoforge:21.1.176` |
| Метаданные мода | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` (генерируется из `src/main/templates`) |
| Реобфускация | `reobfJar` обязательна | не нужна (NeoForge работает на официальных маппингах Mojang) |
| Итоговый jar | `custommusic-1.0.0-all.jar` | `custommusic-1.21.1-1.0.1-all.jar` |
| Shadow | `com.github.johnrengelman.shadow` 8.1.1 | `com.gradleup.shadow` 8.3.9 |

`pack.mcmeta`: `pack_format` 9 → **34** (resource pack 1.21.1), data pack 48.

## 2. Пакеты и классы NeoForge

| 1.19.2 Forge | 1.21.1 NeoForge |
|---|---|
| `net.minecraftforge.api.distmarker.Dist` | `net.neoforged.api.distmarker.Dist` |
| `net.minecraftforge.eventbus.api.IEventBus` / `SubscribeEvent` | `net.neoforged.bus.api.IEventBus` / `SubscribeEvent` |
| `net.minecraftforge.fml.common.Mod` | `net.neoforged.fml.common.Mod` |
| `@Mod.EventBusSubscriber` | `@EventBusSubscriber` (отдельная аннотация, `net.neoforged.fml.common.EventBusSubscriber`) |
| `Bus.FORGE` | `Bus.GAME` |
| `net.minecraftforge.common.MinecraftForge.EVENT_BUS` | `net.neoforged.neoforge.common.NeoForge.EVENT_BUS` |
| `net.minecraftforge.common.ForgeConfigSpec` | `net.neoforged.neoforge.common.ModConfigSpec` |
| `ModLoadingContext.get().registerConfig(...)` | `ModContainer.registerConfig(...)` (контейнер приходит в конструктор мода) |
| `FMLJavaModLoadingContext.get().getModEventBus()` | параметр `IEventBus modEventBus` в конструкторе мода |
| `net.minecraftforge.fml.loading.FMLPaths` | `net.neoforged.fml.loading.FMLPaths` |
| `net.minecraftforge.client.event.*` | `net.neoforged.neoforge.client.event.*` |
| `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` | не нужно: классы с `@EventBusSubscriber(value = Dist.CLIENT)` сами не грузятся на сервере |

Конструктор мода теперь:

```java
@Mod(CustomMusicMod.MODID)
public class CustomMusicMod {
    public CustomMusicMod(IEventBus modEventBus, ModContainer modContainer) { ... }
}
```

## 3. Какие события на какой шине

Проверено по исходникам NeoForge 21.1.176:

- `RegisterKeyMappingsEvent` — **MOD** bus (`ModLoader.postEvent` в `ClientHooks`)
- `RegisterClientCommandsEvent` — **GAME** bus (`NeoForge.EVENT_BUS.post` в `ClientCommandHandler`)
- `InputEvent.Key` — **GAME** bus (`NeoForge.EVENT_BUS.post` в `ClientHooks`)
- `FMLCommonSetupEvent`, `FMLClientSetupEvent` — **MOD** bus

Поэтому `ClientEvents` (кейбинды) висит на MOD-шине, а вложенный класс
`ClientEvents.GameEvents` (нажатия клавиш) и `ClientCommands` — на GAME-шине.

## 4. GUI / рендер (самая большая часть работы)

| 1.19.2 | 1.21.1 |
|---|---|
| `render(PoseStack poseStack, int, int, float)` | `render(GuiGraphics guiGraphics, int, int, float)` |
| `drawString(poseStack, font, text, x, y, color)` (статик из `GuiComponent`) | `guiGraphics.drawString(font, text, x, y, color)` |
| `drawCenteredString(poseStack, font, ...)` | `guiGraphics.drawCenteredString(font, ...)` |
| `fill(poseStack, x1, y1, x2, y2, color)` | `guiGraphics.fill(x1, y1, x2, y2, color)` |
| `renderBackground(poseStack)` | `renderBackground(guiGraphics, mouseX, mouseY, partialTick)` |
| `new Button(x, y, w, h, text, onPress)` | `Button.builder(text, onPress).bounds(x, y, w, h).build()` |
| `mouseScrolled(double x, double y, double delta)` | `mouseScrolled(double x, double y, double scrollX, double scrollY)` |

Чтобы не переписывать 30+ кнопок вручную, добавлен крошечный хелпер
`gui/GuiWidgets.button(x, y, w, h, text, onPress)` — он просто вызывает `Button.builder(...)`.

Для совместимости со старым кодом переменная `GuiGraphics` в экранах называется `g`
(раньше это был `PoseStack ps`).

## 5. Команды

`CommandSourceStack.sendSuccess(Component, boolean)` → `sendSuccess(Supplier<Component>, boolean)`:

```java
ctx.getSource().sendSuccess(() -> Component.literal("Music folder: " + path), false);
```

`Minecraft.getInstance().tell(Runnable)` → `Minecraft.getInstance().execute(Runnable)`.

## 6. Что НЕ менялось

Вся «музыкальная» часть мода не зависит от Minecraft API и перенеслась без правок:

- `music/AudioPlayerManager`, `music/MusicLibraryManager`, `music/PlaylistManager`,
  `music/Track`, `music/Album`, `music/Artist`, `music/FolderNode`, `music/PlayMode`,
  `music/AudioFormatType`, `music/MetadataParser`, `util/DSFParser`
- аудио-библиотеки (mp3spi, jlayer, jorbis/vorbisspi, jflac, jse-spi-flac, jaudiotagger)
  по-прежнему бандлятся в fat jar через Shadow (`mergeServiceFiles()` важен для Java Sound SPI)

Единственная правка в этой части — импорт `FMLPaths` в `MusicLibraryManager`
и `RandomSource` в `PlaylistManager` (пакет тот же).

## 7. Попутно починен OGG Vorbis (был сломан и в 1.19.2)

При проверке декодирования на Java 21 выяснилось:

```
sample-3s.mp3 -> decoded PCM: 576000 bytes (~3.27 s)      OK
sample-3s.wav -> decoded PCM: 563712 bytes (~3.20 s)      OK
sample1.flac  -> decoded PCM: 21537304 bytes (~122.09 s)  OK
example.ogg   -> decoded PCM: 0 bytes                     ПУСТО
```

`com.googlecode.soundlibs:vorbisspi` (javazoom, последний релиз 2012 г.) формат определяет
(`VORBISENC 44100 Hz stereo`), но из сконвертированного потока читается 0 байт, т.е. трек
молча «не играет». Воспроизводится и на чистых библиотеках без Minecraft/Shadow — значит
дело не в порте, а в самой библиотеке на современной JVM.

Решение: `music/OggVorbisDecoder.java` — собственный потоковый декодер поверх того же
JOrbis (`com.jcraft.jorbis`), который уже лежит в fat jar:

```
SyncState -> StreamState -> Packet -> Block -> DspState -> PCM 16 bit LE
```

- декодирование ленивое (по страницам Ogg), файл не грузится в память целиком;
- `skip()` реализован как read-and-discard, поэтому перемотка работает как раньше;
- если файл не Vorbis (Ogg Opus / Ogg FLAC) — декодер бросает исключение, и плеер
  откатывается на обычный SPI-путь;
- в `AudioPlayerManager` добавлены `openAudioStream(File)` и `toPcm(in, target)`;
  `AudioSystem.getAudioInputStream(file)` заменён на `openAudioStream(file)`,
  `AudioSystem.getAudioInputStream(candidate, in)` — на `toPcm(in, candidate)`
  (без лишней конвертации, если форматы уже совпадают).

После фикса:

```
example.ogg   -> decoded PCM: 21537304 bytes (~122.09 s), RMS=10740   OK
```

Тот же патч применён к проекту для 1.19.2 Forge (`custommusic-mod-musicbee`), его нужно
пересобрать: `.\gradlew shadowJar`.

## 8. Критично: нельзя бандлить `com.jcraft.jorbis` (краш запуска игры)

Первая собранная версия **не запускалась** в реальном клиенте:

```
[ERROR] [ModuleLayerHandler/]: Error while resolving modules.
java.lang.module.ResolutionException:
    Modules custommusic and jorbis export package com.jcraft.jogg to module iris
```

Причина: Minecraft сам поставляет библиотеку `org.jcraft:jorbis:0.0.17`
(она нужна ванильному звуковому движку для .ogg). Наш fat jar через Shadow тащил
`com.googlecode.soundlibs:jorbis:0.0.17.4` — это те же пакеты `com.jcraft.jogg` и
`com.jcraft.jorbis`. ModLauncher/BootstrapLauncher строит из jar'ов JPMS-модули,
а два модуля не могут экспортировать один и тот же пакет → игра не стартует вообще.
(В 1.19.2 с этим же набором библиотек проходило, потому что там не было Iris,
который эти пакеты импортирует, — т.е. баг был всегда, просто не выстреливал.)

Как исправлено в `build.gradle`:

```groovy
dependencies {
    // jorbis больше НЕ в списке бандлируемых библиотек
    compileOnly 'org.jcraft:jorbis:0.0.17'   // та же версия, что отдаёт Minecraft
}

tasks.named('shadowJar', ...) {
    exclude 'com/jcraft/**'   // пакет уже есть в Minecraft
    exclude 'junit/**'        // тянулся из старых soundlibs, в рантайме не нужен
    exclude 'org/slf4j/**'    // уже было
    exclude 'org/apache/commons/io/**'  // уже было
}
```

Ключевой момент — `compileOnly` именно на **`org.jcraft:jorbis:0.0.17`** (версия Minecraft),
а не на форк `com.googlecode.soundlibs:jorbis:0.0.17.4`. Тогда `OggVorbisDecoder`
компилируется ровно против тех классов, которые будут в рантайме, и не словит
`NoSuchMethodError`. API совпадает: `Info.synthesis_headerin(Comment, Packet)`,
`DspState.synthesis_init/synthesis_blockin/synthesis_pcmout/synthesis_read`,
`Block.synthesis(Packet)`, `SyncState.buffer/wrote/pageout`.

Общее правило для NeoForge/Forge: **никогда не шейдить то, что уже есть в Minecraft**
(jorbis, guava, gson, commons-io, commons-lang3, log4j, slf4j, netty, fastutil, lwjgl, icu4j, jna, oshi).

### Проверка состава jar

```bash
jar tf build/libs/custommusic-*-all.jar | grep -E "^com/jcraft|^junit|^org/slf4j|^org/apache/commons/io"
# должно быть пусто
```

## 9. Баг, который поймался только запуском: CLIENT-конфиг на сервере

После компиляции мод был проверен реальным запуском выделенного сервера 1.21.1
(`./gradlew runServer`, headless). Первая попытка — краш загрузки:

```
Mod 'custommusic' encountered an error in a deferred task:
java.lang.IllegalStateException: Cannot get config value before config is loaded.
  at com.obninsk.custommusic.music.MusicLibraryManager.init(MusicLibraryManager.java:57)
  at com.obninsk.custommusic.CustomMusicMod.lambda$setup$0(CustomMusicMod.java:37)
```

Причина: `MusicLibraryManager.init()` вызывался из `FMLCommonSetupEvent` и сразу читал
**CLIENT**-конфиг. На клиенте он к этому моменту уже загружен, а на выделенном сервере —
нет, поэтому `ConfigValue.get()` бросает исключение и FML роняет загрузку мода.
(В 1.19.2 это просто не всплывало, потому что мод ставили только на клиент.)

Что сделано:

1. `config/ModConfig.java` — добавлены безопасные геттеры с дефолтами:
   `musicFolder()`, `defaultVolume()`, `pauseVanillaMusic()`, `autoScanOnStartup()`,
   `defaultPlayMode()`, `enableDSF()`, `defaultViewMode()` (внутри `safe(...)` с
   `try/catch`). Прямые `ModConfig.CLIENT.*.get()` из кода убраны.
2. `CustomMusicMod.setup()` больше не трогает библиотеку — common setup пустой.
3. `MusicLibraryManager.init()` вызывается из `ClientEvents.onClientSetup()`
   (`FMLClientSetupEvent`, только клиент) через `event.enqueueWork(...)`.
4. `MusicLibraryManager.init()` сделан идемпотентным (`synchronized` + флаг `initialized`),
   добавлен `ensureInit()`, который дёргается из `getMusicFolder()` и `scanAsync()` —
   даже если GUI откроют раньше client setup, всё проинициализируется лениво.

После фикса сервер стартует чисто:

```
[INFO] [CustomMusic/]: CustomMusic mod initialized (NeoForge / Minecraft 1.21.1)
[INFO] [CustomMusic/]: CustomMusic common setup
[INFO] [minecraft/DedicatedServer]: Done (17.120s)! For help, type "help"
```

Т.е. мод можно спокойно держать в `mods/` и на сервере — он просто ничего там не делает.

> В проекте для 1.19.2 (`custommusic-mod-musicbee`) этот фикс НЕ применялся — там перенесён
> только декодер OGG. Если нужен и сервер-сейф конфиг для 1.19.2 — скажи, перенесу.

## 10. Как собрать

```bash
# нужен JDK 21
./gradlew shadowJar
# -> build/libs/custommusic-1.21.1-1.0.1-all.jar
```

Если `createMinecraftArtifacts` падает с `OutOfMemoryError` (NeoForge декомпилирует
и пересобирает весь Minecraft, нужно ~3-4 ГБ):

```bash
JAVA_TOOL_OPTIONS=-Xmx3G ./gradlew shadowJar      # Linux/macOS
$env:JAVA_TOOL_OPTIONS="-Xmx3G"; .\gradlew shadowJar   # PowerShell
```

На 2 ГБ RAM + swap сборка занимает ~12 минут в первый раз (декомпиляция), дальше — секунды
(всё кешируется в `~/.gradle`).
