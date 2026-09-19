# Car Lyrics: Mazda car-screen prototype plan

Updated 2026-09-19 after confirming the target: lyrics must appear on the 2021 Mazda CX-5 display and all app controls must work with its Commander knob. The connected Pixel 7a runs Android 17 and has Spotify and YouTube Music installed. Spotify Premium is available. This is a separate repository from `car-dashboard`; its unrelated local changes were left untouched.

## Goal and working architecture

Spotify continues to play through its official Android Auto app. Our phone app reads the active Spotify track and playback position, matches a timed lyric file, and sends the current line to an Android Auto `CarAppService`. A `MapWithContentTemplate` surface draws large, static lyric lines inside the host's visible area. Host-rendered action-strip buttons and rows handle every operation the user needs in the car, so the Mazda knob can focus and activate them. Phone setup is done before connecting or while parked.

This is a **private technical prototype path**, demonstrated in part by `car-dashboard`. It does not establish that a lyrics-only POI app satisfies Google Play's car category rules.

## What Spotify Premium changes

| Option | What it provides | What it does not provide |
| --- | --- | --- |
| Android media session | With user-granted notification access, Android can expose the active Spotify session's metadata and playback state/position. This requires no Spotify API credentials and is the first path to test. [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager), [MediaController](https://developer.android.com/reference/android/media/session/MediaController) | Lyrics or a guarantee that Spotify sends precise metadata on this device. |
| Spotify App Remote | Spotify's Android SDK can subscribe to `PlayerState`, including track, pause state, speed, and position. It requires user authorization and can serve as a timing fallback or comparison. [Android SDK](https://developer.spotify.com/documentation/android), [PlayerState](https://spotify.github.io/android-sdk/app-remote-lib/docs/com/spotify/protocol/types/PlayerState.html) | Lyric lines or lyric rights. |
| Spotify Web API | A Premium account satisfies the current development-mode account requirement. `GET /me/player/currently-playing` returns track and progress data, but involves OAuth, polling, and quota limits. [Quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes), [currently playing](https://developer.spotify.com/documentation/web-api/reference/get-the-users-currently-playing-track) | A documented public synced-lyrics endpoint. The track API's `explicit` field only flags explicit content. |

**Answer:** Spotify Premium is sufficient to try official playback-state integrations; it does not make Spotify lyrics available to this app. The simplest first implementation is to let Spotify play and read its Android media session. We should compare App Remote only if that session's timing proves inadequate.

Spotify's [developer policy](https://developer.spotify.com/policy) also prohibits synchronizing sound recordings with visual media. Whether an independently sourced lyric display falls within that prohibition is a rights/policy question; Premium does not grant an exception. Resolve this before any release that uses Spotify's developer platform.

## Car-screen and knob design

`car-dashboard` already uses `AppManager.setSurfaceCallback`, `MapWithContentTemplate`, a small `PaneTemplate`, and host-rendered persistent actions. Its trip-list rows and action buttons receive Commander focus and clicks. The custom Canvas surface itself is artwork, not a focusable knob interface. Reuse the *pattern* in new code: lyrics on the surface; interactive controls in host templates.

The first car screen should contain the current track and at most two large lyric lines, changing discretely at line boundaries. Its host controls should be **Lyrics on/off**, **Offset −**, **Offset +**, and **Source/Match** where template limits allow; use a knob-operable list or pane for less frequent choices. Avoid touch-only hit targets, continuous scrolling, video, and bouncing-word animation. Verify legibility and focus on the actual CX-5 display, including reconnect and day/night modes.

Google documents the `MapWithContentTemplate` surface for maps in Navigation, POI, and Weather apps, and its [car quality rules](https://developer.android.com/docs/quality-guidelines/car-app-quality) require apps to fit their declared category. POI apps must provide meaningful driving-relevant functionality; lyrics do not obviously qualify. [POI guide](https://developer.android.com/training/cars/apps/poi), [Draw maps](https://developer.android.com/training/cars/apps/library/draw-maps). A sideloaded or internal prototype may work on this car while still being unsuitable for Play review. We should not promise public Android Auto availability from this approach.

## Lyric source

Use an authored/sample `.lrc` file for the first end-to-end test, then user-imported `.lrc` files. Keep song lyrics out of the repository unless we have rights to include them. Match normalized title/artist and permit manual selection and timing offset. Spotify does not document a public lyrics endpoint; YouTube caption downloads require permission to edit the video, and YouTube API policy disallows stripping video to create a lyrics-only player. [YouTube captions](https://developers.google.com/youtube/v3/docs/captions/download), [YouTube API policies](https://developers.google.com/youtube/terms/developer-policies). A licensed provider such as [Musixmatch](https://github.com/musixmatch/musixmatch-sdk) is a later option, subject to pricing and display rights.

YouTube Music free-tier support is secondary. Google describes background play as a Premium feature; test its actual behavior while projected before promising it. [YouTube Music help](https://support.google.com/youtubemusic/answer/6313552).

## Implementation milestones and gates

1. **Playback probe:** Build a minimal debug app that shows Spotify package, title, artist, duration, position, pause state, and event timestamps from the active media session on the phone. Test track changes, seeking, ads, disconnect/reconnect, and playback while the lyric app is the foreground Android Auto app. Gate: stable identity and position; otherwise compare Spotify App Remote.
2. **Car surface and Commander probe:** Create a clean `CarAppService` in this repo using the dashboard's template/surface pattern. Draw synthetic, non-copyrighted timed lines. Add host action buttons and a list row; validate every control with the CX-5 knob, then check safe-area clipping and dark mode on the Mazda and Desktop Head Unit. Gate: lyrics visible and usable without touching the screen.
3. **Timed-lyrics engine:** Import `.lrc` on the phone, match it to current Spotify metadata, show static line changes on the car surface, and offer knob-operated ± timing offset and lyric visibility. Gate: a known test track remains within roughly 500 ms after pause, seek, and reconnect; stale metadata hides the lyric line.
4. **Distribution decision:** Review the actual car category, lyric rights, and Spotify terms against the finished prototype. If POI use is not accepted, keep it explicitly a private experiment or redesign around a supported product category rather than claiming Play compatibility.

The next coding task is milestone 1 plus a minimal part of milestone 2, so the first APK demonstrates **Spotify continuing to play while a lyric placeholder appears on the Mazda screen and can be controlled by the knob**. That is the key feasibility test for this product.
