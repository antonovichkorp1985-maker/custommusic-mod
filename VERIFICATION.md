# Верификация сборки 1.21.1-1.0.1

Окружение проверки: Linux, JDK 21.0.12, **NeoForge 21.1.251**, Minecraft 1.21.1,
официальный dedicated server (установщик `neoforge-21.1.251-installer.jar --installServer`).

## 1. Сборка и встроенные проверки

```
./gradlew build
> Task :mergeJseLibs
> Task :jarJar
> Task :jar
> Task :checkNoMinecraftDuplicates
OK: в jar нет дубликатов библиотек Minecraft
> Task :checkNoSplitPackages
OK: во вложенных JarJar (7 шт.) нет общих пакетов
BUILD SUCCESSFUL
```

Обе проверки живут в `build.gradle` и валят сборку, если проблема вернётся.

## 2. Состав jar

```
META-INF/neoforge.mods.toml
META-INF/jarjar/mp3spi-1.9.5.4.jar
META-INF/jarjar/jlayer-1.0.1.4.jar
META-INF/jarjar/tritonus-share-0.3.7.4.jar
META-INF/jarjar/jflac-codec-1.5.2.jar
META-INF/jarjar/jse-flac-1.1.0-merged.jar
META-INF/jarjar/vorbis-java-core-0.8.jar
META-INF/jarjar/jaudiotagger-3.0.1.jar
META-INF/jarjar/metadata.json
```

Классов `com/jcraft/**`, `junit/**`, `org/slf4j/**`, `org/apache/commons/io/**` в jar нет.

## 3. Запуск на NeoForge 21.1.251 + самодиагностика в рантайме

В JVM-аргументы сервера добавлен `-Dcustommusic.selftest=<папка с mp3/wav/ogg/flac>`,
jar положен в `mods/`, запуск `sh run.sh --nogui`. **`ResolutionException` — 0 вхождений**
(в версии 1.0.0 их было 2, и процесс умирал с кодом 2 ещё до загрузки игры).

```
[18:54:42.901] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found mod file "custommusic-1.21.1-1.0.1.jar" [locator: {mods folder locator at /home/user/.cache/nfserver/mods}, reader: mod manifest]
[18:54:43.089] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found gamelibrary file "mixinextras-neoforge-0.5.3.jar" [parent: neoforge-21.1.251-universal.jar, locator: jarinjar, reader: mod manifest]
[18:54:43.089] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jse-flac-1.1.0-merged.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:54:43.089] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "vorbis-java-core-0.8.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:54:43.090] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jflac-codec-1.5.2.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:54:43.090] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "tritonus-share-0.3.7.4.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:54:43.090] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jlayer-1.0.1.4.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:54:43.090] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jaudiotagger-3.0.1.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:54:43.090] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "mp3spi-1.9.5.4.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:54:43.090] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "net.neoforged.neoforge-coremods-21.1.251.jar" [parent: neoforge-21.1.251-universal.jar, locator: jarinjar, reader: mod manifest]
		Custom Music Player 1.0.1 (custommusic)
[18:54:51] [modloading-worker-0/INFO] [ne.ne.ne.co.NeoForgeMod/NEOFORGE-MOD]: NeoForge mod loading, version 21.1.251, for MC 1.21.1
[18:54:51] [modloading-worker-0/INFO] [CustomMusic/]: CustomMusic mod initialized (NeoForge / Minecraft 1.21.1)
[18:54:51] [modloading-worker-0/INFO] [CustomMusic/]: CustomMusic common setup
[18:54:56] [Server thread/INFO] [minecraft/DedicatedServer]: Done (1.113s)! For help, type "help"
```

Отчёт самодиагностики, снятый **внутри процесса NeoForge** (TCCL —
`cpw.mods.modlauncher.TransformingClassLoader`, т.е. ровно тот classloader, которым
Java Sound ищет SPI):

