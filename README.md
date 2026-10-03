# Car Lyrics

Private Android Auto karaoke prototype for a 2021 Mazda CX-5, using the installed **Morphe YouTube** app on the phone as its player. Car Lyrics sends selected YouTube URLs to Morphe, mirrors its video to the car surface, and controls its media session. Your existing Morphe login stays in Morphe.

## Start a session

1. On the phone, open Car Lyrics and tap **Enable Morphe controls**. Android calls this notification access; the app uses it for Morphe playback state and transport controls. It does not read or save notification contents.
2. Tap **Start Morphe sharing**, choose **Share one app**, then **YouTube Morphe**. If it is absent from recent apps, scroll the app list to Y.
3. Open **Car Lyrics** in Android Auto. Select a song or a saved queue/playlist. The car actions are Previous, Play/Pause, Next, and Browse, from left to right.
4. Keep the phone unlocked. Stop sharing from the phone app or its ongoing notification when finished. Locking, reinstalling, or ending capture requires fresh Android consent.

Morphe owns both video playback and audio. Car Lyrics captures video frames only; Android Auto routes the original audio. Browsing retains playback and the live capture session. No account credentials, capture recordings, or notification contents are stored by Car Lyrics.

## Karaoke library and queues

The car has Sing King recent uploads, a local index of 5,000 titles, title search, and curated collections. The phone searches public YouTube results from other providers too. **Queue** and **Mix** on phone results save videos locally; open Queue or Playlists on the car to select them. Next/Previous follow the list selected in Car Lyrics. Automatic queue advancement and reconciling Morphe's own autoplay are still unfinished. Live phone additions do not yet update an already selected playback list.

Lyrics are the text baked into each karaoke video. Availability depends on whether Morphe can play that video for your account and region; this is not a guarantee that every YouTube upload is accessible. The old embedded player is retained in source, but new installations default to Morphe.

## Verified on October 2, 2026

Pixel 7a running Android 17, Morphe YouTube 21.04.223, Car Lyrics **0.4.0 (13)**:

- Actual Morphe video and karaoke lyrics rendered in Android Auto Desktop Head Unit (DHU), at 1280×720 and 800×480.
- A complete 3:54 karaoke video remained visible through its ending; Morphe then autoplayed its own recommendation, confirming the queue-ownership follow-up.
- Car selections open Morphe on the phone; Pause/Play and manual Next control Morphe specifically.
- Capture survives browsing and DHU surface replacement/reconnection without reusing the consent token.
- Audio reaches the DHU stream (a short isolated stream recording measured nonzero audio after raising its test volume).
- Eleven unit tests pass, including media-session targeting, real playback status, phone display launch, and existing browse/queue flows. Signed release APK and AAB build successfully.

**Remaining verification:** physical Mazda display and Commander knob, voice search, and measured audio/video synchronization. Rotary commands did not move focus reliably in this DHU session; touch interaction worked. The 800×480 host grants the full app area. The 1280×720 host keeps a Maps side panel; the app cannot force that host layout away. Video is aspect-fitted, preserving the lyric edges.

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
- `app/build/outputs/bundle/release/app-release.aab` — version code 13, signed release bundle for the existing Play internal track.

The upload key is configured in ignored `signing/keystore.properties`; keep the key and credentials private and backed up. The local ADB install is suitable for DHU testing. Use the Play internal-track installation for the Mazda launcher test, as previously configured.

## Desktop testing

Start Android Auto's head unit server on the phone, then:

```sh
adb forward tcp:5277 tcp:5277
LD_LIBRARY_PATH=/tmp/dhu-libs/usr/lib:$ANDROID_HOME/extras/google/auto \
  $ANDROID_HOME/extras/google/auto/desktop-head-unit \
  --adb=localhost:5277 --config=tools/dhu-mazda.ini
```

`tools/dhu-mazda.ini` requests an 800×480 rotary-only head unit. For touch diagnostics, copy it and change `touch = true`. DHU tap coordinates follow the displayed desktop window dimensions, so do not reuse coordinates blindly between layouts. Check the desktop mixer: a zero-volume DHU stream can make functioning playback appear silent.

See [Morphe implementation and follow-up plan](docs/MORPHE_ANDROID_AUTO.md). Older Spotify research is retained in [docs/PLAN.md](docs/PLAN.md).
