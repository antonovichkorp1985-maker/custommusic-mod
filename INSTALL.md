# Установка мода CustomMusic (Minecraft 1.21.1 / NeoForge)

## Для игрока

1. Установи **Minecraft 1.21.1** и **NeoForge 21.1.x** (через NeoForge-инсталлер или лаунчер).
2. Скачай **`custommusic-1.21.1-1.0.0-all.jar`** (именно `-all`, fat jar).
3. Закинь его в папку `mods/` (например `C:\Users\<ты>\AppData\Roaming\.minecraft\versions\<профиль>\mods\`).
4. Запусти игру один раз и закрой.
5. В корне игры появится папка `custommusic/`.
6. Скопируй туда музыку:
   ```
   custommusic/
     Artist/
       Album/
         01 - Track.mp3
     Another Artist - Track.flac
   ```
7. Запусти игру, нажми **M** (англ. раскладка) — откроется плеер. Если треки не появились сразу, нажми **Обновить**.

> В папке `mods/` не должно быть версии для 1.19.2 Forge (`custommusic-1.0.0-all.jar`) — они несовместимы.

## Для разработчика (сборка из исходников)

Требования: **JDK 21**, Git, интернет, ~3 ГБ свободной оперативной памяти.

```bash
git clone https://github.com/antonovichkorp1985-maker/custommusic-mod.git
cd custommusic-mod
./gradlew shadowJar       # Linux / macOS
.\gradlew shadowJar       # Windows PowerShell
```

Готовый файл: `build/libs/custommusic-1.21.1-1.0.0-all.jar`.

Если сборка упадёт с `OutOfMemoryError` на шаге `createMinecraftArtifacts`:

```powershell
# Windows PowerShell
$env:JAVA_TOOL_OPTIONS="-Xmx3G"
.\gradlew shadowJar
```

```bash
# Linux / macOS
JAVA_TOOL_OPTIONS=-Xmx3G ./gradlew shadowJar
```

## Горячие клавиши

- **M** — открыть плеер
- **O** — открыть плеер (вид «Артисты»)
- **P** — пауза / играть
- **N** — следующий трек
- **B** — предыдущий трек
- **← / →** — предыдущий / следующий (внутри плеера)
- **Пробел** — пауза (внутри плеера)

## Если что-то не работает

- Смотри `logs/latest.log`, ищи `[CustomMusic]`.
- Если пишет «Неподдерживаемый аудио формат» — скорее всего, используется не fat jar.
- Если экран открывается пустым — подожди, идёт фоновое сканирование большой библиотеки.
- Если игра не запускается и ругается на Java — нужен Java 21 (для 1.21.1).
