# 0.5.2 (18) — larger picture and player Favorites

## Changes

- The POI player uses a single-line header once Morphe is ready. Video can use the area below that header as well as the host's narrow right-hand visible rectangle; the larger fit wins. The host's right and bottom boundaries still limit drawing, and the entire frame retains its aspect ratio.
- A star on the video screen saves/removes a favorite without restarting playback. Open **Browse → Favorites** to find saved songs. Favorites and the existing playlist mix are separate.
- Favorites resolve the current media ID/URL when supplied by Morphe. This installed Morphe version exposes a title but no ID; saving therefore requires that its title match the selected video. If Morphe autoplays something else, the button asks you to select that video in Car Lyrics instead of saving the wrong song.
- Repair uses the identified playing video. When its identity is unavailable, it reopens the selected video from the start rather than applying another video's position. Metadata title changes refresh player controls without refreshing browse menus.
- Shizuku binds while browsing; Morphe is still opened only after selecting a video. Graphics initialization runs on a worker, and startup stages have timing logs.
- The native display remains 1280×720 but uses 320 dpi (640×360 logical dp) to request a landscape phone layout rather than a tablet layout.

## Test evidence — October 5, 2026

Pixel 7a, Android 17, Morphe 21.04.223, Shizuku running; `poiProbe` uses the same POI host/category as release. DHU 2.1, Mazda profile, 1280×480 active area, rotary input.

| Check | Result |
| --- | --- |
| Reproduce tiny video | Surface 770×460; visible rectangle `(440,92)–(678,448)` reduced the picture to 238×134 |
| New split-view fit | 597×336 complete frame below the header, clear of map card and car controls; verified with title screens and moving lyrics |
| Favorite from player | Star toggled, preferences persisted, Favorites listed Bohemian Rhapsody and Somebody To Love; playback continued |
| Queue Next | Opened Somebody To Love on the existing native display; retained the large picture |
| Browse → Favorites → Now playing | Returned to the large picture without a new native launch; this path was also exercised with `restrict all` |
| Repair picture | Recreated the native display and retained the large layout; exposed the stale autoplay timestamp case, subsequently guarded in code |
| Startup sample | 141 ms native display creation, 103 ms URL launch, about 3.1 s selection-to-PLAYING; warm Next about 1.1 s |
| Regression tests | 35 passing tests, including Favorites, autoplay identity guards, split/media geometry, density scaling, short surfaces, search, queue, seek, and recovery |
| Release artifacts | Release APK/AAB built; signatures verified |

These startup figures are individual samples, not a benchmark. The 112 dp clearance below the single-line header is verified on this DHU layout. Different host layouts and font settings need a physical Mazda check. The fallback uses the host rectangle while the setup/error pane has extra content.

The host PAN action did not expand the POI content in this test and was removed. There is no promise of forced full-screen: Android Auto owns the split layout. This release improves the picture within the space it gives the app.

Morphe's autoplay still does not follow the Car Lyrics queue automatically. The final autoplay repair guard is regression-covered through identity validation; its complete device recovery path was not repeated after that last change. Repeat-search quota testing, real cabin voice recognition, lock/reboot behavior, and measured A/V sync remain outside this verification. The user reported working native playback on the actual Mazda with the preceding release.

The DHU launcher intermittently lost rotary input before Car Lyrics opened. Reconnecting DHU 2.1 in headless mode restored console input. Phone/app data were preserved. Screenshots containing the unrelated map card were kept out of the repository.

## Install

Upload `app/build/outputs/bundle/release/app-release.aab` to the existing **Internal testing** track. Package `com.doomslug.carlyrics`, version **0.5.2**, code **18**, signed in release mode. Then update **Car Lyrics through Google Play** on the Pixel.

The Pixel's Play app remains **v17** because its Play signing key differs from the local upload key. The separate **Car Lyrics POI Dev** test package is v18. No Play upload was performed and no upload credentials are configured here. Existing Shizuku authorization persists across compatible updates; restart Shizuku after a reboot if needed. This release adds no permission.

Suggested Play release notes:

> Larger karaoke video in split-screen layouts, with improved picture sizing. Added a Favorite button directly on the player and a Favorites menu. Improved native player startup and protected favorites and picture repair from stale autoplay information.