```
=== CustomMusic selftest ===
Java: 21.0.12.1 | TCCL: cpw.mods.modlauncher.TransformingClassLoader@1dcca8d3
-- AudioFileReader (ServiceLoader) --
   io.github.jseproject.FlacAudioFileReader  [union:/home/user/.cache/nfserver/mods/custommusic-1.21.1-1.0.1.jar%23120_/META-INF/jarjar/jse-flac-1.1.0-merged.jar%23133!/]
   org.jflac.sound.spi.FlacAudioFileReader  [union:/home/user/.cache/nfserver/mods/custommusic-1.21.1-1.0.1.jar%23120_/META-INF/jarjar/jflac-codec-1.5.2.jar%23130!/]
   javazoom.spi.mpeg.sampled.file.MpegAudioFileReader  [union:/home/user/.cache/nfserver/mods/custommusic-1.21.1-1.0.1.jar%23120_/META-INF/jarjar/mp3spi-1.9.5.4.jar%23135!/]
   com.sun.media.sound.AiffFileReader  [?]
   com.sun.media.sound.AuFileReader  [?]
   com.sun.media.sound.SoftMidiAudioFileReader  [?]
   com.sun.media.sound.WaveFileReader  [?]
   com.sun.media.sound.WaveFloatFileReader  [?]
   com.sun.media.sound.WaveExtensibleFileReader  [?]
-- FormatConversionProvider (ServiceLoader) --
   io.github.jseproject.FlacFormatConversionProvider  [union:/home/user/.cache/nfserver/mods/custommusic-1.21.1-1.0.1.jar%23120_/META-INF/jarjar/jse-flac-1.1.0-merged.jar%23133!/]
   org.jflac.sound.spi.FlacFormatConversionProvider  [union:/home/user/.cache/nfserver/mods/custommusic-1.21.1-1.0.1.jar%23120_/META-INF/jarjar/jflac-codec-1.5.2.jar%23130!/]
   javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider  [union:/home/user/.cache/nfserver/mods/custommusic-1.21.1-1.0.1.jar%23120_/META-INF/jarjar/mp3spi-1.9.5.4.jar%23135!/]
   com.sun.media.sound.AlawCodec  [?]
   com.sun.media.sound.AudioFloatFormatConverter  [?]
   com.sun.media.sound.PCMtoPCMCodec  [?]
   com.sun.media.sound.UlawCodec  [?]
-- AudioSystem.getAudioFileTypes() --
   WAVE
   AU
   AIFF
   FLAC
   SND
   OGG-FLAC
-- jorbis (???? Minecraft) --
   com.jcraft.jorbis.DspState: ??? (ClassNotFoundException)
-- ????? (5 ???????, ????????? ?? 4) --
   sample-3s.mp3                      openedBy=AudioSystem                                    3.27s read, RMS=3937 -> OK
   sample-3s.wav                      openedBy=AudioSystem                                    3.20s read, RMS=4201 -> OK
   ogg_sample1.ogg -> FAIL javax.sound.sampled.UnsupportedAudioFileException: File of unsupported format
   sample1.flac                       openedBy=io.github.jseproject.FlacAudioFileReader       20.02s read, RMS=10747 -> OK

[18:54:52] [main/INFO] [mojang/YggdrasilAuthenticationService]: Environment: Environment[sessionHost=https://sessionserver.mojang.com, servicesHost=https://api.minecraftservices.com, name=PROD]
[18:54:52] [main/WARN] [minecraft/VanillaPackResourcesBuilder]: Assets URL 'union:/home/user/.cache/nfserver/libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar%23118!/assets/.mcassetsroot' uses unexpected schema
[18:54:52] [main/WARN] [minecraft/VanillaPackResourcesBuilder]: Assets URL 'union:/home/user/.cache/nfserver/libraries/net/minecraft/server/1.21.1-20240808.144430/server-1.21.1-20240808.144430-srg.jar%23118!/data/.mcassetsroot' uses unexpected schema
[18:54:53] [main/INFO] [minecraft/Main]: No existing world data, creating new world
[18:54:53] [main/ERROR] [minecraft/DedicatedServerProperties]: No key layers in MapLike[{}]
[18:54:54] [main/INFO] [minecraft/RecipeManager]: Loaded 1290 recipes
[18:54:54] [main/INFO] [minecraft/AdvancementTree]: Loaded 1399 advancements
[18:54:55] [Server thread/INFO] [minecraft/DedicatedServer]: Starting minecraft server version 1.21.1
[18:54:55] [Server thread/INFO] [minecraft/DedicatedServer]: Loading properties
[18:54:55] [Server thread/INFO] [minecraft/DedicatedServer]: Default game type: SURVIVAL
[18:54:55] [Server thread/INFO] [minecraft/MinecraftServer]: Generating keypair
[18:54:55] [Server thread/INFO] [minecraft/DedicatedServer]: Starting Minecraft server on *:25565
[18:54:55] [Server thread/INFO] [minecraft/ServerConnectionListener]: Using epoll channel type
[18:54:55] [Server thread/WARN] [minecraft/DedicatedServer]: **** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!
[18:54:55] [Server thread/WARN] [minecraft/DedicatedServer]: The server will make no attempt to authenticate usernames. Beware.
[18:54:55] [Server thread/WARN] [minecraft/DedicatedServer]: While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.
[18:54:55] [Server thread/WARN] [minecraft/DedicatedServer]: To change this, set "online-mode" to "true" in the server.properties file.
[18:54:55] [Server thread/INFO] [minecraft/DedicatedServer]: Preparing level "world"
[18:54:56] [Server thread/INFO] [minecraft/MinecraftServer]: Preparing start region for dimension minecraft:overworld
[18:54:56] [Worker-Main-1/INFO] [minecraft/LoggerChunkProgressListener]: Preparing spawn area: 2%
[18:54:56] [Server thread/INFO] [minecraft/LoggerChunkProgressListener]: Time elapsed: 402 ms
[18:54:56] [Server thread/INFO] [minecraft/
```

