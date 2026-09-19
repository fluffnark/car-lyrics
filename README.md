# Car Lyrics

A private Android Auto karaoke prototype for a 2021 Mazda CX-5. It lists recent uploads from [Sing King Karaoke](https://www.youtube.com/@singkingkaraoke) on the car screen. Turn and press the Mazda Commander knob to choose a video, then use the host-rendered Previous, Next, Play/Pause, Save, and Browse controls. The selected karaoke video is designed to play in an official YouTube iframe rendered to Android Auto's custom surface. **No phone taps, Morphe launch, screen-sharing prompt, account, or API key are required during the car flow.** Playback on the actual Mazda display remains unverified.

## Parked car flow

1. Connect the Pixel to Android Auto and open **Car Lyrics** on the Mazda display.
2. Browse the recent Sing King videos using the Commander knob. Four videos are shown per page with thumbnails and More/Previous page rows. Choose one while parked.
   Use **Search** to type or speak a song, artist, album, or genre through Android Auto's search UI. **Genres & albums** opens curated Sing King collections such as Pop, Rock, Country, Musicals, K-pop, 80s, 90s, Duets, Taylor Swift's *Lover*, and Melanie Martinez's *K-12*.
3. Use the Commander knob to pause, resume, skip, save a song, or return to browsing. The Saved/Recent header action switches lists. Hardware Back returns from the player to browsing.
4. Browsing stops the video. If the car API reports movement, the app hides the player and returns to browsing.

When a video is selected, **Queue** adds or removes it from the persistent Up Next list. **Add to mix** adds or removes it from `My karaoke mix` and the Saved list. Browse the Queue or Playlists rows at the top of the car screen to review a set, then choose any queued or playlist song to play it.

The catalog uses the channel's public Atom feed for fresh uploads and includes a generated local index of 5,000 Sing King titles plus curated playlist memberships. The index is title metadata only; playback still needs internet. Run `python3 tools/update_catalog.py` with `yt-dlp` to refresh it. Queue, starter playlists, and saved songs persist locally on the phone. Videos must allow embedding and may include ads. Morphe is no longer in the playback path because capturing its app window required phone consent every session.

## Build and test status

Run the tests and build the signed release APK and AAB with JDK 17 and Android SDK API 36. On this machine:

```sh
JAVA_HOME=/home/doomslug/.local/share/mise/installs/java/17.0.2 \
ANDROID_HOME=/home/doomslug/.local/share/mise/installs/android-sdk/23.0 \
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease
```

The signed release APK is at `app/build/outputs/apk/release/app-release.apk` and the signed release AAB is at `app/build/outputs/bundle/release/app-release.aab` (version `0.3.2`, version code `5`). Automated tests cover feed parsing, saved-song persistence, browse and pagination, search entry, selecting a song, playback actions, error retry, and hardware Back. The player now requests media audio focus before loading a video and releases it when browsing stops. The phone companion screen was rendered and visually reviewed. The connected Pixel 7a fetched and cached 15 live Sing King videos. **Car display playback, YouTube WebView rendering, audio routing, voice search, and Mazda knob behavior still require a real-car test.**

The release build uses the local upload key in `signing/car-lyrics-upload.jks`, configured by the ignored `signing/keystore.properties`. Back up that key and its password before using Play Console for future updates. Play App Signing can re-sign the distributed APKs with Google's app-signing key.

### Make the app appear in the Mazda launcher

An `adb install` alone does not establish real-car availability for a Car App Library app. [Android's testing guidance](https://developer.android.com/training/cars/testing) says the Android Auto **Unknown sources** developer option does not apply to Car App Library apps. Install the debug AAB or APK through [Google Play Internal App Sharing](https://support.google.com/googleplay/android-developer/answer/9844679) (or an Internal Test track) using a Play Console account, then enable Internal App Sharing in Play Store on the Pixel and use the generated download link. Play accepts debuggable AABs for Internal App Sharing and re-signs them with its internal-sharing key. Keep the Play-installed copy on the phone for the Mazda test; updating it with `adb install` changes its trusted install source.

The service declares the POI category, app icon, `template` capability, and Google Android Auto host certificates, including the SHA-256 certificate measured from the Android Auto APK on this Pixel. The phone's Android Auto developer mode and Unknown sources setting are already enabled. The Desktop Head Unit currently disconnects during startup on this machine, before the launcher appears, so it has not proven that Car Lyrics launches.

Since you have Play Console access, upload `app/build/outputs/bundle/release/app-release.aab` to **Internal testing** or **Internal app sharing**. Add the Pixel's Google account as a tester, install the Play-provided link on the phone, then reconnect Android Auto. Keep that Play-installed copy for the Mazda test; an `adb install` replacement changes the trusted install source.

The app's POI surface is an experimental private route: [Android documents virtual displays on car surfaces for map-capable apps](https://developer.android.com/training/cars/apps/library/draw-maps), while [video apps on Android Auto are still early access](https://developer.android.com/training/cars/whats-new). A host could reject this use of the POI category. For this development build, all selection and playback actions are intentionally enabled while moving so the Mazda flow can be diagnosed; restore parked-only actions before any driving use.

The previous screen-sharing APK and Spotify/lyric-library design are superseded. Their research remains in [docs/PLAN.md](docs/PLAN.md), [docs/PRODUCT_WORKFLOW.md](docs/PRODUCT_WORKFLOW.md), and [docs/OPEN_SOURCE_OPTIONS.md](docs/OPEN_SOURCE_OPTIONS.md).

## References

- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference) and [embedded player requirements](https://developers.google.com/youtube/terms/required-minimum-functionality)
- [Android Auto parked-only clicks](https://developer.android.com/reference/androidx/car/app/model/ParkedOnlyOnClickListener)
- [Android car hardware speed API](https://developer.android.com/training/cars/apps/library/car-hardware-api)
