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

Official installation is being verified.