Что из этого следует:

- **SPI из JarJar видны**: `io.github.jseproject.FlacAudioFileReader`,
  `org.jflac.sound.spi.FlacAudioFileReader`, `javazoom.spi.mpeg.sampled.file.MpegAudioFileReader`
  найдены через `ServiceLoader`, источник — `union:/.../custommusic-1.21.1-1.0.1.jar_/META-INF/jarjar/<lib>.jar!/`.
  JarJar не ломает Java Sound SPI.
- **Декодирование в рантайме игры работает**: MP3 3.27s, WAV 3.20s, FLAC 20.02s (самодиагностика
  ограничивает чтение 20 секундами), RMS везде ненулевой — это не «открылось и пусто».
- `AudioSystem.getAudioFileTypes()` в игре отдаёт `WAVE, AU, AIFF, FLAC, SND, OGG-FLAC`.
- **OGG на dedicated server не открывается — и это норма**: `com.jcraft.jorbis.DspState: НЕТ
  (ClassNotFoundException)`, потому что `org.jcraft:jorbis:0.0.17` есть только в списке
  клиентских библиотек Minecraft. На клиенте она присутствует (в логе Prism Launcher видно
  `libraries/org/jcraft/jorbis/0.0.17/jorbis-0.0.17.jar`), поэтому в игре OGG декодируется;
  отдельно это проверено на Java 21 вне игры (раздел 4). На сервере ошибка обрабатывается
  gracefully — мод не падает.

## 4. Декодирование вне игры (Java 21, тот же набор библиотек + jorbis 0.0.17 из клиента)

Тест вызывает реальный код мода (`AudioSpiSelector`, `OggVorbisDecoder`), а не его копию:

```
MP3  sample-3s.mp3   openedBy=AudioSystem                                3.27 s   576000 bytes  RMS=3937   OK
WAV  sample-3s.wav   openedBy=AudioSystem                                3.20 s   563712 bytes  RMS=4201   OK
OGG  ogg_sample1.ogg openedBy=OggVorbisDecoder                         122.09 s 21537304 bytes  RMS=10740  OK
FLAC sample1.flac    openedBy=io.github.jseproject.FlacAudioFileReader  122.09 s 21537304 bytes  RMS=10783  OK
FLAC sample2.flac    openedBy=io.github.jseproject.FlacAudioFileReader  217.36 s 38342040 bytes  RMS=13685  OK
RESULT: ALL FORMATS OK
```

