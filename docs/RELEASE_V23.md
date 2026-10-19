# Car Lyrics 0.7.1 (23): large playlist browsing and lookup recovery

The Spotify import from 0.7.0 is retained. Car playlist and song pages now contain at most 20 entries, expose a page number in the header, and replace the current page screen instead of accumulating pages on the car navigation stack. The changed header distinguishes navigation from a same-title refresh, which retains the host scroll position.

A single failed karaoke search is saved for retry while subsequent songs continue. Previously failed songs are deferred until pending songs have been checked. Three consecutive failures, HTTP 429 or HTTP 403 pause the import and preserve progress. Search errors distinguish HTTP failures, timeouts, connectivity, and changed search responses.

## Validation and release status

51 unit tests, debug/release lint, APK/AAB builds and signature checks passed. Paging tests traverse 120 playlists and 220 songs, verify page titles differ, and verify bounded screen-stack depth. The final title change still requires a connected Pixel/DHU check: USB disconnected while opening the playlist list. Earlier same-title candidates retained the old scroll position and were not published.

Final candidate bundle: `releases/car-lyrics-v0.7.1.aab`, SHA256 `5110d393a14d544ca427e68d056455fa07904aeb8d523205956842848c05ba94`.

Uploaded candidate 23 (0.7.1) to a Play Internal testing draft. It has not been published or installed in the official package. Official installed release remains 0.7.0 (22); its 119 imported playlists and connected Spotify account are saved. Final head-unit check and official Play update are pending Pixel USB reconnection.

Candidate 23 was removed from the draft on October 19 and superseded by published 24/25. It was never published.
