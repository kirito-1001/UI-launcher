# UI launcher — project notes for Claude

Android launcher (inspired by Nova Launcher) written from scratch in Kotlin + Jetpack Compose.
Owner: Khaled. He has only a phone (no computer) and wants Claude to do the coding. APKs are built by GitHub Actions.

## Working rules (from the owner)
- Agree the full change list with him first; do not start editing until he says he is done explaining.
- Commit directly to `main` (no branches).
- Do NOT touch `README.md` until development is finished.
- Keep this file updated at the end of every task.
- Talk to him in Arabic.

## Build
- No local Android SDK/network for Maven in the Claude container, so the project cannot be compiled there.
  Push to `main`; `.github/workflows/build.yml` builds the APK and publishes it to the `latest` release.
- The workflow also writes the errors/summary of every build to the `build-log` branch (`build-log.txt`).
  Read it with `git fetch origin build-log && git show origin/build-log:build-log.txt`.
- Signing uses the committed `app/debug.keystore` so each new APK installs over the previous one.
- Versions: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, minSdk 26, targetSdk/compileSdk 35, package `app.uilauncher`.

## Structure (`app/src/main/java/app/uilauncher/`)
- `MainActivity.kt` — HOME activity; `onNewIntent` handles the home button.
- `LauncherViewModel.kt` — all state + actions (layout, settings, drawer/settings navigation).
- `data/AppRepository.kt` — installed apps via `LauncherApps`, icon cache, launching.
- `data/HomeLayout.kt` — pages / items / dock model, JSON persistence, `fit()` when the grid shrinks.
- `data/Settings.kt` — DataStore keys + `LauncherSettings`. New option = key + field + `toSettings()` + one row in a settings screen.
- `ui/HomeScreen.kt` — pager grid, dock, ONE custom gesture (tap / long-press drag / swipe up) with cell math.
- `ui/DrawerScreen.kt` — app drawer (vertical grid or horizontal pages), search, app menu. Each icon is ONE gesture: tap = launch, long press = menu, long press + move = drag out (menu closes, drawer turns invisible but stays composed so it keeps the finger; state in `LauncherViewModel.dragApp/dragPos/drawerHiddenForDrag`).
- `ui/DragOverlay.kt` — the icon that follows the finger while dragging out of the drawer. `HomeScreen.kt` (grid) and `Dock` listen to `vm.dropEvents` and each handles drops inside its own window bounds; the grid flips pages at the screen edges.
- `ui/SettingsScreens.kt`, `ui/SettingsComponents.kt` — Nova-style settings (main list of sections, simple options first).
- Strings: `res/values/strings.xml` (English) and `res/values-ar/strings.xml` (Arabic) — keep both in sync.

## Decisions
- Settings are independent per section (home / drawer / dock).
- Live wallpapers are shown by the system behind the launcher (window shows wallpaper).
- Widgets (later phase) are placed on grid cells like Nova, via AppWidgetHost/AppWidgetManager.
- Drawer scroll direction (vertical/horizontal) is a user setting. Drawer tabs: manual + automatic, user chooses (later phase).

## Roadmap
1. (current) Foundation: home pages + grid + drag, dock, drawer + search + hide, settings, light/dark/dynamic color.
2. Folders, icon packs (ADW/Nova appfilter.xml), per-app icon, adaptive icons, icon shape/radius, wallpaper, hide status/nav bar.
3. Real widgets (AppWidgetHost).
4. Drawer tabs, transition effects, gestures, notification badges, backup/restore, lock layout, advanced drawer search.
5. Deferred: dock pages, external themes, the owner's own new features (he will describe them later).

## Status
- Phase 1 code written and pushed; waiting for the first successful GitHub Actions build and on-device testing by Khaled.
