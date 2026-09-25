# CustomMusic Mod — MusicBee-стиль плеер для Minecraft 1.21.1 NeoForge

Клиентский мод, добавляющий полноценный музыкальный плеер в стиле MusicBee.
Играй свою музыку из папки `custommusic/` прямо в Minecraft, не заменяя ванильные треки.

> Это порт версии для **1.19.2 Forge** на **1.21.1 NeoForge**.
> Версия для 1.19.2 Forge лежит в ветке/репозитории `custommusic-mod` (jar `custommusic-1.21.1-1.0.1.jar`).

## Поддерживаемые форматы

- **MP3** — через MP3SPI + JLayer
- **WAV** — PCM, любая битность/частота
- **FLAC** — 16/24-bit, включая hi-res (96 kHz) с плавным даунсэмплингом
- **OGG Vorbis** — через JOrbis / VorbisSPI
- **DSF / DFF** — экспериментально (рекомендуется конвертировать в FLAC/WAV)

## Возможности

- Интерфейс в стиле MusicBee: дерево папок / артистов / альбомов слева, таблица треков по центру, очередь и информация о треке справа, плеер снизу.
- Поддержка подпапок: `custommusic/Artist/Album/track.mp3`
- Чтение тегов ID3v1/v2, FLAC, Vorbis через `jaudiotagger`
- Фолбэк парсинг имени файла: `Artist - Album - Title.mp3` или `Artist - Title.mp3`
- Поиск по названию / артисту / альбому / имени файла
- Режимы очереди: последовательно, перемешать, повтор всех, повтор одного
- Перемотка кликом по прогрессбару
- Отдельная громкость через `FloatControl.MASTER_GAIN`
- Фоновое сканирование библиотеки (большая папка не фризит игру)
- Горячие клавиши:
  - `M` — открыть плеер
  - `O` — открыть плеер (вид «Артисты»)
  - `P` — пауза / играть
  - `N` — следующий трек
  - `B` — предыдущий трек
  - `← / →` — предыдущий / следующий (в открытом плеере)
  - `Пробел` — пауза (в открытом плеере)
- Команды:
  - `/custommusic open` — открыть плеер
  - `/custommusic folder` — показать путь к папке
  - `/custommusic rescan` — пересканировать библиотеку
- Не использует Minecraft OpenAL — воспроизведение через Java Sound (`SourceDataLine`)

## Установка

Требуется:
- **Java 21** (Minecraft 1.21.1 не запускается на Java 17)
- **Minecraft 1.21.1**
- **NeoForge 21.1.x** (например 21.1.176+)

Сборка из исходников:

```bash
./gradlew build
# Windows PowerShell: .\gradlew build
```

Готовый jar будет в:

```
build/libs/custommusic-1.21.1-1.0.1.jar
```

Его нужно скопировать в папку модов (`mods/`). Отдельно библиотеки ставить не нужно: аудио-декодеры (MP3SPI/JLayer, jflac, jse-spi-flac) и jaudiotagger лежат внутри этого же jar в `META-INF/jarjar/`, NeoForge подхватывает их сам.

> Суффикс `-all` больше не используется: с версии 1.0.1 вместо шейдинга (fat jar) применяется **Jar-in-Jar**.
> Шейдить `com.jcraft.jorbis` в NeoForge нельзя — Minecraft поставляет его сам, и дубль пакета роняет запуск игры.

При первом запуске создаётся папка `custommusic/` рядом с `mods/`. Помести туда музыку и нажми в GUI **Обновить** или перезапусти игру.

## Конфиг (`config/custommusic-client.toml`)

```toml
[general]
    musicFolder = "custommusic"          # папка относительно директории игры
    defaultVolume = 0.7
    pauseVanillaMusic = true
    autoScanOnStartup = true
    defaultPlayMode = "SEQUENTIAL"       # SEQUENTIAL, SHUFFLE, REPEAT_ALL, REPEAT_ONE
    enableDSF = false
    defaultViewMode = "FOLDERS"          # FOLDERS, ARTISTS, ALBUMS, ALL
```

## Что изменилось при порте на 1.21.1 NeoForge

Подробный список — в `PORT_1.21.1.md`. Коротко:

