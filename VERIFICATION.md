# Верификация сборки 1.21.1-1.0.1

Что реально проверялось (и чем). Окружение: Linux, JDK 21.0.12, NeoForge 21.1.251, Minecraft 1.21.1.

## 1. Сборка

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

Обе проверки — задачи в `build.gradle`, они валят сборку, если проблема вернётся.

## 2. Состав jar

```
META-INF/neoforge.mods.toml
META-INF/jarjar/jlayer-1.0.1.4.jar
META-INF/jarjar/mp3spi-1.9.5.4.jar
META-INF/jarjar/tritonus-share-0.3.7.4.jar
META-INF/jarjar/jaudiotagger-3.0.1.jar
META-INF/jarjar/vorbis-java-core-0.8.jar
META-INF/jarjar/jflac-codec-1.5.2.jar
META-INF/jarjar/jse-flac-1.1.0-merged.jar
META-INF/jarjar/metadata.json
```

Классов `com/jcraft/**`, `junit/**`, `org/slf4j/**`, `org/apache/commons/io/**` в jar нет
(проверка `unzip -l ... | grep -E "com/jcraft|junit"` — пусто).

## 3. Запуск на чистом NeoForge 21.1.251 (dedicated server, headless)

Установлен официальный сервер `neoforge-21.1.251-installer.jar --installServer`,
наш jar положен в `mods/`, запуск `sh run.sh --nogui`:

```
[18:38:07.904] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found mod file "custommusic-1.21.1-1.0.1.jar" [locator: {mods folder locator at /home/user/.cache/nfserver/mods}, reader: mod manifest]
[18:38:08.107] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found gamelibrary file "mixinextras-neoforge-0.5.3.jar" [parent: neoforge-21.1.251-universal.jar, locator: jarinjar, reader: mod manifest]
[18:38:08.108] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jse-flac-1.1.0-merged.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:38:08.108] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "vorbis-java-core-0.8.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:38:08.108] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jflac-codec-1.5.2.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:38:08.108] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "tritonus-share-0.3.7.4.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:38:08.108] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jlayer-1.0.1.4.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:38:08.108] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "jaudiotagger-3.0.1.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:38:08.108] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "mp3spi-1.9.5.4.jar" [parent: custommusic-1.21.1-1.0.1.jar, locator: jarinjar]
[18:38:08.109] [main/INFO] [loading.moddiscovery.ModDiscoverer/SCAN]: Found library file "net.neoforged.neoforge-coremods-21.1.251.jar" [parent: neoforge-21.1.251-universal.jar, locator: jarinjar, reader: mod manifest]
     Mod List:
		Custom Music Player 1.0.1 (custommusic)
[18:38:16] [modloading-worker-0/INFO] [ne.ne.ne.co.NeoForgeMod/NEOFORGE-MOD]: NeoForge mod loading, version 21.1.251, for MC 1.21.1
[18:38:16] [modloading-worker-0/INFO] [CustomMusic/]: CustomMusic mod initialized (NeoForge / Minecraft 1.21.1)
[18:38:17] [modloading-worker-0/INFO] [CustomMusic/]: CustomMusic common setup
[18:38:18] [main/ERROR] [minecraft/DedicatedServerProperties]: No key layers in MapLike[{}]
[18:38:20] [Server thread/INFO] [minecraft/DedicatedServer]: Starting minecraft server version 1.21.1
[18:38:21] [Server thread/INFO] [minecraft/DedicatedServer]: Done (1.275s)! For help, type "help"
```

Ключевое: **`ResolutionException` — 0 вхождений** (в версии 1.0.0 их было 2, и процесс
умирал с кодом 2 ещё до загрузки игры).

> Это проверка загрузчика/модулей/конфига. Клиентский GUI так не проверить — нужен
> запуск с OpenGL, т.е. на машине владельца.

## 4. Декодирование аудио (Java 21, ровно тот набор библиотек, что в jar)

Тест гоняет реальный код мода (`AudioSpiSelector` + `OggVorbisDecoder`), а не «похожий» код:

