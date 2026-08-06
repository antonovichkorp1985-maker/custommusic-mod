# Отчет сборки CustomMusic 1.0.0

## Дата сборки
2026-07-12
Forge: 1.19.2-43.4.0
Java: Temurin 17.0.11
Gradle: 8.1

## Артефакты

- `build/libs/custommusic-1.0.0.jar` (48KB) - slim jar, только классы мода. Требует отдельные зависимости в mods/
- `build/libs/custommusic-1.0.0-all.jar` (2.3MB) - fat jar со всеми аудио библиотеками:
  - mp3spi 1.9.5.4
  - jlayer 1.0.1.4
  - tritonus-share 0.3.7.4
  - jorbis 0.0.17.4
  - vorbisspi 1.0.3.3
  - jflac-codec 1.5.2
  - jse-spi-flac 1.1.0 + jse-api 1.1.0 + vorbis-java-core 0.8
  - jaudiotagger 3.0.1
  - commons-io 2.11.0

Для игрока рекомендуется **all jar**.

## Исправления во время сборки

1. Зависимость `com.googlecode.soundlibs:jflac:1.3.0` не резолвится в Maven Central. Заменена на:
   - `org.jflac:jflac-codec:1.5.2`
   - `io.github.jseproject:jse-spi-flac:1.1.0`
2. `EditBox.setHint` отсутствует в 1.19.2, заменен на `setSuggestion`
3. Ограничение памяти 1.9GB в контейнере, пришлось снизить `org.gradle.jvmargs` до `-Xmx512m` и увеличить до 768m для финальной сборки. MCP настройка прошла успешно после кэширования.

## Команды сборки

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew wrapper --gradle-version 8.1
./gradlew build -x test --no-daemon
```

Fat jar собирается скриптом:

```bash
mkdir /tmp/fatjar && cd /tmp/fatjar
unzip build/libs/custommusic-1.0.0.jar
for jar in <deps>; do unzip -o $jar; done
rm META-INF/*.DSA META-INF/*.RSA META-INF/*.SF
jar cf custommusic-1.0.0-all.jar -C /tmp/fatjar .
```

## Проверка

- Классы: `com.obninsk.custommusic.*`
- Ресурсы: `META-INF/mods.toml`, `assets/custommusic/lang/*`, `pack.mcmeta`
- SPI: `META-INF/services/javax.sound.sampled.spi.*` содержит регистрации mp3, flac, vorbis

ModId: `custommusic`, версия 1.0.0, loader [43,)

## Установка

1. Скопируйте `custommusic-1.19.2-forge-43.4.0-all-1.0.0.jar` в `mods/`
2. Запустите игру, закройте
3. Положите музыку в `custommusic/`
4. Запустите снова, нажмите M

Готово!
