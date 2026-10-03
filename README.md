# Car Lyrics

Private Android Auto karaoke prototype for a 2021 Mazda CX-5, using the installed **Morphe YouTube** app on the phone as its player. Car Lyrics sends selected YouTube URLs to Morphe, mirrors its video to the car surface, and controls its media session. Your existing Morphe login stays in Morphe.

## Start a session

1. On the phone, open Car Lyrics and tap **Enable Morphe controls**. Android calls this notification access; the app uses it for Morphe playback state and transport controls. It does not read or save notification contents.
2. Tap **Start Morphe sharing**, choose **Share one app**, then **YouTube Morphe**. If it is absent from recent apps, scroll the app list to Y.
3. Open **Car Lyrics** in Android Auto. Select a song or a saved queue/playlist. The car actions are Previous, Play/Pause, Next, and Browse, from left to right.
4. Keep the phone unlocked. Stop sharing from the phone app or its ongoing notification when finished. Locking, reinstalling, or ending capture requires fresh Android consent.

Morphe owns both video playback and audio. Car Lyrics captures video frames only; Android Auto routes the original audio. Browsing retains playback and the live capture session. No account credentials, capture recordings, or notification contents are stored by Car Lyrics.

## Karaoke library and queues

The car has Sing King recent uploads, a local index of 5,000 titles, curated collections, and public YouTube search across providers. Car and phone search both accept song, artist, album, genre, and provider names. **Queue** and **Mix** on phone results save videos locally; open Queue or Playlists on the car to select them. Next/Previous follow the list selected in Car Lyrics. Automatic queue advancement and reconciling Morphe's own autoplay are still unfinished. Live phone additions do not yet update an already selected playback list.

Lyrics are the text baked into each karaoke video. Availability depends on whether Morphe can play that video for your account and region; this is not a guarantee that every YouTube upload is accessible. The old embedded player is retained in source, but new installations default to Morphe.

## Voice search

On the car: **Search YouTube → microphone in the search field → say a song and artist → select a result**. For example, say “Queen Bohemian Rhapsody karaoke” or “find karaoke for Halo by Beyoncé.” Include “karaoke” to favor instrumental versions. Results use normal Android Auto rows; selecting one goes through the existing Morphe playback controls. This is the microphone inside Car Lyrics search, not a global “Hey Google, play in Car Lyrics” integration.

On the phone: scroll to **Passenger Queue → Voice search**, speak, then use **Queue** or **Mix** beside a result. Typing and the keyboard Search key work too. Canceling recognition leaves the existing query alone. Android Auto/the phone speech provider handles audio; Car Lyrics receives the transcript. See the [privacy policy](docs/privacy-policy.md).

Local matching tolerates accents, punctuation, and common spoken commands. Library filtering runs off the UI thread, typing is debounced, and old responses cannot replace a newer search. If YouTube search fails, matching local songs remain available with a Retry action. Public YouTube page parsing may need maintenance when YouTube changes its response format.

## Voice release verified on October 3, 2026

Car Lyrics **0.4.1 (14)** installed on the Pixel 7a:

- DHU microphone WAV input recognized “Queen Bohemian Rhapsody karaoke”; live YouTube results included Sing King and KaraFun. Selecting KaraFun opened the correct video in Morphe and rendered it in the car player; Morphe reported PLAYING with matching metadata.
- Repeating the car search with “Beyonce Halo karaoke” produced matching results from multiple providers; selecting Musisi Karaoke opened its matching Halo video. The first search's results did not overwrite the second.
- The phone returned live YouTube results for the Queen query. Its Voice search button opened system recognition, and canceling returned to the unchanged query. Actual phone-microphone transcription has not been tested.
- Nineteen unit tests pass, including interim speech bursts, background local filtering, late responses, network fallback/retry, parser correctness, and submitted speech → result → player selection. Signed APK and AAB builds pass.

The test harness uses recorded synthetic speech with the DHU's `mic play`, not a real Mazda microphone. Restart Android Auto/DHU after reinstalling the app: this host retained dead speech callbacks across APK replacements. DHU tap coordinates also changed when the desktop resized its window; calculate them from the current window size.

The current screen-sharing backend can blank car video while Morphe is hidden by the passenger phone app, even while audio continues. Independent phone browsing plus uninterrupted car video remains a native-backend acceptance gate; see [the native Morphe plan](docs/MORPHE_NATIVE.md).

[Voice results](docs/images/voice-search-results.png) · [Selected Morphe video](docs/images/voice-search-video.png)

## Verified on October 2, 2026

Pixel 7a running Android 17, Morphe YouTube 21.04.223, Car Lyrics **0.4.0 (13)**:

- Actual Morphe video and karaoke lyrics rendered in Android Auto Desktop Head Unit (DHU), at 1280×720 and 800×480.
- A complete 3:54 karaoke video remained visible through its ending; Morphe then autoplayed its own recommendation, confirming the queue-ownership follow-up.
- Car selections open Morphe on the phone; Pause/Play and manual Next control Morphe specifically.
- Capture survives browsing and DHU surface replacement/reconnection without reusing the consent token.
- Audio reaches the DHU stream (a short isolated stream recording measured nonzero audio after raising its test volume).
- Eleven unit tests pass, including media-session targeting, real playback status, phone display launch, and existing browse/queue flows. Signed release APK and AAB build successfully.

