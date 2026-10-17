# WinAmp Classic

A classic Winamp 2 style player for **Android**, **Windows** and **Linux**. It plays MP3, WAV and MIDI, and the desktop version also plays Ogg Vorbis and FLAC and loads real Winamp skins (`.wsz`).

## Download

Everything is on the [latest release](https://github.com/IllyaUA/WinAmp/releases/latest), rebuilt on every push to `main`.

| Platform | File | How to run |
|---|---|---|
| Android 8+ | `WinAmp-Classic.apk` | Open it on the phone and allow installs from your browser or file manager (debug-signed). |
| Windows 10/11 | `WinAmpClassic-windows-x64.zip` | Unzip, run `WinAmpClassic\WinAmpClassic.exe`. Java is bundled. |
| Linux x64 | `WinAmpClassic-linux-x64.tar.gz` | Extract, run `WinAmpClassic/bin/WinAmpClassic`. Java is bundled. |
| Anything with Java 17+ | `winamp-classic.jar` | `java -jar winamp-classic.jar` |

---

# Desktop (Windows and Linux)

Three windows that dock together: player, equalizer, playlist. Drag a window by its title bar; windows underneath it come along, and edges snap together (hold Shift to drag freely).

## Skins

The desktop app draws everything from skin bitmaps, the same way Winamp 2 does.

- **Load a skin:** right-click, Skins, Load skin file. Or drag a `.wsz` onto any window. Skins you load are copied to the skins folder and appear in the Skins menu.
- **Skins folder:** `%APPDATA%\WinAmpClassic\skins` on Windows, `~/.config/winamp-classic/skins` on Linux. Menu item "Open skins folder" shows it.
- **Make your own:** Skins, "Save built-in skin as template" writes a `.wsz` of the default skin that you can edit in any bitmap editor. The same thing from a terminal: `java -jar winamp-classic.jar --export-default-skin template.wsz`.
- A skin can be a `.wsz`/`.zip` file or a folder containing `main.bmp`. File names are matched without regard to case or sub-folders. Anything a skin leaves out falls back to the built-in skin.

Supported skin parts: `main`, `cbuttons`, `titlebar`, `numbers`, `playpaus`, `monostereo`, `volume`, `balance`, `posbar`, `shufrep`, `text`, `eqmain`, `pledit`, plus `viscolor.txt` (analyzer colours) and `pledit.txt` (playlist colours and font).

**Not supported yet:** shade (roll-up) mode, non-rectangular windows (`region.txt`), `nums_ex.bmp`, `genex.bmp`/modern skins, and the mini transport buttons in the playlist window.

> The sprite positions follow the classic Winamp 2.x skin layout. The built-in skin and the loader are tested against each other, but I could not download real third-party skins to test with where this was developed. If a skin looks wrong, send me the `.wsz` and a screenshot.

## Features

- **Formats:** MP3, WAV, MIDI, Ogg Vorbis, FLAC.
- **Player:** seven-segment clock (click to switch to remaining time), scrolling title, kbps/kHz/mono/stereo, 19-bar spectrum analyzer with peaks, seek, volume, balance, shuffle, repeat.
- **Equalizer:** real 10-band equalizer (60 Hz to 16 kHz) with preamp, ON switch, 18 presets and the response curve. Applies to MP3, WAV, Ogg and FLAC. MIDI is played by the synthesizer and is not equalized.
- **Playlist:** double-click to play, Ctrl/Shift to select several, Delete to remove, sort, reverse, randomize, open and save `.m3u`/`.m3u8`. Your playlist is restored on the next start.
- **Adding music:** the eject button or `L` plays files; ADD adds files or whole folders (sub-folders included, natural order so 2 comes before 10). You can also drag files, folders, playlists or skins onto any window, or pass them on the command line.
- **MIDI sound:** the built-in Java synthesizer is basic. For better sound, right-click, "Choose MIDI SoundFont", and pick any `.sf2` file.
- **Window size:** 1x to 4x (`D` toggles double size). The default is 2x on large screens.

## Keys

| Key | Action | Key | Action |
|---|---|---|---|
| `Z` / `X` / `C` / `V` / `B` | previous / play / pause / stop / next | `L` | play files |
| `Left` / `Right` | seek 5 s | `Shift+L` | add folder |
| `Up` / `Down` | volume | `S` / `R` | shuffle / repeat |
| `D` | double size | `Alt+E` / `Alt+G` | equalizer / playlist |

Right-click anywhere for the menu. Mouse wheel on the player changes volume.

## Build and test

Needs JDK 17 or newer.

```
./gradlew :desktop:test          # 80+ tests, no sound card needed
./gradlew :desktop:shadowJar     # desktop/build/libs/winamp-classic.jar
./gradlew :desktop:run           # start it
```

The tests run the real player against a fake sound card, decode real MP3/Ogg/FLAC samples, and render all three windows to images (`desktop/build/renders/`).

## Desktop limits

- Global media keys are not supported (Java cannot register them).
- On a machine without a sound device the player reports "no audio output device is available" and skips on.
- The analyzer shows the audio being played. For MIDI it reacts to the notes instead, because the synthesizer does not expose its audio.

---

# Android

A Winamp 2 style player drawn in code at the original 275 px width and scaled to the screen (no skin support on Android yet).

## Features

- Main window: seven-segment clock (tap to switch between elapsed and remaining), scrolling title, kbps/kHz/mono/stereo indicators, 19-bar spectrum analyzer with peak markers, seek bar, volume, balance, previous/play/pause/stop/next/eject, shuffle, repeat.
- Playlist: tap to play, long-press to select, REM to remove selected, CLR to clear. Saved between launches.
- File browser (ADD or the eject button): navigate folders, tick any number of files and folders, press ADD. A ticked folder adds every MP3/WAV/MIDI inside it, sub-folders included, in natural order. SYS opens Android's own picker as a fallback.
- Equalizer: uses the device's hardware equalizer, so you get the number of bands your phone offers (usually 5), with ON, FLAT and PRESETS.
- Plays in the background with a notification, lock screen controls and headset buttons. Pauses when headphones are unplugged and when another app takes audio focus.
- "Open with" from a file manager adds the file and plays it.

## Build

Needs JDK 17 and the Android SDK (platform 35).

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Android only allows the spectrum analyzer (Visualizer API) for apps that hold it. The app never records. Deny it and everything works except the analyzer bars. |
| `POST_NOTIFICATIONS` | Playback notification on Android 13+. |
| All files access (`MANAGE_EXTERNAL_STORAGE`) | Lets the file browser list folders by path. Android asks you to flip a switch in settings. Without it the browser stays empty and only SYS (Android's picker) works. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Keep playing with the screen off. |

## Android limits

- MIDI is rendered by Android's built-in synthesizer, so the sound depends on the device. Seeking inside a MIDI file can be coarse.
- The equalizer has no preamp slider; Android's equalizer API does not provide one.
- Files opened through "Open with" are granted temporarily by Android and may not reopen after the app restarts.

---

# Layout of the code

- `app/`: the Android app (`player/` engine and service, `ui/` Compose drawing).
- `desktop/`: the desktop app (`audio/` player, `skin/` loader and built-in skin, `ui/` windows).
- `app/src/main/java/com/illyaua/winamp/core/`: plain Kotlin shared by both apps (playlist order, shuffle, equalizer and FFT, spectrum math, directory scanning, M3U). Unit tested.
