# Документация CustomMusic Mod v1.0.0

## 1. Архитектура

### Поток данных
```
[Папка custommusic/]
        |
        v
MusicLibraryManager.scan()  -> рекурсивный обход, фильтр по расширениям
        |
        v
Track(File) + MetadataParser.enrich() -> ID3/FLAC теги или парсинг имени
        |
        v
Map<ArtistName, Artist> -> Artist содержит Map<AlbumName, Album> -> Album содержит List<Track>
        |
        v
GUI (MusicPlayerScreen) -> выбор Artist/Album/Track
        |
        v
PlaylistManager.setQueue(List<Track>) + PlayMode
        |
        v
AudioPlayerManager.play(Track) -> отдельный Thread "CustomMusic-Player" -> декодинг через AudioSystem + SPI -> SourceDataLine
        |
        v
Колонки игрока (Java Sound, не OpenAL)
```

### Многопоточность
- Главный поток Minecraft (Render Thread) - GUI, ввод
- Фоновый тред сканирования - `CompletableFuture.runAsync` в `MusicLibraryManager.scanAsync()`
- Тред плеера - `ExecutorService` singleThread `CustomMusic-Player` в `AudioPlayerManager`. Декодинг и запись в линию происходит там, чтобы не фризить игру.
- Пауза реализована через `Object.wait/notify` и `SourceDataLine.stop/start`

### Аудио конвейер
```
File -> AudioInputStream base (mp3spi/jflac/vorbisspi декодируют в PCM_SIGNED или свой формат)
     -> AudioSystem.getAudioInputStream(targetFormat, base) // конвертация частоты/битности
     -> SourceDataLine.write(byte[])
```

TargetFormat по умолчанию:
```java
new AudioFormat(PCM_SIGNED, base.getSampleRate(), 16, channels, channels*2, baseRate, false)
```
Фолбэк если конвертация не поддерживается: `PCM 44100Hz 16-bit Stereo`

Громкость: `FloatControl.Type.MASTER_GAIN` с логарифмической конверсией линейной громкости 0..1 в dB.

## 2. Форматы и библиотеки

| Формат | Расширение | Библиотека декодирования | Метод | Статус |
|--------|------------|--------------------------|-------|--------|
| WAV | .wav | javax.sound.sampled (JDK) | нативно | ✅ |
| MP3 | .mp3 | com.googlecode.soundlibs:mp3spi + jlayer + tritonus-share | SPI | ✅ |
| FLAC | .flac | com.googlecode.soundlibs:jflac | SPI | ✅ |
| OGG Vorbis | .ogg | com.googlecode.soundlibs:vorbisspi + jorbis | SPI | ✅ |
| DSF | .dsf | нет стабильного SPI, парсер заголовка свой | заголовок читается, воспроизведение экспериментально | ⚠️ опционально |
| DFF | .dff | аналогично DSF | | ⚠️ |

SPI (Service Provider Interface) - механизм Java Sound: кладем jar с `META-INF/services/javax.sound.sampled.spi.AudioFileReader` и `FormatConversionProvider` в classpath, после этого `AudioSystem.getAudioInputStream` начинает понимать новые форматы.

Проблема: Forge Gradle по умолчанию не шейдит зависимости в мод jar. Надо или:
- использовать `shadowJar` плагин
- или использовать Forge `JarJar` (рекомендуется для 1.19.2+): положить зависимости в папку `META-INF/jarjar/` и указать в `mods.toml`
- или просто положить рядом библиотеки в `mods/` (неудобно)

В настоящем проекте зависимости указаны как `implementation` - при сборке они попадут в `mcmodsrepo` если настроен `maven`, но для итогового jar нужен Shadow. Мы оставили комментарий в FAQ.

## 3. GUI - устройство

`MusicPlayerScreen extends Screen`

