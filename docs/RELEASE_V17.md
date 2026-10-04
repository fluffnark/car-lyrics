# 0.5.1 (17) — search, queue, and scrolling fixes

## Changes

- Car search processes the submitted phrase, leaving interim recognition text to Android Auto. Local and remote results stay inside SearchTemplate, whose updates count as refreshes.
- A microphone shortcut appears in every car menu and the compact player controls. Android Auto still owns the second “Select and speak” action; the app does not programmatically activate the host microphone.
- Player status is in subtitle text under a stable row title, so loading/playing/paused changes refresh the pane without consuming task steps.
- Browse returns to the root screen. Search waits until the host has requested the root Back template before pushing its new screen. Search starts a short flow, and returning to playback preserves the underlying player without reselecting its video.
- Phone queue changes refresh the active car queue. Queue rows resolve the current video ID before playing; transport controls read fresh queue/playlist contents.
- Phone Queue adds once and becomes disabled. Manage queue has Play on car, Move up, and an explicit Remove action. Queue/Mix taps update existing result buttons rather than recreating the list.
- Phone layout handles keyboard insets; submitting Search dismisses the keyboard. Previously queue rows extended underneath the keyboard.
- Car lists use static icons. The old thumbnail cache held fewer images than a long browse list requested, causing eviction/refetch and repeated invalidation. Playback state changes also no longer invalidate menus.
- Seek and Repair map actions are exposed in the standard POI player.

## Device and host testing

Pixel 7a, Android 17, Morphe 21.04.223, Shizuku running. Tested `poiProbe` with the same POI category and `FULLSCREEN_HOST=false` as the Play release. DHU profile: 1280×480 active area, rotary only, `restrict all` enabled.

| Flow | Result |
| --- | --- |
| Voice “Queen Bohemian Rhapsody karaoke” | Full phrase accepted; matching results; selected Sing King video played |
| Player microphone → second voice search | Full “Beyonce Halo karaoke” returned results from multiple providers |
| Phone addition → car Queue → rotary selection | Don’t Stop Me Now appeared and played; matching Morphe metadata |
| Phone Manage queue → Play on car | Somebody To Love played while the car had been in a collection menu |
| Phone Move up / Remove | Correct order persisted; explicit removal reduced the queue count |
| Browse genres, scroll to 80s | Focus and position remained unchanged across a 12-second wait |
| Scroll 80s song list to Billie Jean | Focus and position remained unchanged across a 12-second wait during playback |
| Phone keyboard | IME resizes the app; tapping the keyboard Search button dismisses it |
| Native backend | Actual video frames; MediaProjection reported null |
| Regression tests | 29 passing tests, including voice callbacks, queue persistence/order, menu invalidation, screen depth, seek and recovery |

The first repeat-search run reproduced “while driving” on the player pane. It exposed status titles consuming template steps and pop/push skipping the host Back template. Both were corrected and regression-tested. The final repeat-search device retest remains incomplete: the Pixel reached 1% battery, so video/DHU testing was stopped. The updated player was reached in the final build, but the repeated-search sequence was not completed. This fixes app flow/invalidation problems; Android Auto’s own restrictions remain under host control. Real Mazda/cabin microphone retesting is still needed. The user reported successful native playback on the physical Mazda with v16. Measured A/V sync and automatic end-of-track queue advancement are not covered by this release.

## Install

The Pixel’s Play-installed `com.doomslug.carlyrics` remains **v16**. Its signing key differs from the local upload key, so it was preserved. Local **Car Lyrics POI Dev** is v17 in `com.doomslug.carlyrics.dev`, replacing Native Dev with its data and Shizuku permissions retained. Queues belong to each package separately.

Upload `app/build/outputs/bundle/release/app-release.aab` to the existing Play internal testing track, then update Car Lyrics through Play on the Pixel. The bundle is signed in release mode, version code 17, version name 0.5.1. It has not been uploaded by this session; no Play Developer API credentials are configured.

Existing Shizuku and notification-access permissions persist for compatible updates. After a phone reboot, start Shizuku again before native playback. No new permission is introduced by v17.

Suggested Play release notes:

> Improved voice search reliability and added a microphone shortcut throughout the car app. Fixed passenger queue additions and playback selection, added phone queue management, and stabilized browsing with the rotary controller. Improved phone search layout when the keyboard is open.
