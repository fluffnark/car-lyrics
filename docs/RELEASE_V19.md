# Car Lyrics 0.6.0 (19) — setup recovery and passenger search

October 18, 2026. Package `com.doomslug.carlyrics.dev` is the installed POI Dev test copy. The Play-installed package remains v18; v19 requires its signed AAB to be uploaded to the existing Play testing track and installed through Play. Do not uninstall either copy to resolve a signing mismatch.

## Recovery findings and changes

The Pixel had rebooted roughly 17 hours before inspection. Shizuku 13.6.0 was installed but its shell server was absent; media-control access for both Car Lyrics packages remained enabled. The Play copy was in backup mode and needed native mode and Shizuku authorization restored. Those steps were completed without clearing app data.

The installed Shizuku setting is **Start on boot (root)**. The earlier notes claiming configured boot recovery were inaccurate for this unrooted phone. A future reboot can require **Shizuku → Start under Wireless debugging** or USB repair.

The app now distinguishes missing Morphe, missing media controls, stopped/disconnected Shizuku, and missing app authorization. The phone provides Open Shizuku and Repair picture/reconnect. Binder death resets the native service, timeout, readiness and initialization state; stale asynchronous results cannot revive an old connection. The player retains its selected video and position for reconnect and preserves paused state.

Device testing found Shizuku's server startup could miss binder delivery to an already-running Car Lyrics process. A bounded request to the installed manager's explicit receiver now refreshes that connection. Only callbacks from the manager UID are accepted, and normal Shizuku per-package authorization remains required. The compatibility fallback is isolated in `ShizukuConnection.kt`, audited against the installed manager's [13.6.0 request handler](https://github.com/RikkaApps/Shizuku/blob/v13.6.0/manager/src/main/java/moe/shizuku/manager/shell/ShellBinderRequestHandler.kt). This uses the SDK's internal provider attachment method, with a documented local lint suppression; recheck the fallback when updating Shizuku.

## Passenger search and library

- Search results display thumbnails with separate Play now, Play next, Queue, and Add to album actions.
- Karaoke preference appends karaoke when needed. Preferred provider refines the YouTube query; it is a ranking preference, not a guarantee that every returned video belongs to that channel. Library-only search avoids the remote request.
- Play now uses the current car session when connected and opens Morphe on the phone otherwise. The selected video is saved to Queue first.
- Play next inserts or moves a song immediately after the current selection. If the current song came from search/another list, it is adopted into Queue before the insertion. Adding next never restarts the current song.
- Queue and album additions are idempotent. Album names can be created on the phone; My albums/playlists opens saved lists and their song actions. The same lists are available in the car.
- Thumbnail requests have three workers and notify phone/car subscribers even when the image was already being fetched elsewhere. Missing images leave a fixed placeholder rather than blocking result actions.

[Phone search screenshot](images/phone-search-v19.png)

## USB commands

Run from the repository root:

```sh
./tools/caraoke doctor
./tools/caraoke repair
./tools/caraoke reload
./tools/caraoke logs
```

Doctor/repair default to the Play package. Use `--package com.doomslug.carlyrics.dev` to target Dev. Reload defaults to Dev and refuses the Play package; it builds the POI test variant and installs with `-r` to preserve app data. Repair starts Shizuku only when its process is absent and resolves the current installed native starter path, with a legacy script fallback. Repair refreshes an older pre-v19 app process because that copy cannot request a missed binder; v19 can reconnect without an app restart. No app is uninstalled, no data is cleared, and no Morphe/Google login is copied.

SDK detection handles the local mise layout even when the `adb` shim has no selected version. Multiple or unauthorized devices produce a specific instruction; use `--serial 32021JEHN04408` when needed.

## Verification

Pixel 7a, Android 17, Morphe YouTube 21.04.223, Shizuku 13.6.0. Android Auto Desktop Head Unit uses `tools/dhu-mazda.ini`: 1280×720 transport, 1280×480 active area, rotary input, touch disabled.

- 40 unit tests pass, including insertion order without restart, persistent named albums, repeated adds, library-only requests, stale responses after options change, and rejection of an untrusted binder callback.
- Debug lint passes with no errors. Signed release APK/AAB and POI Dev APK build successfully. Android 10 compatibility errors in the existing insets and process-output reads were fixed during validation.
- Phone: real Queen search returned Sing King/KaraFun results and visible thumbnails; provider preference returned KaraFun first. Created Road Trip with Bohemian Rhapsody, reopened it after reload, and found it in the car's playlist browser.
- Final installed phone build: library-only search returned Somebody To Love immediately; with the DHU disconnected, Play now opened that video in Morphe on display 0 and reported PLAYING. The test video was paused before releasing the phone.
- Phone Play now launched Bohemian Rhapsody on the native display, rendered its video in the Mazda DHU, and Morphe reported PLAYING with matching metadata. `dumpsys media_projection` reported null.
- While the car video continued, searched for Somebody To Love and selected Play next. Rotary Next launched that exact queue song. The existing queue stayed intact.
- Rotary pause produced PAUSED at 32,006 ms. Killing the Shizuku server and running repair restored the same video, the same 32,006 ms position, and PAUSED. The Car Lyrics PID stayed 8431.
- Resumed with the rotary control, killed/restarted Shizuku again, and verified PLAYING on the same song restored at 59,036 ms. The app PID remained unchanged and one new native helper served the recovered display.
- Disconnected and reconnected the DHU, reopened POI Dev with the rotary controller, found the preserved six-song queue, and selected Bohemian Rhapsody; native rendering and Morphe PLAYING returned.
- With `restrict all`, Browse, Playlists, Road Trip, and phone Return to current video remained available. Phone Repair picture recreated the display and restored the current selection/position; captured frames continued.

These are device/DHU observations, not verification of the physical Mazda, reboot auto-start, locked-phone operation, cabin microphone recognition, measured A/V sync, or public Play eligibility. Automatic queue advancement at the end of a video remains unfinished; manual Next follows the saved queue.
