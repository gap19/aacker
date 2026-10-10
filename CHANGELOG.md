# Changelog

## 0.1.0 — 2026-10-10

First release, for Wear OS 4 (API 33) or newer; built and tuned on a Galaxy Watch7 with Galaxy Buds3 FE.

- Library from the watch's media index: artists, albums and tracks, disc and track order, accent-insensitive sorting.
- Gapless background playback with Media3 reduced to AAC in MP4: audio renderer only, synchronous MediaCodec, dynamic scheduling, long read-ahead and AudioTrack buffers.
- Playback waits for Bluetooth earbuds and opens the watch's output switcher instead of using the speaker.
- Now playing with cover background, progress on the screen edge, bezel volume and a per-album accent colour.
- Lyrics from the M4A `©lyr` tag.
- Random album, album shuffle, queue and position restored after restarts.
- Battery saver: AAC offload to the audio chip where the route allows it, without gapless joins.
- English and Portuguese.
