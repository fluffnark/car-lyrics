# Car Lyrics 0.7.0 (22): Spotify karaoke playlists

## Behavior

Albums → Spotify playlists connects through browser OAuth with PKCE and read-only playlist scopes. The app inventories every page, reads playlist items using Spotify’s current `items` endpoint (and understands legacy `track` fields), then matches independent YouTube karaoke videos using artist and title. Confirmed matches become separate local albums available on the phone and in Android Auto. Spotify audio is never used; the original Spotify playlists are never modified.

Public Spotify embed data is a fallback when development-mode playlist-item access is denied. Its playlist identity is checked. If its returned track count cannot be verified against the inventory, the app labels it as a public preview and offers CSV for the complete list. Unknown and ambiguous matches remain unmatched; choosing a song in import details opens search, where ⋮ → Use for this Spotify song saves a manual correction.

Imports save each playlist atomically in private app storage. Pausing, reloading, a network failure, or a process restart preserves completed matches; Resume continues remaining work. Spotify/network server failures retry with bounded delays. Confirmed results are reused for repeated songs. Misses are rechecked after seven days. A refresh reconciles accessible source tracks while preserving matches and manual choices. Removed source playlists remain saved locally rather than being deleted automatically. Handmade albums stay separate.

Tokens are encrypted using Android Keystore. Disconnect removes tokens and retains imported karaoke albums. The updated privacy policy explains that song/artist searches go to YouTube. The Spotify logo is the official white SVG distributed with Spotify’s design guidelines, converted to an Android vector without changing its paths.

Car lists and songs page within the host content limit. Import progress does not redraw the car menu under the rotary selection; reopening a list reads the latest saved results.

## Validation

51 unit tests pass, including artist/title mismatch rejection, accent/remaster normalization, full API pagination, legacy/current track shapes, episode omission, repeated-page failure, public-preview identity validation, CSV quoted commas/newlines/BOM, stable import identity, local-album preservation, manual corrections, and car paging through 120 playlists and 220 songs.

`lintDebug`, `lintRelease`, release APK/AAB builds, APK signature verification and AAB signature verification pass. The upload certificate is unchanged.

On the connected Pixel, completed browser authorization without exposing passwords or exporting tokens. Actual account inventory contained 119 playlists and 3,377 track occurrences (2,947 unique tracks). The public embed fallback produced 114 complete lists and five labelled previews; no list was silently omitted. Coverage is measured only for the tracks actually read, and further tracks in previews require a full CSV export.

Opened an imported playlist with the Mazda-profile DHU rotary controls and selected a matched karaoke video. Morphe rendered its video/lyrics on the head-unit surface. Test media was paused and the DHU stopped. Physical Mazda display, cabin microphone and measured A/V timing remain outside this emulator check.

## Release

Final bundle: `releases/car-lyrics-v0.7.0.aab`, SHA256 `3f574aa1f7f7a1ae457e146879c83d530832cd95331fabde688b582b245fb90f`.

Published 22 (0.7.0) to the existing Play Internal testing track on October 18, 2026 at 10:12 PM Denver time. Console confirmed Available to internal testers. No supported devices were lost; the single warning was the absent deobfuscation file (shrinking is disabled). The earlier 21 candidate was removed from the draft before publication. Production and existing closed-track drafts were left alone.

Updated the official Pixel package through Play at 10:26:43 PM Denver time. Package manager reports 0.7.0 (22), installer `com.android.vending`. The installed APK signature matches the previous Play certificate: `951ebe45ed00c2cb66cab5a4ad21e0f6a892e98c54fa8338e7039bd789bd7fb7`. No uninstall or app-data clear was used. Ready/native mode and the existing queue were preserved.

Completed Spotify browser sign-in on the phone in the official package and started its first import. Development and official imports are separate; development test progress was paused before starting the official import. A manual match selection was also exercised in the development copy and retained the correct video.

The final official build was also opened in the DHU. Its 119 imported playlists appeared with coverage labels, including the five public previews, and the first page exposed More at 98 of 122 albums. The host applied its normal Safety pause after sustained rotary scrolling.
