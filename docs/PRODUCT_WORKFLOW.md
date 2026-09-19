# Lyric-ready library and playback workflow

> Earlier Spotify concept. The current APK mirrors Morphe YouTube karaoke video; it does not import Spotify albums, filter playlists, or fetch timed lyrics.

Updated 2026-09-19. Target: build playlists, select albums, and shuffle mixes **inside Car Lyrics**, with browsing and playback controls reachable by the 2021 Mazda CX-5 Commander knob. Spotify Premium is the audio source; LRCLIB and imported `.lrc` files provide lyrics. The app is a private Android Auto prototype.

## User flow

1. On the phone, connect Spotify using [Authorization Code with PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow). Grant only the scopes needed to read selected library sources and control playback; request playlist-write scope only when the user elects to save a filtered playlist to Spotify. Notification access is separately granted for fast local playback-state tracking.
2. Import saved albums, saved tracks, and playlists the user owns or collaborates on. Search for an album or track to add it to an in-app mix. The phone preflights each track against [LRCLIB](https://lrclib.net/docs), caching the selected timed-lyrics record and any manual correction.
3. Show each collection as **Ready / Total**. The user can view excluded tracks and fix an ambiguous match or import a `.lrc` file. A mix is a local list of Spotify track URIs and source rules; creating it does not automatically alter the Spotify account.
4. In the car, use knob-focused host rows to select **Lyric-ready Albums**, **My Playlists**, **My Mixes**, or **Shuffle All Ready**. Opening a collection shows its ready count and actions **Play in order**, **Shuffle ready tracks**, and **Save ready tracks as a mix**. The last action creates an automatically named local mix without text entry; full renaming and track-by-track edits happen on the phone. The lyric surface shows only the approved current track and one or two static timed lines.
5. Starting playback creates a session queue containing only ready Spotify track URIs. Spotify's official app performs the audio playback. The app observes every track change; if playback leaves the approved queue, it hides lyrics on detection and asks the user to return to the mix or stops playback where control is available.

## What counts as lyric-ready

A track is eligible when it is a playable Spotify music track, has a strong title/artist match, a suitable recording-duration match, nonempty synchronized lines spanning the apparent sung portion, and a cached lyric record or user-imported file. Plain text without timestamps, a snippet, an instrumental with no lyric lines, and an ambiguous live/remaster mismatch are excluded. The user may manually verify/correct a match and adjust its timing offset.

No catalog can prove automatically that every sung word appears in a community lyric record. The UI should call automatically matched tracks **Likely ready** and provide an optional **Verified only** mode based on user review. The default queue must exclude clear misses and partial snippets. Display coverage honestly rather than promising every album will have every song.

## Spotify integration and queue strategy

| Need | Current documented API | Prototype use |
| --- | --- | --- |
| Inventory sources | [Saved tracks](https://developer.spotify.com/documentation/web-api/reference/get-users-saved-tracks), [saved albums](https://developer.spotify.com/documentation/web-api/reference/get-users-saved-albums), [album tracks](https://developer.spotify.com/documentation/web-api/reference/get-an-albums-tracks), and [current user's playlists](https://developer.spotify.com/documentation/web-api/reference/get-a-list-of-current-users-playlists) | Build the in-app library and coverage counts. [Playlist items](https://developer.spotify.com/documentation/web-api/reference/get-playlists-items) are currently readable only for owned or collaborative lists; followed third-party mixes need on-demand matching or user-supplied metadata. |
| Start a filtered session | [Start/Resume Playback](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback) accepts an array of track URIs for Premium users | Send only eligible URIs. For shuffle, randomize that array ourselves and request Spotify shuffle off, so the app's eligibility filter determines the order. Test queue ordering and end behavior on the Pixel and Mazda. |
| Save a reusable Spotify list | [Create Playlist](https://developer.spotify.com/documentation/web-api/reference/create-playlist) and [Add Items](https://developer.spotify.com/documentation/web-api/reference/add-items-to-playlist) | Optional explicit export to a private Spotify playlist. Keep local mixes first, so edits need not create many Spotify playlists. |
| Playback controls | [Queue](https://developer.spotify.com/documentation/web-api/reference/get-queue), [shuffle](https://developer.spotify.com/documentation/web-api/reference/toggle-shuffle-for-users-playback), [repeat](https://developer.spotify.com/documentation/web-api/reference/set-repeat-mode-on-users-playback), and Android media-session state | Route car knob play/pause/next and track selection through host actions. Verify Spotify's active device accepts commands; [some devices are restricted](https://developer.spotify.com/documentation/web-api/reference/get-a-users-available-devices). |

Spotify says Player API commands may execute in an unexpected order when combined, so issue commands sequentially and verify the resulting session before showing lyrics. Spotify [Autoplay](https://support.spotify.com/us/article/autoplay/) can add similar tracks after a selection ends; ask the user to turn it off for strict sessions and monitor the actual track. If the source player changes outside this app, **absolute audio-only eligibility cannot be guaranteed** because Spotify owns playback. The app can guarantee that it never intentionally queues an unready track and never shows lyrics for a track outside its accepted match set; unexpected tracks are detected and handled as quickly as the player allows.

## Release order

1. Prove a two-track filtered queue using test lyric records, with Spotify playback visible in the car and knob-operated selection.
2. Add album import, LRCLIB preflight, readiness counts, and a car album picker.
3. Add local named mixes, deterministic shuffle of eligible tracks, and exclusion/correction screens on the phone.
4. Add playlist import and optional private Spotify playlist export.
5. Measure coverage and handling of Spotify Autoplay, ads, seeks, disconnected phone, network loss, and external queue edits on the CX-5.

Spotify's [developer policy](https://developer.spotify.com/policy) includes a restriction on synchronizing recordings with visual media. Premium does not waive it; this private prototype establishes technical feasibility, while any broader distribution requires a separate rights/policy decision.
