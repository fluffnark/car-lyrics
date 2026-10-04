# Car Lyrics

Private Android Auto karaoke prototype for a 2021 Mazda CX-5, using the installed **Morphe YouTube** app on the phone as its player. Car Lyrics opens selected YouTube URLs in Morphe on a dedicated native display, renders that display on the car surface, and controls Morphe’s media session. Android screen sharing remains available as a backup. Your existing Morphe login stays in Morphe.

## Current test build — 0.5.1 (17), October 4, 2026

**Car Lyrics POI Dev** is installed alongside the Play-installed **v16** on the Pixel 7a. This test variant uses the same POI category and layout as the standard release. It replaces the previous Native Dev test package (`com.doomslug.carlyrics.dev`) while preserving its permissions and local queue. The Play app and its data are separate and unchanged.

- **Voice search:** a microphone shortcut is available in every car menu and the player. Open it, then select Android Auto’s “Select and speak” field. Only submitted phrases start searches; partial transcripts no longer rebuild the input screen.
- **Shorter car flows:** results stay inside SearchTemplate, and Browse returns to the root instead of stacking menus. The host receives the root Back template before opening Search; player row titles remain stable across loading/playing/paused changes so updates count as refreshes.
- **Live queue:** phone additions appear in the open car queue. Selections use video IDs, and Next/Previous read the current queue order rather than an old snapshot.
- **Phone queue controls:** Queue adds once; Manage queue offers Play on car, Move up, and Remove. Queue/Mix taps update in place. The keyboard now resizes the layout and closes when Search is submitted.
- **Stable scrolling:** car song lists use static icons; background thumbnail churn and playback status changes no longer redraw browsing screens.
- **Now playing, knob seek, and picture repair** remain available. Native Morphe still plays on its own display without screen-sharing consent.

Upload the signed **0.5.1 (17)** standard release AAB to the existing Play internal track, then update **Car Lyrics** through Play for Mazda testing. The locally installed POI Dev copy is for the DHU. No Play upload credentials are configured here.

## Start a session

1. On the phone, open **Car Lyrics** (or **POI Dev** for DHU testing) and enable **Morphe controls**. Android calls this notification access; Car Lyrics uses Morphe’s playback state and transport controls, not notification contents.
2. Ensure **Shizuku** says it is running. In Car Lyrics choose **Enable native Morphe • Shizuku** and allow access. These permissions and native mode are already configured for both packages on the connected Pixel.
3. In the desktop Android Auto emulator, open **Car Lyrics POI Dev**, then choose a song, Queue, or playlist. Selection launches the installed Morphe on its separate 1280×720 display. Use the microphone, Play/Pause, Next, and Browse controls. Previous is in Browse.
4. Use **Now playing** to return from any menu, **Seek current video** to change position, and **Repair picture** if the image needs recovery. The phone’s fixed **Return to current video** button returns the car UI too.

For the backup, choose **Use screen-sharing backup → Start Morphe sharing → Share one app → YouTube Morphe**. Android requires fresh consent when capture ends. In backup mode, Morphe must remain visible on the unlocked phone; opening passenger search can hide its captured video.

Morphe owns video decoding, audio, and your existing login. Car Lyrics does not store account credentials or record frames. Native mode needs Shizuku running; automatic startup after the next phone reboot and locked-phone behavior still need testing. See [native implementation and test evidence](docs/MORPHE_NATIVE.md).

## Install for the physical Mazda

Car App Library apps must be installed from a trusted source for real-vehicle testing. Android Auto’s Unknown sources option does **not** cover them; see [Google’s real-vehicle testing requirements](https://developer.android.com/training/cars/testing#test-in-real-vehicles). The POI Dev installation above was verified in the DHU, not as a sideloaded Mazda installation.

1. Upload `app/build/outputs/bundle/release/app-release.aab` (**0.5.1 / 17**, package `com.doomslug.carlyrics`) to the existing **Internal testing** track and roll it out.
2. On the Pixel, use the existing tester account/link and **update Car Lyrics through Google Play**. The separate POI Dev copy can remain installed.
3. Open the updated **Car Lyrics** on the phone. Enable **Morphe controls** if requested, then choose **Enable native Morphe • Shizuku** and authorize **Car Lyrics**. POI Dev’s permission and native-mode preference belong to a different package and do not transfer.
4. Check Shizuku says it is running, connect the Mazda, and open the Play-installed **Car Lyrics**. Select a song. Keep Morphe’s fullscreen preference set to Landscape.

The release bundle includes native Morphe, Now playing, seek, recovery and visible-area fitting. The POI Dev test variant now uses the same POI host as this standard release; the optional nativeProbe variant uses the experimental navigation host. Trusted installation alone does not verify the remaining physical-car behavior.

Automatic Play uploads require Google Play Developer API credentials with access to this app and its testing releases. None are configured in this repository as of October 4. The local signing key signs the bundle; it does not grant Play Console access.

## Latest verification

On the Pixel 7a (Android 17), with the **1280×480 rotary-only Mazda DHU profile**, POI host, and `restrict all`:

- Full “Queen Bohemian Rhapsody karaoke” voice request returned matching results. Selecting one played the matching Morphe video.
- A phone-added “Don’t Stop Me Now” appeared in the car queue and played when selected with rotary controls. Phone Play on car switched to “Somebody To Love”; Morphe metadata confirmed both selections as PLAYING.
- Passenger additions, reorder, and removal persisted; the open car queue updated without reopening. Phone search rows kept their positions after Queue taps with the keyboard dismissed.
- Genre and 80s song lists stayed at the same scrolled rows across 12-second waits while playback continued. Playback status no longer invalidates menus.
- Native playback reported no active MediaProjection session.
- The final template-quota changes are covered by regression tests; the repeat-search device retest was interrupted when the Pixel reached 1% battery.
- **29 unit tests** pass. Signed release APK/AAB builds pass.

The user reported successful native video on the physical Mazda with v16. These v17 changes still need that physical-car retest and a real cabin microphone check. Android Auto owns host restrictions; passing these DHU paths does not guarantee every host message is eliminated. Measured A/V synchronization remains unverified.

See [v17 test notes](docs/RELEASE_V17.md) and [native backend evidence](docs/MORPHE_NATIVE.md). This is a private surface experiment, not a claim of public Play eligibility.

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
./gradlew :app:testDebugUnitTest :app:assemblePoiProbe :app:assembleRelease :app:bundleRelease
```

Outputs:

- `app/build/outputs/apk/poiProbe/app-poiProbe.apk` — **Car Lyrics POI Dev**, same layout/category as release, separate test package.
- `app/build/outputs/apk/nativeProbe/app-nativeProbe.apk` — side-by-side **Car Lyrics Native Dev**, local navigation-host experiment. Do not upload this variant to Play.
- `app/build/outputs/apk/release/app-release.apk` — standard POI prototype, locally upload-key signed. It cannot replace the Play-signed app by ADB on this Pixel.
- `app/build/outputs/bundle/release/app-release.aab` — **0.5.1 (17)**, signed release bundle for the existing Play internal track; not uploaded by this session. The POI Dev test build matches the standard host category.

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
