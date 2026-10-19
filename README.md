# Car Lyrics

Spotify karaoke imports: open **Albums → Spotify playlists → Connect Spotify**, then **Refresh playlists**. All playlist pages are inventoried; available title-and-artist karaoke matches appear as albums on phone and car. **Resume matching** keeps saved work, **Import CSV** handles complete exported lists, and import details let you choose corrections through search. Public embed previews are labelled whenever full coverage cannot be verified. See [v22 validation and release record](docs/RELEASE_V22.md).


Private Android Auto karaoke prototype for a 2021 Mazda CX-5, using the installed **Morphe YouTube** app on the phone as its player. Car Lyrics opens selected YouTube URLs in Morphe on a dedicated native display, renders that display on the car surface, and controls Morphe’s media session. Android screen sharing remains available as a backup. Your existing Morphe login stays in Morphe.

## Current Play release — 0.7.0 (22), October 18, 2026

**Car Lyrics 0.7.0 (22)** is published on the existing Play internal testing track. Spotify playlist import is available in Albums; the release passed 51 tests, debug/release lint and signature checks. See [v22 verification](docs/RELEASE_V22.md) for installation and head-unit evidence. The separate POI Dev copy remains available for development.

- **Recovery:** accurate setup status distinguishes stopped Shizuku, missing authorization, missing Morphe, and missing playback controls. Open Shizuku and Repair picture/reconnect are available on the phone. A live Shizuku restart can reconnect an existing car session and restore its selected song, position and paused state.
- **Minimal UI:** compact connection status, flat thumbnail rows, Explore / Queue / Albums tabs, and a single song menu. Search filters and setup details open only when needed. The car player has four main controls; seek, repair, and favorites are in Browse.
- **Passenger search:** thumbnails, karaoke preference, provider preference, and library-only search; each result has **Play now**, **Play next**, **Queue**, and **Add to album**. Named albums/playlists are local, persist across reload, and appear on both phone and car. Repeated adds do not remove songs.
- **USB tools:** `./tools/caraoke doctor`, `repair`, `reload`, and `logs` locate the SDK/phone and current Shizuku starter. Reload updates only the separate Dev copy with `adb install -r`, preserving queues and settings. Use `--serial` when several devices are connected.

Shizuku was stopped after the phone had rebooted. Its installed **Start on boot (root)** setting does not establish automatic startup on this unrooted phone. Start Shizuku through wireless debugging after a reboot, or use the USB repair command. Shizuku itself must be running before Car Lyrics can attach.

See [v20 design and verification](docs/RELEASE_V20.md) and [v19 recovery details](docs/RELEASE_V19.md). Earlier [v18 layout/favorites evidence](docs/RELEASE_V18.md) remains applicable.

## Start a session

1. On the phone, open **Car Lyrics** (or **POI Dev** for DHU testing) and enable **Morphe controls**. Android calls this notification access; Car Lyrics uses Morphe’s playback state and transport controls, not notification contents.
2. Ensure **Shizuku** says it is running. In Car Lyrics tap **Setup** at the top right, follow the primary connection action, and allow Shizuku access if requested. These permissions and native mode are already configured for both packages on the connected Pixel.
3. In the desktop Android Auto emulator, open **Car Lyrics POI Dev**, then choose a song, Queue, or playlist. Selection launches the installed Morphe on its separate 1280×720 display. Use the microphone, Play/Pause, Next, and Browse controls. Previous is in Browse.
4. Use **Now playing** to return from any menu, **Seek current video** to change position, and **Repair picture** if the image needs recovery. Tap the phone’s now-playing bar to return the car UI too.

For the backup, open **Setup/Ready → More options → Use screen sharing**, then choose **Share one app → YouTube Morphe** in Android’s picker. Android requires fresh consent when capture ends. In backup mode, Morphe must remain visible on the unlocked phone; opening passenger search can hide its captured video.

Morphe owns video decoding, audio, and your existing login. Car Lyrics does not store Morphe account credentials or record frames. Optional Spotify connection tokens are encrypted with Android Keystore. Native mode needs Shizuku running. The installed start-on-boot setting is root-only; on this phone, use wireless Start or USB repair after reboot. Native video continued with this Pixel locked and its physical display off in DHU testing. See [native implementation and test evidence](docs/MORPHE_NATIVE.md).

## Install for the physical Mazda

