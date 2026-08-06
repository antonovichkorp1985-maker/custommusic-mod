# MusicBee-стиль GUI — изменения

## Что сделано

Переработан интерфейс мода CustomMusic под стиль MusicBee:
- Единый экран `MusicBeeScreen` вместо двух разных (`MusicPlayerScreen` + `FolderBrowserScreen`).
- Раскладка:
  - **Слева** — дерево библиотеки (папки / исполнители / альбомы / все треки).
  - **Центр** — таблица треков с сортируемыми колонками: №, Название, Исполнитель, Альбом, Год, Рейтинг, Время, Формат.
  - **Справа** — очередь воспроизведения сверху, информация о текущем треке снизу.
  - **Снизу** — панель управления: назад/играть/вперёд, режим, полоса прогресса, громкость.
- Вверху добавлены кнопки переключения вида: Папки / Артисты / Альбомы / Все.
- Работает поиск, сортировка по заголовкам колонок, прокрутка колёсиком, горячие клавиши.

## Изменённые файлы

- `src/main/java/com/obninsk/custommusic/gui/MusicBeeScreen.java` — новый экран (основной).
- `src/main/java/com/obninsk/custommusic/client/ClientEvents.java` — клавиши M и O открывают `MusicBeeScreen`.
- `src/main/java/com/obninsk/custommusic/client/ClientCommands.java` — команда `/custommusic open` открывает `MusicBeeScreen`.
- `src/main/java/com/obninsk/custommusic/music/AudioPlayerManager.java` — добавлен трекинг текущего времени воспроизведения и прогресса.
- `src/main/java/com/obninsk/custommusic/music/Track.java` — добавлены поля `trackNumber`, `rating`, `bitrate`.
- `src/main/java/com/obninsk/custommusic/music/MetadataParser.java` — читаются номер трека, рейтинг, битрейт из тегов.
- `src/main/java/com/obninsk/custommusic/music/MusicLibraryManager.java` — вспомогательные методы для статистики и формата времени.

## Старые экраны

`MusicPlayerScreen.java` и `FolderBrowserScreen.java` оставлены в проекте, но больше не используются. При желании их можно удалить.

## Сборка

Обычный slim jar (только код мода):
```bash
cd custommusic-mod-musicbee
./gradlew build
```

Fat jar со всеми аудио-декодерами (MP3/FLAC/OGG) — используй именно его для игры:
```bash
./gradlew shadowJar
```

Готовый fat jar появится в:
```
build/libs/custommusic-1.0.0-all.jar
```

Его и клади в `mods/`. Slim jar (`custommusic-1.0.0.jar`) будет играть только WAV.

Fat jar уже исключает конфликтующие `commons-io` и `slf4j`, а также мержит `META-INF/services` для SPI.
