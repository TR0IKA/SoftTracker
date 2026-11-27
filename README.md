# Lyrix 1.1.1 — обновление (только изменённые файлы)

Закинь папку `app` в корень репозитория **с заменой файлов** (GitHub → Add file → Upload files → перетащи папку `app`).
Удалять ничего не нужно.

| Файл | Куда | Что |
|---|---|---|
| `app/build.gradle.kts` | заменить | версия 1.1.1 |
| `app/src/main/AndroidManifest.xml` | заменить | встроенный плеер (сервис) |
| `app/src/main/java/com/lyrix/overlay/Library.kt` | **новый** | «Моя музыка» |
| `app/src/main/java/com/lyrix/overlay/PlayerService.kt` | **новый** | встроенный плеер |
| `.../Lyrics.kt`, `NowPlaying.kt`, `MediaListener.kt`, `Favorites.kt` | заменить | определение треков, ключ трека, запасной источник текстов |
| `.../LyricsView.kt` | заменить | текст сдвигается из зон, а не пропадает |
| `.../OverlayService.kt`, `MainScreen.kt`, `MainActivity.kt`, `DevScreen.kt`, `LyrixApp.kt` | заменить | интерфейс, обложки по ссылке, диагностика |

Заметка: избранное, добавленное в 1.1.0, сохранится. Свои .lrc, загруженные в 1.1.0, придётся загрузить заново.