Перебор целевых PCM-форматов (как делает `AudioPlayerManager.buildTargetCandidates`):

```
FLAC 44100/16 -> PCM 44100/16  OK
FLAC 44100/16 -> PCM 48000/16  OK   (ресэмплинг: умеет jse-spi-flac, jflac - нет)
FLAC 44100/16 -> PCM 48000/24  FAIL Unsupported conversion (исходник 16-битный, так и должно быть)
```

Независимость от порядка библиотек в classpath (FLAC):

```
jflac-first / jse-first / only-jflac -> открылось и декодировалось, OK
only-jse без vorbis-java-core        -> NoClassDefFoundError: org/gagravarr/flac/FlacFile
                                        (поэтому vorbis-java-core оставлен в JarJar)
```

## 5. Найденные и исправленные проблемы

| Симптом | Причина | Фикс |
|---|---|---|
| `Modules custommusic and jorbis export package com.jcraft.jogg to module iris` | fat jar шейдил `com.jcraft.jorbis`, а Minecraft поставляет `org.jcraft:jorbis:0.0.17` сам | Shadow убран, jorbis не бандлится; `compileOnly 'org.jcraft:jorbis:0.0.17'` |
| `Modules jse.api and jse.spi.flac export package io.github.jseproject` | две разные библиотеки с одним пакетом | склеены в `jse-flac-1.1.0-merged.jar` (задача `mergeJseLibs`) |
| `Modules io.github.jseproject.merged and vorbis.java.core export package org.gagravarr.theora` | конфигурация склейки подтянула транзитивный vorbis-java-core внутрь merged-jar | `transitive = false` у `configurations.jseMerge` |
| jse-провайдеров не было в `ServiceLoader` | `exclude 'META-INF/services/**'` на уровне задачи вырезал и слитые нами сервис-файлы | exclude перенесён внутрь `from(zipTree(...))` |
| FLAC: `IllegalArgumentException: conversion not supported` | `AudioSystem` берёт первого провайдера и не откатывается; reader и converter оказывались из разных библиотек, результат зависел от порядка jar'ов | `AudioSpiSelector`: пара reader+converter из одной библиотеки + перебор провайдеров с откатом |
| OGG: 0 байт PCM | javazoom vorbisspi (2012) на Java 17/21 отдаёт пустой поток | свой `OggVorbisDecoder` на ванильном jorbis из Minecraft |
| `Cannot get config value before config is loaded` (краш загрузки на сервере) | CLIENT-конфиг читался в `FMLCommonSetupEvent` | безопасные геттеры в `ModConfig`, init перенесён в `FMLClientSetupEvent` |
| CI: `./gradlew: Permission denied`, exit 126 | у `gradlew` в git не было бита +x | `git update-index --chmod=+x gradlew` + `chmod +x` в workflow |

## 6. Что НЕ проверялось (нужен запуск у владельца)

- Рендер и клики в GUI: нужен клиент с OpenGL, в контейнере его нет.
- Запись звука в `SourceDataLine` (реальная звуковая карта). Проверено всё, что идёт до неё:
  открытие файла, выбор SPI, декодирование в PCM, конвертация формата.
- Поведение рядом с конкретными модами сборки владельца (Create, GTCEu, Mekanism, IE,
  Sodium/Iris). Со своей стороны мод больше не имеет общих пакетов ни с Minecraft,
  ни между собственными библиотеками — именно это и вызывало `ResolutionException`.

## 7. Как повторить проверку

```bash
./gradlew build     # checkNoMinecraftDuplicates и checkNoSplitPackages отработают сами
```

Самодиагностика в рантайме:

```
-Dcustommusic.selftest=/путь/к/папке/с/музыкой     # отчёт попадёт в latest.log
/custommusic selftest                              # то же самое командой в игре
```
