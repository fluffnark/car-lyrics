# Car Lyrics 0.8.1 (25): playlist editing and improved matches

## Phone behavior

The Albums tab is now Playlists. The search field filters playlist names while this tab is selected. Cover artwork and compact rows lead to an inline song list with thumbnails and the standard Play now, Play next, Queue and Add to playlist menu. New creates an empty playlist. Local playlists can be renamed or deleted, and songs can be removed or moved up/down. Empty lists remain visible. Deleting a starter list stays deleted after reload. Duplicate names are rejected without merging or losing songs.

Spotify imports remain tied to their source lists. Make editable copy creates a local karaoke mix whose order and membership can be edited independently. Original Spotify lists are not modified. Import & matching opens a quieter progress screen, with Continue/Pause as the primary action; refresh, CSV, disconnect and rechecking unmatched songs are in the options menu. Song review and manual correction remain available.

## Matching

Artist and song identity are still required. Provider/year annotations such as the observed `Flipturn - August (Funbox Karaoke, 2018)` are accepted when the artist and title are present outside the annotation. Known collaborator names and remaster labels no longer create false rejections. Longer song titles, wrong artists, and unrequested live/cover/remix/acoustic variants remain rejected. A second targeted search runs only when the first successful search yields no confident match. Old misses are rechecked on the first completed pass of the new policy, and Recheck unmatched songs bypasses the normal seven-day miss cache.

The progress screen distinguishes pending songs, no confident match, and lookup errors. Individual lookup errors are saved for retry and do not block all later songs; three consecutive errors or HTTP 403/429 pause safely. A fast identity check rejects unrelated library candidates before processing annotations. Local matching now reports playlist progress. Shared thumbnail IDs update all visible instances.

## Verification

53 tests pass, including empty creation, rename, reorder, remove, reload, starter deletion, provider metadata, collaborators, remasters and rejection of longer/live/cover results. Debug/release lint and APK/AAB signature checks pass.

On Pixel 32021JEHN04408 in the separate development package, created and renamed a test playlist, verified it after an in-place update, filtered playlist names, deleted only that test playlist, and verified it disappeared. Opened the new import screen and resumed matching. With the same 119 saved source lists, the initial matching pass raised the development total from 223 to 255 while preserving manual matches. About 2,950 tracks remained pending; this was not a completed full-library match result. Direct YouTube search for Adele Hello returned karaoke results on the phone. Paused the development import before switching to the official app.

Mazda-profile DHU rotary testing of the shared car paging implementation opened Playlists page 1/7, selected More at 20 of 123, and confirmed page 2/7 began at its first row with focus reset. The DHU was stopped afterward. This is emulator verification, not a new physical Mazda cabin test.

## Release

24 (0.8.0) was published to Internal testing on October 19, 2026 at 08:15 America/Denver. It includes the playlist editor and improved matching. 25 (0.8.1), published at 08:25, adds the large-library fast path, progress feedback and shared-artwork fix. Console confirmed Available to internal testers. No supported devices were lost. The only warning is the missing deobfuscation file, with shrinking disabled. Candidate 23 was removed from the draft and never published.

Final bundle: `releases/car-lyrics-v0.8.1.aab`, SHA256 `fd29c3f70e846de47e9b670ca11a4183f963c366e354f9867520dabcf38140c9`. Upload certificate unchanged: `9aad5abe44127308ccca0f1670e31ece186f006f50e36cf99851f9f4b0e0e3f1`.

After opening the internal-test download link, the official phone UI showed the new Playlists tab and thumbnail song list with its existing queue and imported source list. USB disconnected before package version/certificate verification and before starting matching in the official app. Those final checks remain pending reconnection; no installed-version claim is made for 25.