Рендер в `render()`:
- `renderBackground`
- верхняя строка: название мода, строка NowPlaying
- три колонки: фон `fill(0x88000000)`, заголовки, списки
- списки рендерятся вручную через цикл по видимым строкам (не используется `ObjectSelectionList` для упрощения контроля)
- подсветка: выбранный - синий/красный/зеленый, ховер - серый, сейчас играет - зеленый
- скролл: переменные `artistScroll`, `albumScroll`, `trackScroll`, управляются `mouseScrolled`
- клики: `mouseClicked` определяет колонку по `mouseX` и ряд по `mouseY`, обновляет `selectedArtist/Album` и вызывает `filterTracks()`
- контролы: `Button` виджеты Forge

Особенности:
- Поиск `EditBox` с `setResponder` -> `filterTracks()` каждый ввод
- `setStatus()` - сообщение внизу на 4 секунды
- `isPauseScreen() = false` - чтобы музыка играла когда GUI открыт и игра не ставилась на паузу

## 4. Конфигурация плейлистов и порядка

`PlaylistManager`:
- `originalQueue` - исходный порядок (как в библиотеке / альбоме)
- `currentQueue` - порядок с учетом режима
  - SEQUENTIAL: как есть
  - SHUFFLE: `Collections.shuffle`
  - REPEAT_ALL / REPEAT_ONE: как есть, но логика `next()` зацикливает
- `currentIndex` - текущая позиция

При смене режима `setPlayMode()` сохраняет текущий трек и ищет его в новой очереди.

При окончании трека `AudioPlayerManager.handleAutoNext()` вызывает `PlaylistManager.next()`.

Пользователь может:
- кликнуть на трек в правом списке -> `playTrack()` -> `setQueue(trackDisplay, index)` + `play`
- выбрать исполнителя/альбом -> список треков фильтруется, но очередь не меняется до явного плей. Это позволяет сначала отфильтровать, потом играть.

Будущее расширение: сохранять плейлисты в `custommusic/playlists/*.json` формате:
```json
{
  "name": "My Mix",
  "tracks": ["path/to/file1.mp3", "path/to/file2.flac"],
  "mode": "SHUFFLE"
}
```

## 5. Метаданные

`MetadataParser` использует `org.jaudiotagger:jaudiotagger:2.0.3` (или `net.jthink:jaudiotagger:3.0.1`).

Логика:
```java
AudioFile af = AudioFileIO.read(file);
Tag tag = af.getTag();
title = tag.getFirst(FieldKey.TITLE) etc.
```

Если тегов нет - остается fallback из имени файла, реализованный в `Track.parseFallbackMetadata()`.

Поддерживаются:
- MP3: ID3v1, ID3v2.2/2.3/2.4
- FLAC: Vorbis Comments
- OGG: Vorbis Comments
- WAV: INFO chunk + ID3 в некоторых случаях

Для DSF: jaudiotagger не поддерживает DSD теги (ID3 chunk в DSF). Можно парсить ID3 вручную - в DSF после заголовков идет ID3 chunk по `metadataOffset`. Это можно добавить позже.

## 6. DSF / DSD подробный разбор

Структура DSF файла (little-endian):
```
0-3: "DSD " (0x44 0x53 0x44 0x20)
4-11: chunk size (28)
12-19: total file size
20-27: metadata offset (0 = no metadata)

28-31: "fmt " chunk
32-39: fmt chunk size (52)
40-43: format version (1)
44-47: format ID (0 = DSD)
48-51: channel type (2 = stereo etc)
52-55: channel num (1=mono,2=stereo,5=5.0 etc)
56-59: sampling frequency (2822400, 5644800, 11289600 etc)
60-63: bits per sample (1)
64-71: sample count (кол-во семплов на канал)
72-75: block size per channel (4096)
76-79: reserved

80-83: "data" chunk
84-91: data chunk size
92-...: DSD data interleaved per block per channel LSB first
```

DSD data: 1-bit, в каждом байте 8 семплов, LSB first. Для стерео блок 4096 байт на канал, интерливинг: сначала 4096 байт левого, потом 4096 правого, повтор.

Чтобы воспроизвести через PCM, нужно:
- прочитать DSD блоки
- применить low-pass FIR фильтр, обычно с коэффициентами для decimation 1:8 или более.

