# Как выложить мод

## 1. GitHub (быстрее всего)

1. Зарегистрируйся / войди на https://github.com.
2. Создай новый репозиторий, например `custommusic-mod`.
3. Загрузи содержимое этой папки в репозиторий:
   ```bash
   git init
   git remote add origin https://github.com/ТВОЙ_НИК/custommusic-mod.git
   git add .
   git commit -m "Initial commit"
   git push -u origin main
   ```
   Или просто через кнопку "Add file" → "Upload files".
4. Перейди во вкладку **Actions** → разреши workflows, если спросит.
5. Сделай релиз:
   - **Releases** → **Create a new release**.
   - Укажи тег, например `v1.0.0`.
   - Загрузи `custommusic-1.0.0-all.jar` (можно собрать локально или скачать из артефакта GitHub Actions).

## 2. CurseForge

1. Регистрируйся на https://www.curseforge.com.
2. Создай проект Minecraft → Mods.
3. Заполни описание, добавь скриншоты.
4. **Обязательно** укажи в описании, что мод использует LGPL/Apache/BSD библиотеки (см. `THIRD_PARTY_LICENSES.md`).
5. Загрузи `custommusic-1.0.0-all.jar`.
6. Дождись модерации.

## 3. Modrinth

1. Регистрируйся на https://modrinth.com.
2. Создай проект, выбери `mod`, версию `1.19.2`, загрузчик `Forge`.
3. Загрузи `custommusic-1.0.0-all.jar`.
4. Укажи лицензию MIT и прикрепи `THIRD_PARTY_LICENSES.md`.

## Важно

- Не выкладывай вместе с модом сами музыкальные файлы — только jar.
- Соблюдай лицензии сторонних библиотек (файлы `LICENSE` и `THIRD_PARTY_LICENSES.md` уже в проекте).