Car App Library apps must be installed from a trusted source for real-vehicle testing. Android Auto’s Unknown sources option does **not** cover them; see [Google’s real-vehicle testing requirements](https://developer.android.com/training/cars/testing#test-in-real-vehicles). The POI Dev installation above was verified in the DHU, not as a sideloaded Mazda installation.

1. The official **Car Lyrics 0.6.1 (20)** is already installed through Play on the Pixel. Open **Car Lyrics**, whose phone header should say Ready. The separate POI Dev copy is for development.
2. Ensure Shizuku is running, connect the Mazda, and open **Car Lyrics** from Android Auto. Select a song. Keep Morphe’s fullscreen preference set to Landscape.
3. If phone setup is requested later, tap Setup and follow the primary action. Each app copy has its own native-mode preference and Shizuku authorization.

For future updates, publish the signed release AAB on the existing Internal testing track and update through Play. The local upload-key APK cannot replace the Play-signed app by ADB.
The release bundle includes native Morphe, Now playing, seek, recovery and visible-area fitting. The POI Dev test variant now uses the same POI host as this standard release; the optional nativeProbe variant uses the experimental navigation host. Trusted installation alone does not verify the remaining physical-car behavior.

Automatic Play uploads require Google Play Developer API credentials with access to this app and its testing releases. API credentials are not configured in this repository; the October 18 release was published manually through the signed-in Play Console browser. The local signing key signs the bundle; it does not grant Play Console access.

## Latest verification

Pixel 7a / Android 17 / Morphe 21.04.223 / Shizuku, with the standard POI host and a 1280×480 rotary Mazda DHU profile:

- 43 unit tests, debug lint, release APK/AAB builds, and signature verification pass.
- Phone search thumbnails, song actions, queue ordering, and saved albums were exercised on the device.
- DHU voice search recognized recorded speech, returned live provider results, and rotary selection opened the matching native video.
- The simplified four-control player, Browse seek, phone return to current video, and Play next → rotary Next worked with driving restrictions enabled.
- V20 restored paused and playing sessions across three Shizuku restarts, recovered a killed helper, and kept video visible with the Pixel locked and its physical display off. Compact 800×480 and wide 1280×480 DHU reconnects passed.

See [v20 observations and remaining limits](docs/RELEASE_V20.md). Physical Mazda hardware, cabin microphone recognition, measured A/V synchronization, and reboot auto-start remain separate checks. The user reported native playback on the physical Mazda with the previous release. Automatic end-of-video queue advancement remains unfinished.

Previous voice, passenger queue, and scrolling evidence is in [v17 test notes](docs/RELEASE_V17.md). The v17 repeat-search quota device retest remains incomplete. This is a private surface experiment, not a claim of public Play eligibility.

## Karaoke library and queues

The car has Sing King recent uploads, a local index of 5,000 titles, curated collections, and public YouTube search across providers. Car and phone search both accept song, artist, album, genre, and provider names. **Queue** and **Add to album** on phone results save videos locally; open Queue or Playlists on the car to select them. Next/Previous follow the list selected in Car Lyrics. Automatic queue advancement and reconciling Morphe's own autoplay are still unfinished. Manual Next/Previous read the latest queue contents, including phone additions.

Lyrics are the text baked into each karaoke video. Availability depends on whether Morphe can play that video for your account and region; this is not a guarantee that every YouTube upload is accessible. The old embedded player is retained in source, but new installations default to Morphe.

## Voice search

On the car: **Search YouTube → microphone in the search field → say a song and artist → select a result**. For example, say “Queen Bohemian Rhapsody karaoke” or “find karaoke for Halo by Beyoncé.” Include “karaoke” to favor instrumental versions. Results use normal Android Auto rows; selecting one goes through the existing Morphe playback controls. This is the microphone inside Car Lyrics search, not a global “Hey Google, play in Car Lyrics” integration.

On the phone: tap the microphone beside search, speak, then tap a result to play or open its three-dot menu for **Queue**, **Play next**, **Play now**, or **Add to album**. Typing and the keyboard Search key work too. Canceling recognition leaves the existing query alone. Android Auto/the phone speech provider handles audio; Car Lyrics receives the transcript. See the [privacy policy](docs/privacy-policy.md).

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
- `app/build/outputs/bundle/release/app-release.aab` — **0.7.0 (22)**, signed release bundle published to the existing Play internal track on October 18; installed through Play on the Pixel. The POI Dev test build matches the standard host category.

The upload key is configured in ignored `signing/keystore.properties`; keep the key and credentials private and backed up. The local ADB install is suitable for DHU testing. Use the Play internal-track installation for the Mazda launcher test, as previously configured.

## Desktop testing

Start Android Auto's head unit server on the phone, then:

```sh
adb forward tcp:5277 tcp:5277
LD_LIBRARY_PATH=/tmp/dhu-libs/usr/lib:$ANDROID_HOME/extras/google/auto \
  $ANDROID_HOME/extras/google/auto/desktop-head-unit \
  --headless --adb=localhost:5277 --config=tools/dhu-mazda.ini
```

`tools/dhu-mazda.ini` targets **1280×480 with rotary input**, using a 1280×720 transport with 240 pixels of vertical margins. This is the working target for the factory 10.25-inch 2021 CX-5 display; exact pixel geometry and density still need confirmation from this car. Mazda's US specifications confirm the display size, not its pixel resolution. Logical DPI 160 and physical DPI 133 are test assumptions. `tools/dhu-compact.ini` preserves the older 800×480 test option.

DHU 2.1 in headless mode gave the most reliable console rotary input in the October 5 session. If the launcher stops accepting knob input, reconnect the DHU before diagnosing app callbacks. For touch diagnostics, copy the profile and set `inputmode = hybrid` and `touch = true`. DHU screenshots retain the 720-pixel transport height, with the active screen between y=120 and y=600; `cropmargins = true` crops the desktop window. Tap coordinates follow the current desktop window dimensions. For rotary testing, use `dpad rotate right/left`, then `keycode dpad_center down` and `keycode dpad_center up` with a short delay between them. Instant `dpad click` was unreliable for playback actions in this DHU build. Check the desktop mixer if functioning playback appears silent.

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
