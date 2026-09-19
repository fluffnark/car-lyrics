# Car Lyrics: feasibility and build plan

Research checked 2026-09-19. Target hardware: connected Pixel 7a (Android 17), with Spotify and YouTube Music installed. The sibling `car-dashboard` repo is a useful reference for Android tooling, the Desktop Head Unit, and on-device testing. It has unrelated local modifications in release artifacts, which were left untouched.

## Product goal

Show synchronized, readable karaoke lyrics for music the user starts in an existing music app. Start with an on-phone, parked-use experience. Do not assume that a separate lyric app can take over the Android Auto media screen or display karaoke text while driving.

## Findings that shape the design

| Route | Finding | Decision |
| --- | --- | --- |
| Android media sessions | An enabled notification listener can obtain active media sessions; `MediaController` exposes track metadata and playback state. This may provide title, artist, and timing from either installed player without a Spotify or YouTube developer account. Actual metadata quality and timing must be tested on this phone. [Android MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager), [MediaController](https://developer.android.com/reference/android/media/session/MediaController) | First integration path. Ask for notification access only when the user enables music detection; process supported music sessions locally. |
| Spotify Web API | A currently playing endpoint exists, but the Web API requires Premium, and development-mode apps require the owner to have Premium. The public track endpoint exposes an explicit-lyrics flag, not lyric lines; no documented lyrics endpoint was found in the public API reference. [Spotify Web API](https://developer.spotify.com/documentation/web-api), [current track](https://developer.spotify.com/documentation/web-api/reference/get-the-users-currently-playing-track), [track](https://developer.spotify.com/documentation/web-api/reference/get-track) | Do not depend on Spotify OAuth for the first version. Spotify plays music in its own app. |
| YouTube Music and free tier | YouTube Music supports Android Auto, but Google lists background play and audio-only mode as Premium benefits. Free-tier music behavior while projected needs a device test; podcasts have different rules. The YouTube Data API does not offer a YouTube Music now-playing or general lyrics endpoint. [YouTube Music on Android Auto](https://support.google.com/youtubemusic/answer/9231765), [background play](https://support.google.com/youtubemusic/answer/6313552), [YouTube Data API](https://developers.google.com/youtube/v3/docs) | Try the installed YouTube Music app on the phone and in the Desktop Head Unit. Treat free-tier music playback as uncertain until observed. |
| YouTube karaoke videos | YouTube's API policy prohibits separating audio/video components and background players; downloadable captions require permission to edit the video. A lyric-only replacement for someone else's karaoke video is therefore not a viable API shortcut. [YouTube API policies](https://developers.google.com/youtube/terms/developer-policies), [caption downloads](https://developers.google.com/youtube/v3/docs/captions/download) | Do not embed, strip, scrape, or re-render YouTube videos or captions. |
| Lyric source | Music player metadata does not contain usable synchronized lyrics. Musixmatch has a licensed lyrics API, including synchronized subtitle support, but access and display rights need explicit provider terms. [Musixmatch SDK](https://github.com/musixmatch/musixmatch-sdk) | Prototype with user supplied `.lrc` files or lyrics we have permission to use. Investigate a licensed provider after the timing prototype works. |
| Car display | Android Auto supports audio media apps, but its media category excludes video. Video apps are an Android Automotive OS parked category, not projected Android Auto. Car quality rules also prohibit automatically scrolling text in relevant driving categories. The available categories do not establish a path for a standalone karaoke lyric screen while driving. [Android for Cars categories](https://developer.android.com/training/cars), [car app quality](https://developer.android.com/docs/quality-guidelines/car-app-quality) | No lyric surface while driving. Do not reuse the dashboard's POI/map service for lyrics. Revisit only if Google documents a suitable supported category and behavior. |

## Milestones

### 1. Media-session spike on the Pixel

- Create a minimal Android phone app with an explicit notification-access setup flow.
- Observe active `MediaController` sessions for Spotify and YouTube Music; show app, title, artist, duration, position, play/pause, and source timestamp in a debug screen.
- Confirm callbacks on track change, seek, pause, resume, ads, and app switching. Reject stale sessions and account for playback speed and elapsed realtime.
- Test with the Desktop Head Unit and with the car if available. Record whether free YouTube Music actually plays music during projection.

**Gate:** at least one player provides stable track identity and position across those transitions. If YouTube Music does not, retain Spotify support and a manual track/offset mode.

### 2. Local timed-lyrics prototype

- Import a user provided `.lrc` file through Android's document picker. Parse timestamps and optional song metadata without copying external song lyrics into the repository.
- Match normalized title and artist; require confirmation for ambiguous matches. Provide a manual time offset and a clear unmatched state.
- Render one or two large lines with a static change on each timestamp. Use a phone UI intended for use while parked; include pause, resume, seek, and orientation checks.

**Gate:** a known test LRC stays within roughly 500 ms of audible playback after seek, pause, and reconnect, with no lyric jump during ads or metadata gaps.

### 3. Real catalog and rights decision

- Compare a licensed synchronized-lyrics provider's coverage, pricing, attribution, offline caching, and car-display rights against a local-file-only release.
- Keep provider adapters separate from session tracking and the LRC renderer. Do not use undocumented Spotify or YouTube endpoints.

**Gate:** source terms explicitly permit the intended display and distribution before fetching copyrighted lyric text in an app release.

### 4. Car product decision

- Validate current Android Auto categories and Play review requirements again at implementation time.
- If no supported lyric display category exists, keep the product as a parked phone companion. A future native Android Automotive OS parked app would be a separate target requiring compatible car hardware.
- If the goal is an Android Auto audio app, implement a genuine audio catalog and playback service using `MediaLibraryService`/`MediaSession`; it would still use the host's media UI rather than a freeform karaoke screen.

**Gate:** no release labeled as Android Auto lyric support until the UI has a legitimate supported category and passes distraction requirements.

## First implementation slice

The next coding step is milestone 1 only: a debug APK that displays the active music session on the phone. It needs no API keys, backend, lyric catalog, or copied code from the dashboard. Build and install it on the connected Pixel, then record results for both players before choosing the full app architecture.

## Open product choice

Should this optimize for **parked sing-along on the phone**, or is **lyrics on the car's Android Auto screen** essential? Current published Android Auto APIs do not show a supported path for the latter while driving, so that choice determines whether the phone prototype is a useful end product or only a feasibility experiment.
