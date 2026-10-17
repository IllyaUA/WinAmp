# WinAmp Classic for Android

A classic Winamp 2 style player for Android that plays **MP3**, **WAV** and **MIDI** files. Ogg, FLAC, M4A and AAC also work because Android decodes them with the same player.

The three windows (main player, equalizer, playlist) are drawn in code at the original 275 px width and scaled to your screen. No skin bitmaps are bundled.

## Features

- Main window: seven-segment clock (tap to switch between elapsed and remaining), scrolling title, kbps/kHz/mono/stereo indicators, 19-bar spectrum analyzer with peak markers, seek bar, volume, balance, previous/play/pause/stop/next/eject, shuffle, repeat.
- Playlist: add files, add a whole folder (scans subfolders), tap to play, long-press to select, REM to remove selected, CLR to clear. Saved between launches.
- Equalizer: uses the device's hardware equalizer, so you get the number of bands your phone offers (usually 5), with ON, FLAT and PRESETS.
- Plays in the background with a notification, lock screen controls and headset buttons. Pauses when headphones are unplugged and when another app takes audio focus.
- "Open with" from a file manager adds the file and plays it.

## Build

Needs JDK 17 and the Android SDK (platform 35).

```
./gradlew testDebugUnitTest      # logic tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

Pushes to GitHub build the APK automatically; download it from the workflow run's artifacts.

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Android only allows the spectrum analyzer (Visualizer API) for apps that hold it. The app never records. Deny it and everything works except the analyzer bars. |
| `POST_NOTIFICATIONS` | Playback notification on Android 13+. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Keep playing with the screen off. |

## Layout

- `core/`: plain Kotlin with no Android types (playlist order, shuffle, time formatting, spectrum math). Unit tested.
- `player/`: `PlayerEngine` (MediaPlayer, audio focus, equalizer, analyzer), `PlaybackService` (notification and media session), playlist storage and metadata reading.
- `ui/`: Compose drawing of the three windows.

## Known limits

- MIDI is rendered by Android's built-in synthesizer, so the sound depends on the device. Seeking inside a MIDI file can be coarse, and some devices report no duration until playback starts.
- The equalizer has no preamp slider; Android's equalizer API does not provide one.
- Files opened through "Open with" are granted temporarily by Android and may not reopen after the app restarts.