Например DSD64 2822400Hz -> PCM 44100Hz требует decimation x64 (2822400/44100=64).

Упрощенный FIR: можно использовать библиотеку `com.github.psamborski:dsd2pcm` или портировать `dsd2pcm` из C.

В моде:
- `DSFParser` читает заголовок, отдает `DSFInfo`
- В `AudioPlayerManager.handleDSD` пытается открыть через `AudioSystem` (если пользователь добавил свой SPI)
- Если не получается - кидает ошибку с рекомендацией конвертировать через ffmpeg.

Пример конвертации:
```bash
ffmpeg -i album.dsf -ar 88200 -ac 2 -sample_fmt s32 album_88k.flac
```

## 7. Интеграция с Minecraft (Forge)

- Мод клиентский (`side=CLIENT` в mods.toml)
- Требует Forge 43.4.0+
- Java 17
- KeyBindings регистрируются через `RegisterKeyMappingsEvent` (Forge 41+ заменил `ClientRegistry`)
- Команды регистрируются через `RegisterClientCommandsEvent` (клиентские команды)

Для приглушения ванильной музыки можно добавить в `AudioPlayerManager`:

```java
Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
```

И при стопе вернуть старое значение из `ModConfig`.

Это пока оставлено как TODO чтобы не конфликтовать с пользовательскими настройками.

## 8. Безопасность и производительность

- Сканирование библиотеки - рекурсивный обход, может быть медленным если много файлов. Для 1000 треков ~2-3 сек. Выполняется в фоне.
- Декодинг трека - тред `CustomMusic-Player` не блокирует главный. Буфер 8192 байт.
- Память: не грузим весь трек в RAM, стримим.
- Если игра крашнется во время игры, `SourceDataLine` закрывается в `stop()` и `shutdown()` (вызывается при закрытии мира? Нужно добавить слушатель `FMLClientSetupEvent` для хука).
- Не используем нативные либы чтобы не зависеть от OS, но для DSD возможно понадобится JNI.

## 9. План развития

### v1.1
- Сохранение плейлистов JSON
- Drag&drop файлов в GUI (через `RenderSystem`? В Minecraft нет DnD, но можно кнопку "Добавить папку")
- Отображение обложек (извлечение картинки из тега jaudiotagger -> `FlotControl`?)

### v1.2
- Спектральный анализатор (визуализация) - использовать TarsosDSP для FFT
- Crossfade между треками (два `SourceDataLine` + микширование)

### v2.0
- Серверный стриминг: синхронизация плейлистов через пакеты Forge Networking
- Поддержка радио URL

## 10. Тестирование

Ручной чеклист:
1. Запустить игру с модом, проверить создание папки `custommusic/` и `README.txt`
2. Поместить `test.mp3`, `test.wav`, `test.flac`, `test.ogg` (по одному каждого типа) в папку
3. Открыть игру, нажать M, нажать Обновить, убедиться что треки появились, разделены по артистам
4. Поиск - ввести часть имени, убедиться фильтр работает
5. Клик по треку - играет, NowPlaying обновляется, звук слышен
6. Пауза P, следующий N, предыдущий B
7. Режимы: SEQUENTIAL -> SHUFFLE -> REPEAT_ALL -> REPEAT_ONE, проверить
8. Положить много треков (100+) в подпапки, проверить скролл
9. Проверить громкость +/- 
10. Проверить команды `/custommusic open` etc.
11. Проверить DSF: поместить DSF, убедиться что парсер не крашит и выдает понятную ошибку, если `enableDSF=false` - пропускается

Автоматические тесты для аудио части можно написать в `src/test/java` с моком `AudioSystem`.

## 11. Известные ограничения

- MP3 VBR с обложкой может долго открываться через SPI
- FLAC 192kHz/24-bit конвертируется к 44.1kHz из-за фолбэк формата - в идеале сохранять исходную частоту
- Нет поддержки CUE sheets
- Нет эквалайзера
- На Linux может понадобиться настроить `pulseaudio` для доступа к `SourceDataLine` (использовать `alsa`)

---
Конец документации.
