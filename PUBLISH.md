# Как выложить мод (версия 1.21.1 NeoForge)

## 1. GitHub

1. Залей содержимое этой папки в репозиторий:
   ```bash
   git init
   git remote add origin https://github.com/antonovichkorp1985-maker/custommusic-mod.git
   git add .
   git commit -m "Port to Minecraft 1.21.1 NeoForge"
   git push -u origin main
   ```
   Или через кнопку **Add file → Upload files**.
   > Ветки: логично держать 1.19.2 Forge в `main`, а 1.21.1 NeoForge — в ветке `1.21.1-neoforge`
   > (или наоборот). Тогда в репозитории будут обе версии.

2. **Actions** → workflow `Build` соберёт jar и выложит его в артефакты
   (`custommusic-1.21.1-neoforge`). Раннер GitHub даёт 7 ГБ RAM, поэтому
   `createMinecraftArtifacts` там проходит без танцев с памятью.

3. **Releases → Create a new release**
   - Choose a tag: `v1.21.1-1.0.0` → **Create new tag on publish**
   - Release title: `CustomMusic 1.0.0 for Minecraft 1.21.1 (NeoForge)`
   - Описание:
     ```
     MusicBee-style local music player for Minecraft 1.21.1 + NeoForge 21.1.x.
     Supports MP3, WAV, FLAC (incl. 96/24), OGG. Requires Java 21.
     ```
   - **Attach binaries**: `build/libs/custommusic-1.21.1-1.0.0-all.jar`
     (или скачанный из Actions артефакт)
   - Отметь **Set as the latest release** → **Publish release**

## 2. Modrinth

1. https://modrinth.com → **Create a project** → тип `mod`.
2. Заполни:
   - Name: `CustomMusic Player`
   - Summary: `MusicBee-style local music player: play your own MP3/FLAC/OGG/WAV in Minecraft.`
   - License: **MIT** (+ упоминание LGPL/Apache/BSD библиотек, см. `THIRD_PARTY_LICENSES.md`)
   - Categories: `Utility`, `Equipment`? → лучше `Utility` / `Social`… используй `Utility`
   - Links: репозиторий GitHub, issues
3. **Upload version**:
   - Game version: `1.21.1`
   - Loader: **NeoForge**
   - File: `custommusic-1.21.1-1.0.0-all.jar`
   - Version number: `1.21.1-1.0.0`
   - Channel: `release`
4. Добавь скриншоты GUI (открой плеер клавишей `M` и сделай скриншоты).

## 3. CurseForge

1. https://authors.curseforge.com → **New Project** → Class: `Mods`.
2. Заполни название, описание, добавь скриншоты.
3. **Обязательно** укажи в описании сторонние библиотеки и их лицензии
   (MP3SPI/JLayer — LGPL, JOrbis/VorbisSPI — LGPL/BSD, jflac — BSD/Apache,
   jaudiotagger — LGPL/BSD). Файл `THIRD_PARTY_LICENSES.md` уже в репозитории.
4. Upload file:
   - Game versions: `1.21.1`
   - Мод-лоадер: **NeoForge**
   - File: `custommusic-1.21.1-1.0.0-all.jar`
   - Release type: `Release`
5. Дождись модерации (обычно несколько часов/дней).

## Важно

- Не выкладывай вместе с модом сами музыкальные файлы — только jar.
- Соблюдай лицензии сторонних библиотек (`LICENSE`, `THIRD_PARTY_LICENSES.md`).
- Для 1.19.2 Forge и 1.21.1 NeoForge — **разные jar**, не путай их в релизах.
  В названии релиза/версии всегда указывай версию Minecraft и лоадер.
