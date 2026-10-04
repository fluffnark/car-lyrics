# Car Lyrics

Private Android Auto karaoke prototype for a 2021 Mazda CX-5, using the installed **Morphe YouTube** app on the phone as its player. Car Lyrics opens selected YouTube URLs in Morphe on a dedicated native display, renders that display on the car surface, and controls Morphe’s media session. Android screen sharing remains available as a backup. Your existing Morphe login stays in Morphe.

## Current test build — 0.5.0 (16), October 4, 2026

**Car Lyrics Native Dev** is installed alongside the Play-installed v14 on the Pixel 7a. Open **Native Dev** in Android Auto to test these changes. It uses package `com.doomslug.carlyrics.dev` and a separate local queue. The Play copy and its saved data were preserved; its Play signing key differs from our local upload key.

- **Now playing** returns to the existing player from every car menu/search screen. Back uses Android Auto’s actual screen stack, and playback/status survive menu navigation.
- **Seek current video**: turn the knob to choose a time, then press to seek. Ordinary songs have 10-second steps; long videos use larger steps to fit the host’s list limit.
- **Repair picture** restarts Morphe, recreates its native display, and restores the selected video’s current time (or the beginning if it has ended).
- Video fits Android Auto’s reported visible rectangle in both split and expanded layouts. Reopen the app from the launcher for the widest host view; the app cannot force the dashboard to hide every other card.
- **Native Morphe** starts from a car song selection with no sharing picker once Shizuku and playback controls are authorized. Passenger search and queue editing can stay open on the phone while car video continues.

## Start a session

1. On the phone, open **Car Lyrics Native Dev** and enable **Morphe controls**. Android calls this notification access; Car Lyrics uses Morphe’s playback state and transport controls, not notification contents.
2. Ensure **Shizuku** says it is running. In Car Lyrics choose **Enable native Morphe • Shizuku** and allow access. These permissions and native mode are already configured for Native Dev on the connected Pixel.
3. Open **Car Lyrics Native Dev** in Android Auto, then choose a song, Queue, or playlist. Selection launches the installed Morphe on its separate 1280×720 display. Use Previous, Play/Pause, Next, and Browse from the car.
4. Use **Now playing** to return from any menu, **Seek current video** to change position, and **Repair picture** if the image needs recovery. The phone’s fixed **Return to current video** button returns the car UI too.

For the backup, choose **Use screen-sharing backup → Start Morphe sharing → Share one app → YouTube Morphe**. Android requires fresh consent when capture ends. In backup mode, Morphe must remain visible on the unlocked phone; opening passenger search can hide its captured video.

Morphe owns video decoding, audio, and your existing login. Car Lyrics does not store account credentials or record frames. Native mode needs Shizuku running; automatic startup after the next phone reboot and locked-phone behavior still need testing. See [native implementation and test evidence](docs/MORPHE_NATIVE.md).

## Latest verification

On the Pixel 7a (Android 17), with a **1280×480 rotary-only Mazda DHU profile**:

- Native Morphe video rendered with `dumpsys media_projection` reporting `null`.
- Phone search found Queen karaoke; adding it to Queue and selecting it in the car played the matching Morphe video without a sharing prompt. The phone remained available for another app.
- Seek to **0:20** produced a Morphe media-session position of **20,000 ms**. **Repair picture** restored fullscreen and resumed at the saved **122,119 ms** without phone interaction.
- Browse, Queue, Seek, Back, and Now playing worked with DHU `restrict all`. No driving-block message appeared in these tested paths. The app has no Park checks; Android Auto retains its own host restrictions. This does not establish that every host message is eliminated.
- In dashboard split mode the full surface was 1190×460, but its visible rectangle narrowed to `Rect(24,88–682,448)`. Video fit that rectangle and stayed clear of the media card.
- **25 unit tests** pass, covering screen-stack navigation, resumed-screen status, seek capability/clamping, delayed capture approval, search, and viewport fitting.

[Native player](docs/images/native-v16-player.png) · [Knob seek](docs/images/native-v16-seek.png) · [Queue and Now playing](docs/images/native-v16-queue.png)

Physical Mazda/Commander testing, the real cabin microphone, and measured audio/video synchronization remain outstanding. This private surface experiment does not establish public Play eligibility.

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

That v14 screen-sharing limitation is addressed by the opt-in native backend in v16 above; it still applies when using screen-sharing backup.

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
./gradlew :app:testDebugUnitTest :app:assembleNativeProbe :app:assembleRelease :app:bundleRelease
```

Outputs:

- `app/build/outputs/apk/nativeProbe/app-nativeProbe.apk` — side-by-side **Car Lyrics Native Dev**, local navigation-host experiment. Do not upload this variant to Play.
- `app/build/outputs/apk/release/app-release.apk` — standard POI prototype, locally upload-key signed. It cannot replace the Play-signed v14 by ADB on this Pixel.
- `app/build/outputs/bundle/release/app-release.aab` — **0.5.0 (16)**, signed release bundle for the existing Play internal track; not uploaded by this session. The standard POI host differs from the locally tested navigation host.

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

This is a **local experiment**, labeled **Car Lyrics Wide Probe**. Karaoke is not a navigation app, so this variant is not suitable for Play submission. The ordinary release remains POI. Neither this result nor the existing POI prototype establishes public Play eligibility or physical Mazda compatibility. This historical v15 test used screen sharing. V16 adds native rendering to both variants, with side-by-side testing through `nativeProbe`.

```sh
./gradlew :app:assembleFullscreenProbe
adb install -r app/build/outputs/apk/fullscreenProbe/app-fullscreenProbe.apk
```

The older `fullscreenProbe` uses the original package and local signing key. It cannot replace the currently Play-signed app on this Pixel. Use `nativeProbe` for side-by-side testing and preserve the Play copy; restart DHU/Android Auto after an APK update. Native mode keeps its Shizuku authorization across compatible updates.

Video remains aspect-fitted: filling this very wide screen edge to edge would crop the top/bottom of ordinary karaoke videos. The four transport/browse buttons remain available through rotary focus; automatic hiding was not observed. A second session with touch disabled verified list selection, video rendering after reconnect, song changes, pause, and Back to browsing. Pause/resume was also checked in the hybrid diagnostic session. Twenty unit tests pass, and both the probe APK and standard release APK/AAB build successfully. Physical Mazda testing and measured A/V synchronization remain outstanding.

[Wide browse screenshot](docs/images/mazda-wide-browse.png) · [Wide Morphe video screenshot](docs/images/mazda-wide-video.png)

References: [Mazda 2021 CX-5 specifications](https://news.mazdausa.com/vehicles-2021-cx-5), [DHU display and input configuration](https://developer.android.com/training/cars/testing/dhu), [NavigationTemplate](https://developer.android.com/reference/androidx/car/app/navigation/model/NavigationTemplate).

See [Morphe implementation and follow-up plan](docs/MORPHE_ANDROID_AUTO.md). Older Spotify research is retained in [docs/PLAN.md](docs/PLAN.md).