- ForgeGradle → **ModDevGradle**, `mods.toml` → **`neoforge.mods.toml`**
- `net.minecraftforge.*` → `net.neoforged.*`, `ForgeConfigSpec` → `ModConfigSpec`
- `PoseStack` → `GuiGraphics` (все `drawString` / `fill` теперь вызываются у `GuiGraphics`)
- `new Button(...)` → `Button.builder(...).bounds(...).build()`
- `mouseScrolled` получил параметр `scrollX`
- `sendSuccess(Component, bool)` → `sendSuccess(Supplier<Component>, bool)`
- Java 17 → **Java 21**
- **Бонус:** добавлен собственный декодер OGG Vorbis (`music/OggVorbisDecoder.java`).
  Библиотека `javazoom vorbisspi` (2012 г.) на Java 17/21 определяет .ogg, но отдаёт поток,
  из которого читается **0 байт** — трек молча не игрался. Теперь OGG реально играет.
  Тот же фикс применён и к версии для 1.19.2 Forge.

Проверено на Java 21: MP3, WAV, FLAC, OGG — декодируются в PCM корректно
(тест `FullPathTest`, реальные файлы, не «в ноль»).

## Лицензии

- Код мода — **MIT License** (см. `LICENSE`).
- Сторонние аудио-библиотеки — LGPL-2.1 / Apache-2.0 / BSD и их варианты (см. `THIRD_PARTY_LICENSES.md`).

## FAQ

**Q: Музыка не играет, ошибка "Unsupported audio format"?**
A: Проверь, что используешь `custommusic-1.21.1-1.0.1.jar` и что внутри него есть `META-INF/jarjar/` с библиотеками (`unzip -l custommusic-1.21.1-1.0.1.jar | grep jarjar`). Старые сборки `-all` (1.0.0) не использовать.

**Q: Игра вообще не запускается, в логе `java.lang.module.ResolutionException: Modules custommusic and jorbis export package com.jcraft.jogg`?**
A: Это jar версии **1.0.0** — в него через Shadow попала копия `com.jcraft.jorbis`, которую Minecraft и так поставляет сам (`org.jcraft:jorbis:0.0.17`). Два JPMS-модуля с одним пакетом → ModLauncher падает до загрузки игры.
В **1.0.1** сделано так: библиотеки подключаются через **Jar-in-Jar** (вложенные jar в `META-INF/jarjar/`), `com.jcraft.jorbis` в мод не кладётся вообще, а свой OGG-декодер компилируется против той же версии jorbis (`org.jcraft:jorbis:0.0.17`), которую отдаёт игра. Удали старый jar из `mods/` и положи `custommusic-1.21.1-1.0.1.jar`.

**Q: Сборка падает с `Unsupported class file major version` / требует Java 17?**
A: Нужен JDK **21**. Проверь `java -version` и `JAVA_HOME`.

**Q: Сборка падает на `createMinecraftArtifacts` с OutOfMemoryError?**
A: NeoForge декомпилирует и пересобирает Minecraft — нужно много памяти. Закрой другие программы или задай
`JAVA_TOOL_OPTIONS=-Xmx3G` (Linux/Mac: `export JAVA_TOOL_OPTIONS=-Xmx3G`, PowerShell: `$env:JAVA_TOOL_OPTIONS="-Xmx3G"`).

**Q: При открытии меню подвисает на большой библиотеке?**
A: Сканирование фоновое. При открытии экрана может кратковременно показываться «Сканирование библиотеки…», но игра не замораживается.

**Q: Можно слушать музыку на сервере для всех игроков?**
A: Нет, плеер полностью клиентский. Для серверного вещания нужен отдельный мод.

**Q: Мод можно положить в `mods/` на выделенном сервере?**
A: Да. Он загрузится и ничего не будет делать (проверено запуском dedicated server 1.21.1).
CLIENT-конфиг на сервере не читается — все значения берутся с безопасными дефолтами.

**Q: OGG не играл в старой версии?**
A: В этой версии добавлен собственный декодер OGG Vorbis (`music/OggVorbisDecoder.java`) —
старая библиотека javazoom vorbisspi на Java 17/21 отдавала пустой поток. Проверено:
MP3 / WAV / FLAC / OGG декодируются в PCM на Java 21.

Удачного прослушивания!