```
=== MP3 ===
  openedBy: AudioSystem (mp3spi)      decoded:   3.27 s,   576000 bytes, RMS=3937   OK
=== WAV ===
  openedBy: AudioSystem               decoded:   3.20 s,   563712 bytes, RMS=4201   OK
=== OGG (свой декодер поверх jorbis из Minecraft) ===
  openedBy: OggVorbisDecoder          decoded: 122.09 s, 21537304 bytes, RMS=10740  OK
=== FLAC (sample1) ===
  openedBy: io.github.jseproject.FlacAudioFileReader
                                      decoded: 122.09 s, 21537304 bytes, RMS=10783  OK
=== FLAC (sample2) ===
  openedBy: io.github.jseproject.FlacAudioFileReader
                                      decoded: 217.36 s, 38342040 bytes, RMS=13685  OK
RESULT: ALL FORMATS OK

=== FLAC, перебор целевых форматов (как делает AudioPlayerManager) ===
  target PCM_SIGNED 44100 Hz 16 bit -> 38342040 bytes, 217.36 s  OK
  target PCM_SIGNED 48000 Hz 16 bit -> 38342040 bytes, 199.70 s  OK   (ресэмплинг 44.1 -> 48, умеет jse-spi-flac)
  target PCM_SIGNED 48000 Hz 24 bit -> FAIL Unsupported conversion (исходник 16-битный, так и должно быть)

=== Проверка порядка библиотек в classpath (FLAC не должен зависеть от порядка) ===
  jflac-first / jse-first / only-jflac -> открылось, 122.09 s, OK
  only-jse без vorbis-java-core        -> NoClassDefFoundError: org/gagravarr/flac/FlacFile
                                          (поэтому vorbis-java-core оставлен в JarJar)
```

`RMS` считается по decoded PCM — это защита от «поток открылся, но в нём тишина/ноль байт»
(именно так выглядел сломанный OGG в javazoom vorbisspi).

## 5. Найденные и исправленные проблемы запуска

| Симптом | Причина | Фикс |
|---|---|---|
| `Modules custommusic and jorbis export package com.jcraft.jogg to module iris` | fat jar шейдил `com.jcraft.jorbis`, а Minecraft поставляет `org.jcraft:jorbis:0.0.17` сам | jorbis не бандлится; `compileOnly 'org.jcraft:jorbis:0.0.17'` |
| `Modules jse.api and jse.spi.flac export package io.github.jseproject` | две разные библиотеки с одним пакетом | склеены в один `jse-flac-1.1.0-merged.jar` (задача `mergeJseLibs`) со слиянием `META-INF/services` |
| `Modules io.github.jseproject.merged and vorbis.java.core export package org.gagravarr.theora` | конфигурация склейки подтянула транзитивный vorbis-java-core внутрь merged-jar | `configurations.jseMerge { transitive = false }` |
| FLAC: `IllegalArgumentException: conversion not supported` | `AudioSystem` берёт первого провайдера и не откатывается; reader и converter получались из разных библиотек, порядок зависел от расположения jar'ов | `AudioSpiSelector` выбирает пару reader+converter из одной библиотеки и перебирает провайдеров сам |
| OGG: 0 байт PCM | javazoom vorbisspi (2012) на Java 17/21 отдаёт пустой поток | свой `OggVorbisDecoder` на ванильном jorbis из Minecraft |
| `Cannot get config value before config is loaded` (краш загрузки на сервере) | CLIENT-конфиг читался в `FMLCommonSetupEvent` | безопасные геттеры в `ModConfig` + init перенесён в `FMLClientSetupEvent` |

## 6. Что НЕ проверялось

- Рендер и клики в GUI (нужен клиент с OpenGL).
- Воспроизведение звука в реальной звуковой карте (`SourceDataLine` в контейнере недоступен) —
  проверялось только декодирование в PCM, т.е. всё до записи в линию.
- Совместимость с конкретными модами из сборки владельца (Create, GTCEu, Mekanism, IE,
  Sodium/Iris): проверено, что своих конфликтов пакетов у мода больше нет ни с Minecraft,
  ни между собственными библиотеками.
