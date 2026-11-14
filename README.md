# tvlauncher

A minimal Android TV / Google TV home screen: one centred dock, an App library, weather, exchange rates and a news
ticker. Built and used on a TCL 65C8K (G08, Google TV 14), Android 8.0 (API 26) or newer.

Originally derived from [CleanYourTV](https://github.com/Gathin23/cleanyourtv) 3.4 (MIT); now its own project under
the GPL v3 (see `LICENSE` and `NOTICE`).

## Install

Download `tvlauncher-<version>.apk` from [Releases](../../releases), then over ADB:

```bash
adb install -r tvlauncher-<version>.apk
adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
```

On Google TV the stock home (`com.google.android.apps.tv.launcherx`) and the setup wizard
(`com.google.android.tungsten.setupwraith`) also claim Home; disable them with `pm disable-user --user 0` once this
launcher works (the wizard deletes the Wi-Fi network it saved during setup). The remote-button helper is an
accessibility service: enable "Главный экран / Home screen" in Settings > Accessibility.

## Build

JDK 17 and Android SDK platform 34: `cd launcher && ./gradlew assembleRelease`. Without signing variables the debug
key signs. Releases are built by `.github/workflows/release.yml` on a `v*` tag.

## Built-in defaults

The APK ships set up for daily use, with no personal data: the launcher's own keys default on
(`res/values/fork_defaults.xml`) and `config.xml` holds the rest. A theme's `config.json` still overrides each one.

- `home_dock_only`, `library_button_left`, `tile_menu_close`, `show_weather` (no city until one is picked),
  `show_nbg_rates`, `show_news` (World) on; `menu_opacity` 70; dock band opacity 40 (`fork_dock_opacity`).
- 7 columns, no rows below the dock, frosted-glass dock with even padding, icon-only buttons, App library button on,
  no ambient/app glow, white header, clock over the wallpaper.
- Dock: YouTube, Netflix, Prime Video, Disney+, Spotify, YouTube Music, Plex (missing apps are skipped).
- TCL remote: gear → quick panel (83), YouTube (190), Input → Inputs panel over apps (178), TV → the hardware tuner (4001,
  `app_keys` action `tuner`, found on any TV). Live TV app `com.tcl.tv`.
- No wallpapers are bundled; a theme adds them (`wallpaper_groups`).

The launcher uses `INTERNET` (weather, rates, news) and the TV's own ADB for "Close".

## Theme keys

| Key | What it does |
|---|---|
| `home_dock_only` | Home is one centred dock: only `dock_apps` and apps added with "To dock". The dock holds as many apps as there are columns; lowering the column count moves the rightmost apps back to the App library. The settings page shows sliders for columns and dock transparency, and drops "Rows below the dock", "Default home" and "TV settings". |
| `library_button_left` | The App library button first in the top bar, left of Inputs. |
| `menu_opacity` (30-100) | Opacity of the glass menus (Inputs, quick settings panel, App library, tile menu). |
| `tile_menu_close` | Tile menu reads Move, Close, Remove, App info. Close force-stops the app through the TV's own ADB (127.0.0.1:5555); needs USB/network debugging on and the launcher's key allowed. Without ADB it opens App info. |
| `show_weather` | Weather under the clock from Open-Meteo (no key); city and forecast length (now, today, 3 days, week) in Launcher settings. |
| `show_nbg_rates` | National Bank of Georgia USD and EUR rates next to the greeting, green/red arrow for the daily change. |
| `show_news` | A ticker under the dock with the top 10 Google News headlines (Russian edition). Section, text size, speed and the band's transparency in Launcher settings. Sections: World, Top stories, Georgia (a one-day search), Business, Technology, Science, Sports, Entertainment, Health. Text size (16-40sp), scroll speed (20-200 dp/s) and the transparency of its dark rounded band (0-100%) are sliders in Launcher settings. |
| `wallpaper_groups` (`["id:Label", ...]`) | Launcher settings offers wallpaper groups instead of single pictures; pictures are `wallpapers/<id>-*.jpg`, a group rotates only its own pictures, "Random" rotates all. Up to 300 pictures. |

## App keys

`app_keys` also accepts `inputs_overlay`: the Inputs list as a compact translucent panel over the app on screen,
without going Home (e.g. `"178:inputs_overlay"` for the TCL Input button).

## Remote buttons

Launcher settings has a "Remote buttons" section: Netflix (4062), Prime Video (4099), YouTube (190), TCL Channel (4020)
and Live TV (4001), measured on a TCL G08 remote. Each can open any TV app, the Inputs panel, Live TV (the hardware
tuner), the quick settings panel, nothing, or keep its default. The choice wins over the theme's `app_keys` and is
read on every press by the key helper (the accessibility service).

The microphone button cannot be remapped while Google Assistant is enabled: the remote's BLE service hands the voice
session straight to the Assistant and no key event reaches Android. With the Assistant disabled it arrives as
`KEYCODE_SEARCH`, but without voice.

## Other changes

- With `home_dock_only`, the on-screen gear opens the same quick settings panel as the remote's gear.
- The Inputs panel over apps copies the Home dropdown: under the Inputs pill (its position is remembered from Home),
  as wide as the pill but at least 150dp and up to 360dp, same rows and drop-in animation.
- Quick settings panel: "System" and "Home screen" first.
- Smaller focused-app title on Home (38sp).
- `adbkey.import` through the theme provider: hands LocalAdb a key the TV already trusts (taken in, then deleted).

## Network use

Only while Home is on screen: weather every 30 min, rates every 3 h, news every 30 min; last answers are cached.
Sent: the typed city name (Open-Meteo geocoding), then its coordinates; nothing else identifies the TV.
