# CustomMusic Mod — MusicBee-стиль плеер для Minecraft 1.19.2 Forge 43.4.0

Клиентский мод, добавляющий полноценный музыкальный плеер в стиле MusicBee.
Играй свою музыку из папки `custommusic/` прямо в Minecraft, не заменяя ванильные треки.

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
- Горячие клавиши:
  - `M` — открыть плеер
  - `P` — пауза / играть
  - `N` — следующий трек
  - `B` — предыдущий трек
  - `← / →` — предыдущий / следующий
  - `Пробел` — пауза
- Команды:
  - `/custommusic open` — открыть плеер
  - `/custommusic folder` — показать путь к папке
  - `/custommusic rescan` — пересканировать библиотеку
- Не использует Minecraft OpenAL — воспроизведение через Java Sound (`SourceDataLine`)

## Установка

Требуется:
- Java 17+
- Minecraft 1.19.2
- Forge 43.4.0+

```bash
./gradlew shadowJar
# Windows: .\gradlew shadowJar
```

Готовый **fat jar** будет в:

```
build/libs/custommusic-1.0.0-all.jar
```

Его нужно скопировать в папку модов (`mods/`). **Важно:** использовать именно `custommusic-1.0.0-all.jar`, а не обычный `custommusic-1.0.0.jar`, иначе MP3/FLAC/OGG не заработают.

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

## Публикация / распространение

Мод можно свободно выкладывать, если соблюдены лицензии используемых библиотек.

### GitHub (рекомендую сначала)

Самый простой способ — создать репозиторий на GitHub, залить исходники и выпустить Release с `custommusic-1.0.0-all.jar`.

### CurseForge / Modrinth

Можно выложить и туда, но нужно:
- указать все сторонние библиотеки и их лицензии в описании проекта;
- убедиться, что fat jar включает лицензионные файлы (они уже есть в `THIRD_PARTY_LICENSES.md` и `LICENSE`).

## Лицензии

- Код мода — **MIT License** (см. `LICENSE`).
- Сторонние аудио-библиотеки — LGPL-2.1 / Apache-2.0 / BSD и их варианты (см. `THIRD_PARTY_LICENSES.md`).

## FAQ

**Q: Музыка не играет, ошибка "Unsupported audio format"?**
A: Скорее всего, используется не fat jar. Бери `custommusic-1.0.0-all.jar`.

**Q: FLAC 96 kHz / 24 bit не играл?**
A: В текущей версии плеер пытается использовать нативный формат, а если звуковая карта не поддерживает hi-res — даунсэмплит до 48/44.1 kHz 16-bit.

**Q: При открытии меню подвисает на большой библиотеке?**
A: Сканирование теперь фоновое. При открытии экрана может кратковременно показываться "Сканирование библиотеки…", но игра не замораживается.

**Q: Можно слушать музыку на сервере для всех игроков?**
A: Нет, плеер полностью клиентский. Для серверного вещания нужен отдельный мод.

Удачного прослушивания!

---

> Версия для **Minecraft 1.21.1 + NeoForge** лежит в ветке [`1.21.1-neoforge`](https://github.com/antonovichkorp1985-maker/custommusic-mod/tree/1.21.1-neoforge).
