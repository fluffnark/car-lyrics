# Car Lyrics 0.9.0 (26): history, native Home and matching coverage

## Behavior

History keeps up to 200 unique recent videos and available resume positions in local app storage. Temporary empty media timelines retain the last known progress. Identifiable Morphe playback can update progress while the phone UI is closed; unsupported or unidentified media metadata is not attributed to a different song. Phone history supports resume, play from the beginning, removal and clear. Car history is paginated so older entries remain reachable without growing the screen stack.

Play current video works from phone and car browsing, including the last local history entry when no car session exists. Phone scroll positions are kept separately for search/options, queue, playlist overview/filter, each playlist, Home and History, and saved across Activity recreation. Car browsing/search screens remain underneath playback, preserving query and page state. The host still controls car scroll/focus; exact rotary restoration is not guaranteed.

Home is a native list inside Car Lyrics on both phone and car. The optional Shizuku helper reads only recommendation cards from Morphe on its own private display. Public YouTube search verifies title/channel identity; public metadata supplies the canonical player title, including YouTube alternate-title cases. Unverified links remain unavailable. Thumbnails settle before the new car list is published so image downloads do not repeatedly reset rotary focus. Refresh and More recommendations are available. Morphe handles login and playback; no account cookies or credentials are imported. Cards remain in memory. Android 11 or later, compatible Morphe UI metadata and native setup are required; this is a local UI bridge, not an official account feed API.

## Spotify coverage

The bundled matching index expands from 5,138 titles to 22,074 public titles: 10,174 KaraFun, 6,427 Sing King, 2,581 Sing2Piano and 2,892 Funbox Karaoke. Its JSON asset is 1,942,324 bytes; no videos or audio are bundled. The updater is `tools/update_matching_catalog.py`.

Matching uses indexed rare title words, improved provider/collaborator/recording normalization, four targeted online searches with transient recovery, and shared verified recording results across playlists. Manual choices stay specific to their imported song identity. Old misses are eligible for policy-3 rechecking. Incomplete lookups remain retryable; HTTP 403/429 stop the pass safely. Partial public-preview refreshes preserve previously known songs/manual choices and label read/known/declared totals.

An offline audit of 119 development playlists and 3,377 imported rows found 217 additional playlist-row candidates (183 song identities, 179 videos), projecting 255 to at least 472 matches. That was candidate evidence, not a saved-device claim. After the combined development install, Continue matching saved **474 video rows**, up from **255**, with the existing manual choice unchanged. Final saved states: 473 matched, 1 manual, 2,736 pending, 156 missing and 11 errors. Matching was stopped after this verification; this is not a completed online pass or an official-package matching result.

Source completeness remains separate: 4,023 declared entries versus 3,377 imported rows. Five restricted previews account for 644 missing source entries; two additional one-row gaps remain unclassified. Full CSV imports are needed to fill restricted previews. A larger matching catalog cannot match songs that were never imported.

## Verification, October 22, 2026

- 73 JVM tests, zero failures/errors; debug and release lint pass.
- Development APK, release APK and release AAB build successfully. APK/AAB signatures verify with the existing upload certificate.
- Pixel 7a development package updated in place to `0.9.0-poi-dev` (26), retaining playlists, queue and manual choice. The official package was still `0.8.1` (25) during development verification; its subsequent Play update is recorded below.
- Pixel: native Home stayed inside Car Lyrics, showed account recommendations with thumbnails, and loaded more cards. Current-video navigation opened Morphe. History captured actual karaoke playback checkpoints and a recommendation with its canonical title (8.5 seconds into a 47:28 video), validating the alternate-title fix. Playlist overview/detail scroll survived tab changes, playback handoff and Activity recreation checks.
- Mazda-profile DHU: native Home rows/thumbnails, recommendation playback and return to the same Home row verified. A deeper scrolled Queue playback/back round trip reset to the first row. Reusing the same Screen and the exact previous Template did not change that host behavior; the ineffective template cache was removed. Phone scroll restoration and car query/page retention pass, but exact rotary scroll/focus restoration remains unresolved on this host.
- Tests cover local history persistence/limits/removal, independent phone scroll positions, current-video fallback, native Home identity checks, bounded car history paging, search/player round trips and Spotify partial-preview preservation.

The DHU is an emulator connected to the Pixel. Physical Mazda cabin controls, microphones, A/V synchronization and reboot behavior were not retested in this session. Resume progress depends on metadata/seek support exposed by the installed Morphe build. Version 26 was published to Play internal testing on October 22, 2026 at 14:49 America/Denver. Console confirmed Available to internal testers. No supported devices were lost; the only warning is the missing deobfuscation file, with shrinking disabled.

Candidate AAB: `app/build/outputs/bundle/release/app-release.aab`, SHA256 `773595ea43459681f003b81d15a4476f07aded66299934d5ddda957159f3d219`. Upload certificate SHA256: `9aad5abe44127308ccca0f1670e31ece186f006f50e36cf99851f9f4b0e0e3f1`.

## Internal release

Published through Play Console in the existing Zen browser session. Version 26 is the only included bundle; version 25 is excluded from the new release. [Console confirmation](images/play-internal-v26.png). The preserved upload artifact is `releases/car-lyrics-v0.9.0.aab` with the SHA256 above. Before the update, the official Pixel copy was version 25 with 119 imported playlists, 172 saved matches, and one queued video. Its Play signing certificate SHA256 is `951ebe45ed00c2cb66cab5a4ad21e0f6a892e98c54fa8338e7039bd789bd7fb7`; this differs from the upload key, so the local upload-key APK is not used to replace it. The official app was then updated through the Pixel Play Store at 15:00:21 America/Denver. ADB confirmed `com.doomslug.carlyrics`, versionName `0.9.0`, versionCode `26`, and installer `com.android.vending`. Its Play signing certificate was unchanged, as was firstInstallTime (`2026-10-08 10:39:44`). The new UI showed Ready, Home and History controls, the existing one-item queue, and existing Spotify playlist rows. Neither the package nor its app data was removed. The 474-match measurement above remains development-package evidence; existing official playlists use Continue matching to save results from the expanded index.
