# Car Lyrics 0.6.1 (20) — simpler phone and car controls

October 18, 2026. Builds use the standard POI host. The separate Dev package was used for detailed Pixel/DHU testing. The signed release AAB was then published to the existing Play internal track and the official app updated from v18 to v20 through Play on the Pixel. Updating the Play copy requires the existing internal testing track; ADB cannot replace its Play signing certificate.

## Interface

The phone opens directly to music, with a compact connection status, one search field, Explore / Queue / Albums tabs, and flat thumbnail rows. Tap a song to play immediately. Its three-dot menu holds Play now, Play next, Queue, and Add to album. Queue rows also offer reordering and removal. This keeps the full action set without repeating four buttons below every result.

Search filters open from the sliders icon: karaoke preference, preferred provider, and library-only. The Ready/Setup status opens connection details and one primary setup/repair action; advanced recovery options remain in its menu. A small now-playing bar returns the car to its current video. Saved albums hide empty starter lists.

The car video has four main controls: microphone, play/pause, next, and Browse. Seek current video, Repair picture, and Save current video live in Browse. The floating three-button tool overlay has been removed so it no longer covers the picture.

## Recovery hardening

Each native service binding owns its own connection callback and generation. Callbacks and worker results from an old binding cannot attach to a newer session. Cleanup removes client callbacks before stopping the remote service. Server restart recovery retains the resolved video identity, position, and paused/playing intent; an unknown Morphe autoplay video cannot apply its timestamp to an unrelated selected song.

The [v19 recovery notes](RELEASE_V19.md) explain the original stopped-Shizuku finding, permission setup, explicit manager reconnection, and USB commands. Shizuku still needs a manual wireless or USB start after a reboot on this unrooted Pixel.

## Verification

Pixel 7a, Android 17, Morphe 21.04.223, Shizuku 13.6.0. Mazda-style DHU profile: rotary input, touch disabled, 1280×480 active area.

- 43 unit tests pass with zero failures/errors. Debug lint passes. POI Dev APK, signed release APK, and signed AAB build successfully. APK and bundle signatures verify against the existing local upload key.
- Phone thumbnail results, compact queue, all four song actions, album creation and reopening, and idempotent album additions were exercised on the device.
- Native video renders with four car actions and no floating tool overlay. Matching Morphe video metadata confirms the selected URL.
- The car search microphone accepted recorded synthetic speech, “Queen Bohemian Rhapsody karaoke,” returned live results from several providers, and rotary selection opened the matching native video.
- With driving restrictions enabled, rotary Browse → Seek current video → 0:20 updated Morphe to 20,000 ms. The phone now-playing bar returned the car to its video.
- Phone Play next moved I Want To Break Free immediately after the current selection without restarting it; rotary Next launched that exact video.

- Final UI APK installed in place with `tools/caraoke reload`; all six queued songs and Road Trip remained. Restored the queue order changed by Play next testing.
- Final-build rotary Pause stopped Bohemian Rhapsody at 16,079 ms. Stopping Shizuku and running USB repair restored the same video at exactly 16,079 ms, still paused, with the same Car Lyrics PID 25991.
- Resumed with the knob, stopped/restarted Shizuku again, and verified PLAYING restored near 16,994 ms. Exactly one native helper remained and Car Lyrics PID stayed unchanged.
- Killed only the native helper and tapped phone Setup/Ready → Repair picture. A new helper restored Bohemian Rhapsody near 29 seconds and PLAYING without restarting Car Lyrics.
- Screen-off testing found keyguard blacked out the private video display while audio continued. The native display now requests its own group and always-unlocked flag, leaving the primary phone display and its lock intact. Android requires that group for this flag; see [AOSP VirtualDisplayAdapter](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/display/VirtualDisplayAdapter.java). The final adjustment passed on the Pixel: compact 800×480 DHU showed advancing lyrics while primary display group 0 was dozing and keyguard reported showing/input-restricted. No phone unlock was needed to start the new native display from the head unit.
- While the phone stayed locked, paused Bohemian Rhapsody at 38,996 ms, stopped/restarted Shizuku a third time, and restored the same video at exactly 38,996 ms, still paused. Exactly one native helper served the recovered display.
- Reconnected the final build to the wider 1280×480 Mazda profile, selected the preserved queue song from the phone, and verified the native picture and matching PLAYING metadata again.
- Browse → Save current video produced Saved to Favorites, and a second selection removed it, restoring the original favorite state. The phone now-playing bar returned from Browse.
- Final cleanup: paused Morphe, stopped DHU, confirmed all six queued IDs/order, and found no AndroidRuntime errors in the recent log window. Released the Pixel to the Nest task and wrote the timestamped release marker for dashboard coordination.

[Minimal phone UI](images/phone-search-v20.png)

The locked-phone fix was followed by the full 43-test/lint/APK/AAB build again. Physical-car checks remain outstanding.

## Remaining limits

DHU tests do not verify the physical Mazda display/Commander knob, actual cabin microphone, measured A/V synchronization, or Play eligibility. Automatic end-of-video queue advancement remains unfinished; manual Next follows the queue. These limits also apply to the earlier v19 test evidence.

## Official Play release and installation

At the owner’s explicit request, published **20 (0.6.1)** to the existing Internal testing track using the signed-in Play Console browser on October 18, 2026 at 4:24 PM Denver time. Console confirmed Available to internal testers. There were no lost supported devices and no blocking errors; the single warning concerned the absent deobfuscation file (this release does not enable shrinking). Existing closed-track drafts and production settings were left alone.

The Pixel’s app-details page initially stayed cached on Open; Manage apps & device → Available updates offered Car Lyrics. Updated only Car Lyrics from that list. Package manager confirmed **0.6.1 / 20**, last updated at 4:31:53 PM, installer `com.android.vending`. Pulled the installed APK and verified its Play certificate matches the pre-update v18 certificate: `951ebe45ed00c2cb66cab5a4ad21e0f6a892e98c54fa8338e7039bd789bd7fb7`. No uninstall or app-data clear was used.

The official app opened with Ready and thumbnail results. In the Mazda-profile DHU, selected the official Car Lyrics launcher tile and a recent Miki Matsubara karaoke song with the rotary controller. Native video rendered, matching Morphe metadata reported PLAYING, and the only helper belonged to `com.doomslug.carlyrics:native_morphe`. Paused the video and stopped DHU before releasing the Pixel. This confirms the official Play package and permissions, while physical Mazda testing remains separate.

Uploaded immutable bundle: `releases/car-lyrics-v0.6.1.aab`, SHA256 `df19c20c56f1c923535af0c02db9c461e34843319caa3c763f91806671374841`.
