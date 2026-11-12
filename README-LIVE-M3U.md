# OwnTV — Live M3U build

This folder holds the **OwnTV** television app, trimmed to a live-TV / M3U-only player.

## The baseline

The current point for future work is:

| | |
|---|---|
| Branch | `live-only-m3u` (in `OwnTV/`) |
| Commit | `c9a800ae` — *Live: settings panel focus fixes; drop badge-size and window-padding options* |
| Tag | `live-m3u-2026-11-12` |
| APK | `OwnTV-Live-M3U-live-m3u-2026-11-12.apk` (34 MB, `armeabi-v7a`) |

`OwnTV-Live-M3U.apk` is a hard link to the same file — the same build under its everyday name.

To go back to this point:

```bash
cd OwnTV
git checkout live-m3u-2026-11-12
```

To rebuild the APK from it:

```bash
cd OwnTV
./gradlew :app:assembleStandardRelease -PowntvAbis=armeabi-v7a -Powntv.skipLint=true --max-workers=1
```

The television is a **32-bit ARM** box (`armeabi-v7a`), so that is the ABI the APK is built for.
`-Powntv.skipLint=true` turns off the lint gate: the analyser's analyser needs about 2 GB of heap,
which this phone does not have spare. CI never sets it and still gates on lint.

## What this build is

- Live TV only — M3U playlists. No movies, series, sports or downloads.
- AMOLED black; the left navigation rail can be hidden, leaving Settings and the Guide at the top right.
- A three-panel Live screen: categories, channel list, preview.
- Several playlists can be merged, and the categories can be merged by name or empty ones hidden.
- A typed channel number jumps to that channel; the digits show in the bottom-right corner.
- Programme details live in the third panel, under the picture.

## Layout

```
OwnTV/                       the app source (its own git repository)
OwnTV-Live-M3U*.apk          the built APK
dumbtv/                      an unrelated project, kept as it was
```
