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
- Versions: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, minSdk 29 (Android 10; owner's decision), targetSdk/compileSdk 35, package `app.uilauncher`.

## Structure (`app/src/main/java/app/uilauncher/`)
- `MainActivity.kt` — HOME activity; `onNewIntent` handles the home button.
- `LauncherViewModel.kt` — all state + actions (layout, settings, drawer/settings navigation).
- `data/AppRepository.kt` — installed apps via `LauncherApps`, icon cache, launching.
- `data/IconPacks.kt` — icon packs: `IconPacks.installed()` finds packs (ADW/Nova/Apex/GO actions, listed in the manifest `<queries>`), `IconPack` reads `appfilter.xml` (exact icon per activity, then per package; `wrap()` themes apps the pack lacks using iconback/iconmask/iconupon/scale). The chosen pack (`Keys.iconPack`, one choice for home+dock+drawer) is applied in `AppRepository.loadIcon`; changing it clears the cache and bumps `iconVersion`; `AppIcon` reloads on `(app.key, iconVersion)` via LaunchedEffect (never gate the reload on the current bitmap being null: icons already on screen must refresh).
- `data/IconShapes.kt` — `IconStyle(pack, shape, corner)` (one style for home+dock+drawer) and `IconShapes.apply()` that re-cuts the apps' own icons to circle / rounded square (corner %) / squircle (adaptive icons: both layers are redrawn and clipped; old-style icons go smaller on a white backdrop). Pack icons are never re-shaped. `AppRepository.setIconStyle()` applies it; `Applied` holds style+pack as one object.
- `data/CustomIcons.kt` — per-app icons the user picked, saved as PNG in `filesDir/custom_icons` (name = SHA-1 of the app key; `.s.png` = gallery image that follows the chosen icon shape, plain `.png` = icon taken from a pack, kept as is). They win over pack/shape in `AppRepository.loadIcon`.
- `ui/IconEditor.kt` — full-screen editor opened from "Edit icon" in the long-press menus (home, dock, drawer): pick from the applied pack (searchable grid), pick from the gallery (system photo picker, no permission) then square-crop (drag/pinch), or restore the original. State: `vm.iconEditApp`.
- `data/HomeLayout.kt` — pages / items / dock model, JSON persistence, `fit()` when the grid shrinks. Folders: `Folder(id,name,apps)` list in the layout (JSON key `folders`); a folder's icon is a `HomeItem` whose key is `FOLDER_PREFIX + id`. `mergeInto/dropAppOn` (drop on an occupied cell creates/joins a folder), `takeOut`, `disband`, `rename`; empty folders vanish. Folders are home-screen only (not in dock/drawer yet), not nested.
- `ui/Folders.kt` — `FolderItem` (2x2 mini icons) and `FolderOverlay` popup (editable name, app grid, long-press menu). State: `vm.openFolderId`.
- `data/Settings.kt` — DataStore keys + `LauncherSettings`. New option = key + field + `toSettings()` + one row in a settings screen.
- `ui/HomeScreen.kt` — pager grid, dock, ONE custom gesture (tap / long-press drag / swipe up) with cell math.
- `ui/DrawerScreen.kt` — app drawer (vertical grid or horizontal pages), search, app menu. Each icon is ONE gesture: tap = launch, long press = menu, long press + move = drag out (menu closes, drawer turns invisible but stays composed so it keeps the finger; state in `LauncherViewModel.dragApp/dragPos/drawerHiddenForDrag`).
- `ui/DragOverlay.kt` — the icon that follows the finger while dragging out of the drawer. `HomeScreen.kt` (grid) and `Dock` listen to `vm.dropEvents` and each handles drops inside its own window bounds; the grid flips pages at the screen edges.
- Wallpaper: no own picker; `vm.openWallpaperPicker()` opens the system chooser (ACTION_SET_WALLPAPER) from the home empty-space menu and Settings > Home screen. Status/nav bar hiding: `Keys.hideStatusBar/hideNavBar`, applied in `LauncherRoot` via WindowInsetsController (transient bars on swipe).
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
- Phase 1 done and tested by Khaled (home, dock, drawer, settings, drag from drawer to home/dock).
- Phase 2 coded: icon packs, icon shape/corner and per-app icon editor are CONFIRMED working by Khaled. Folders (home only; dock folders + drag-out from popup deferred) and wallpaper picker + hide status/nav bar are built (build OK) but NOT yet tested by him. Next is Phase 3 (real widgets) once he says to start.
- Full approved feature list (17 sections) and phase order 2-6 were agreed with him; cloud sync, online theme download and floating widgets are deferred.