**Remaining verification:** physical Mazda display and Commander knob, real cabin microphone recognition, and measured audio/video synchronization. Rotary commands did not move focus reliably in the October 2 DHU session; the October 3 profile below corrects its input mode. The 800×480 host grants the full app area. The normal POI build keeps a Maps side panel on the wider host. Video is aspect-fitted, preserving the lyric edges.

This uses a POI custom surface as a private experiment. DHU success does not establish public Play eligibility or compatibility with every Android Auto head unit.

## Build

Use JDK 17 and Android SDK API 36:

```sh
JAVA_HOME=/home/doomslug/.local/share/mise/installs/java/17.0.2 \
ANDROID_HOME=/home/doomslug/.local/share/mise/installs/android-sdk/23.0 \
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk` — locally installed prototype.
- `app/build/outputs/bundle/release/app-release.aab` — version code 14 (0.4.1), signed release bundle for the existing Play internal track.

The upload key is configured in ignored `signing/keystore.properties`; keep the key and credentials private and backed up. The local ADB install is suitable for DHU testing. Use the Play internal-track installation for the Mazda launcher test, as previously configured.

## Desktop testing

Start Android Auto's head unit server on the phone, then:

```sh
adb forward tcp:5277 tcp:5277
LD_LIBRARY_PATH=/tmp/dhu-libs/usr/lib:$ANDROID_HOME/extras/google/auto \
  $ANDROID_HOME/extras/google/auto/desktop-head-unit \
  --adb=localhost:5277 --config=tools/dhu-mazda.ini
```

`tools/dhu-mazda.ini` targets **1280×480 with rotary input**, using a 1280×720 transport with 240 pixels of vertical margins. This is the working target for the factory 10.25-inch 2021 CX-5 display; exact pixel geometry and density still need confirmation from this car. Mazda's US specifications confirm the display size, not its pixel resolution. Logical DPI 160 and physical DPI 133 are test assumptions. `tools/dhu-compact.ini` preserves the older 800×480 test option.

For touch diagnostics, copy the profile and set `inputmode = hybrid` and `touch = true`. DHU screenshots retain the 720-pixel transport height, with the active screen between y=120 and y=600; `cropmargins = true` crops the desktop window. Tap coordinates follow the current desktop window dimensions. For rotary testing, use `dpad rotate right/left`, then `keycode dpad_center down` and `keycode dpad_center up` with a short delay between them. Instant `dpad click` was unreliable for playback actions in this DHU build. Check the desktop mixer if functioning playback appears silent.

## Full-width local experiment — October 3, 2026

The `fullscreenProbe` build uses Android Auto's navigation host and an empty navigation overlay. In the 1280×480 DHU test, opening Car Lyrics from the app launcher expanded its surface from **770×460 to 1190×460**, removed the side panel, and displayed actual Morphe video without the large playback-status pane. The Android Auto rail remains visible. Selecting the dashboard view can still show a second card; reopen the app from the launcher for the expanded view.

This is a **local experiment**, labeled **Car Lyrics Wide Probe**. Karaoke is not a navigation app, so this variant is not suitable for Play submission. The ordinary release remains POI. Neither this result nor the existing POI prototype establishes public Play eligibility or physical Mazda compatibility. Both still use screen sharing; this is separate from the native Morphe backend investigation.

```sh
./gradlew :app:assembleFullscreenProbe
adb install -r app/build/outputs/apk/fullscreenProbe/app-fullscreenProbe.apk
```

The probe uses the same package, version code, and configured release signing key, replacing the local app while preserving its saved data. Signature compatibility with a Play-installed copy depends on Play App Signing configuration. Restart DHU/Android Auto after replacement and grant fresh Morphe sharing consent. Restore the standard build with `adb install -r app/build/outputs/apk/release/app-release.apk`; no uninstall is needed on the tested Pixel.

Video remains aspect-fitted: filling this very wide screen edge to edge would crop the top/bottom of ordinary karaoke videos. The four transport/browse buttons remain available through rotary focus; automatic hiding was not observed. A second session with touch disabled verified list selection, video rendering after reconnect, song changes, pause, and Back to browsing. Pause/resume was also checked in the hybrid diagnostic session. Twenty unit tests pass, and both the probe APK and standard release APK/AAB build successfully. Physical Mazda testing and measured A/V synchronization remain outstanding.

[Wide browse screenshot](docs/images/mazda-wide-browse.png) · [Wide Morphe video screenshot](docs/images/mazda-wide-video.png)

References: [Mazda 2021 CX-5 specifications](https://news.mazdausa.com/vehicles-2021-cx-5), [DHU display and input configuration](https://developer.android.com/training/cars/testing/dhu), [NavigationTemplate](https://developer.android.com/reference/androidx/car/app/navigation/model/NavigationTemplate).

See [Morphe implementation and follow-up plan](docs/MORPHE_ANDROID_AUTO.md). Older Spotify research is retained in [docs/PLAN.md](docs/PLAN.md).
