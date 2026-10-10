<p align="center"><img src="docs/icon.svg" width="112" alt="AACker icon"></p>
<h1 align="center">AACker</h1>
<p align="center">Your record collection, offline — on your wrist.</p>

AACker is a native Wear OS music player for personal AAC libraries, written in Kotlin with Compose for Wear OS (Material 3) and Media3 (ExoPlayer). It is the watch companion of [FLACker for Linux](https://github.com/gap19/flacker-linux) and [FLACker for Android](https://github.com/gap19/flacker-android): albums come first, play in their intended order, and go to your Bluetooth earbuds while the watch sleeps.

It was built and tuned on a Galaxy Watch7 with Galaxy Buds3 FE, for `.m4a` (AAC-LC) copies of a FLAC collection.

**Fully offline. No accounts, telemetry, ads or internet permission. Your music is only read — AACker never writes to it.**

## Features

- **Library:** artists → albums → tracks, straight from the watch's media index; no folders to pick and nothing to scan. Albums are grouped by folder and title, so equal titles never merge, and `CD1`/`Disc 2` subfolders join their album. Accent-insensitive sorting in the watch's language. An artist with a single album opens it directly.
- **Album order:** disc and track number from the tags, with disc headers on multi-disc albums.
- **Playback:** gapless background playback with the system media controls and earbud buttons; play an album, shuffle it, or let the edge button pick a **random album**. The queue, track and position survive restarts; restoring never starts sound by itself.
- **Earbuds first:** with no Bluetooth audio connected, playback waits and the watch's output switcher opens — music never comes out of the watch speaker.
- **Now playing:** the cover fills the round screen, progress runs along its edge, and the rotating bezel sets the volume. Each album gets its own accent colour, derived from its cover.
- **Lyrics:** read from the M4A `©lyr` tag; LRC timestamps are hidden, nothing is synchronized.
- **Languages:** English and Portuguese, following the watch.

## Battery and performance

AACker is designed to do as little as possible per second of music:

- The library is one query to the system media index; audio files are never opened to list them.
- Covers are extracted once per album from the embedded picture; 128 px thumbnails are cached.
- Media3 is reduced to what local AAC needs: only the audio renderer and the MP4 extractor (without artwork parsing), synchronous MediaCodec, dynamic scheduling of the playback loop, up to five minutes read ahead and two seconds of PCM in the AudioTrack, so the CPU works in bursts and sleeps in between.
- True black (OLED pixels off) and no continuous animation; the player redraws once per second, only while the screen is visible. A paused player with the app out of sight stops its service.
- **Battery saver** (Settings) decodes on the watch's audio chip when the route allows it. On the Galaxy Watch7 over Bluetooth, AAC offload is available only without gapless joins, so songs may have a short pause between them; it is off by default.

The release APK, minified with R8, is about 3.5 MB.

## Getting music onto the watch

Wear OS watches have no USB file transfer. Copy your `.m4a` files over Wi-Fi debugging (Settings → About watch → Software → tap *Software version* five times; then Developer options → *ADB debugging* and *Wireless debugging*):

```sh
adb pair WATCH_IP:PAIRING_PORT PAIRING_CODE
adb connect WATCH_IP:PORT
adb push --sync /path/to/aac/. /sdcard/Music/
adb shell content call --method scan_volume --uri content://media --arg external_primary
```

`--sync` sends only new or changed files, so the same command resumes an interrupted copy or adds new albums. The last line asks the watch to index the music; restarting the watch does the same.

Recommended files: AAC-LC in `.m4a`, 44.1 kHz stereo, with track and disc numbers (`N/total`), album artist and a JPEG front cover embedded.

## Install

Requires Wear OS 4 (API 33) or newer. Build the APK (below) and install it over the same connection:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

Open AACker and allow access to music when asked.

## Build from source

Requirements: JDK 17 or newer and the Android SDK (platform 37, build-tools 36). Gradle is fetched by the wrapper.

```sh
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:testDebugUnitTest :app:lintRelease   # unit tests and lint
./gradlew :app:assembleRelease                      # minified with R8
```

To sign release builds, create `keystore.properties` next to `settings.gradle.kts` (it is git-ignored):

```properties
storeFile=/path/to/your-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, `assembleRelease` signs with the debug key, which is enough to install on your own watch.

`scripts/android-env.sh` points the build at a self-contained JDK, SDK and Gradle in `.build/android` (git-ignored), if you keep them there; `scripts/install.sh [IP:PORT]` tests, lints, builds and installs in one step.

## Your data

The queue, position and modes are kept in `queue.json`, written with `AtomicFile`; the battery saver choice in the app's preferences; extracted covers in the app's cache. Everything stays in the app's private storage and is excluded from backup.

## Disclaimer

AACker is shared in the hope that it will be useful to others who enjoy listening to their own music collections. It is provided "as is", without warranty of any kind, express or implied, including any warranty of merchantability or fitness for a particular purpose. The author is not liable for any damage or loss arising from its use, including to files, devices or data. Using, building, installing or modifying this software is at your own risk and under your own responsibility. Keep backups of anything that matters to you. See sections 15 and 16 of the [license](LICENSE).

## License

AACker is free software under the [GNU General Public License v3.0 or later](LICENSE). The bundled League Gothic and Source Sans 3 fonts are under the SIL Open Font License 1.1 (`app/src/main/assets/licenses/`). AndroidX, Compose for Wear OS, Media3 and Kotlin coroutines are under the Apache License 2.0. The license grants no rights over the music, lyrics or images in your library.
