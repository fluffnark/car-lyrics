# Open source apps and timed-lyrics catalogs

Checked 2026-09-19 for the private Mazda CX-5 prototype. Existing projects make the technical path much shorter, but none proves that our desired large custom lyric surface and Commander controls will work on this car without a device test.

## Existing Android Auto apps

| Project | Fit | Reuse decision |
| --- | --- | --- |
| [Auto Lyrics](https://github.com/GitUpGitUp/auto-lyrics) | Closest functional experiment: watches Android media sessions, queries SyncLRC with LRCLIB fallback, and displays synchronized lyrics in Android Auto. Its current source uses a `MediaBrowserServiceCompat` and media browse/now-playing UI; the README's IoT/`PaneTemplate` architecture appears stale. Media browsing should be knob operable, but the exact CX-5 behavior is untested. | Try as a reference build or on the Desktop Head Unit. Its README says MIT, but the checked-out tree has no `LICENSE` file; clarify before copying code. Its media UI is less flexible than the dashboard-style Canvas we want. [Current service source](https://github.com/GitUpGitUp/auto-lyrics/blob/main/app/src/main/java/com/autolyrics/auto/LyricsBrowserService.kt), [manifest](https://github.com/GitUpGitUp/auto-lyrics/blob/main/app/src/main/AndroidManifest.xml). |
| [AAMediaMate](https://github.com/gululu1235/AAMediaMate) | Apache-2.0 media bridge that reads another player's session and can show lyrics via media title text. It supports LRCLIB and other providers. It cautions against using lyrics while driving. | Useful reference for session bridging and cached lyrics; its title-text display is a poor fit for large, static two-line karaoke on the Mazda. [English README](https://github.com/gululu1235/AAMediaMate/blob/main/README_en.md). |
| [LRCGET](https://github.com/tranxuanthang/lrcget) | MIT desktop tool for bulk-fetching timed lyrics for **local audio files** and exporting `.lrc`. | Helpful if we later maintain a local music collection; it does not enumerate Spotify playlists or display on Android Auto. |

Build the Mazda display in this repo using the proven `car-dashboard` surface and host-action pattern. Reuse ideas and documented interfaces from these projects. Do not port an entire player just to obtain lyrics lookup.

## Catalog choice: LRCLIB first

[LRCLIB](https://lrclib.net/docs) is a free, community-maintained catalog with an unauthenticated `GET /api/get` signature lookup and `GET /api/search` fallback. Results may contain `syncedLyrics`, plain lyrics, or a richer `lyricsfile`. Line timing is sufficient for our first version; word-level timing is a separate coverage question. Its own server source is [MIT licensed](https://github.com/tranxuanthang/lrclib/blob/main/LICENSE); that source license does **not** establish that every song lyric is freely redistributable. Keep fetched lyrics in a personal cache for the prototype, and do not commit or bundle a catalog in the APK.

The site offers [database dumps](https://lrclib.net/db-dumps), but the upstream implementation describes a database around 70 GB. It is a poor phone asset and unnecessary for playlist coverage. Query only tracks in the user's library, deduplicate by title/artist/album/duration, and cache matches. [Upstream size note](https://github.com/tranxuanthang/lrclib/blob/main/LYRICSFILE_IMPLEMENTATION_PLAN.md).

LRCLIB recommends exact matching with track, artist, album, and duration; its documented duration tolerance is about ±2 seconds. For bulk work, send requests sequentially with 200–500 ms spacing and honor `429 Retry-After`. A fuzzy match must be confirmed before display if multiple recordings have similar names. [LRCLIB API guidance](https://lrclib.net/docs).

## Covering Spotify playlists and albums

Spotify OAuth can read the user's [saved tracks](https://developer.spotify.com/documentation/web-api/reference/get-users-saved-tracks), [saved albums](https://developer.spotify.com/documentation/web-api/reference/get-users-saved-albums), and [playlist list](https://developer.spotify.com/documentation/web-api/reference/get-a-list-of-current-users-playlists). [Album tracks](https://developer.spotify.com/documentation/web-api/reference/get-an-albums-tracks) can be enumerated. Since the February 2026 changes, [playlist items](https://developer.spotify.com/documentation/web-api/reference/get-playlists-items) are accessible only for playlists the user owns or collaborates on. Followed third-party playlists therefore need on-demand lookup as each track plays, or user-supplied exported metadata. See [the product workflow](PRODUCT_WORKFLOW.md) for eligible-only playback and mix creation.

The import should produce a **coverage report**, not a promise of universal lyrics: synced match, plain-only, no match, and ambiguous/version mismatch for every track. The actual percentage for this user's playlists cannot be known until they authorize an inventory and we run matching. Spotify Premium enables development-mode API access; it does not improve LRCLIB's song coverage or supply Spotify's own lyric catalog. [Spotify quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).
