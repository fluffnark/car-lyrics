# Car Lyrics

A private Android Auto karaoke prototype for a 2021 Mazda CX-5. It lists recent uploads from [Sing King Karaoke](https://www.youtube.com/@singkingkaraoke) on the car screen. Turn and press the Mazda Commander knob to choose a video, then use the host-rendered Previous, Next, Play/Pause, and Browse controls. The selected karaoke video plays in an official YouTube iframe rendered to Android Auto's custom surface. **No phone taps, Morphe launch, screen-sharing prompt, account, or API key are required during the car flow.**

## Parked car flow

1. Connect the Pixel to Android Auto and open **Car Lyrics** on the Mazda display.
2. Browse the recent Sing King videos using the Commander knob. Choose one while parked.
3. Use the Commander knob to control playback or return to browsing. Browsing stops the video. If the car API reports movement, the app hides the player and returns to browsing.

The catalog uses the channel's public Atom feed, which contains only recent uploads (currently about 15); a larger searchable catalog is future work. Videos need an internet connection, must allow embedding, and may include ads. Morphe is no longer in the playback path because capturing its app window required phone consent every session.

## Build and test status

Run `./gradlew :app:assembleDebug` with JDK 17 and Android SDK API 36. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. On this machine:

```sh
JAVA_HOME=/home/doomslug/.local/share/mise/installs/java/17.0.2 \
ANDROID_HOME=/home/doomslug/.local/share/mise/installs/android-sdk/23.0 \
./gradlew :app:assembleDebug
```

The project builds and is installed on the connected Pixel 7a. The public Sing King feed was fetched and parsed successfully from the development machine. **Car display playback, YouTube WebView rendering, audio routing, and Mazda knob behavior still require a parked real-car test.** The app's POI surface is an experimental private route: [Android documents virtual displays on car surfaces for map-capable apps](https://developer.android.com/training/cars/apps/library/draw-maps), while [video apps on Android Auto are still early access](https://developer.android.com/training/cars/whats-new). A host could reject this use of the POI category. Vehicle speed may also be unavailable to third-party apps; the parked-only selection action is the primary gate.

The previous screen-sharing APK and Spotify/lyric-library design are superseded. Their research remains in [docs/PLAN.md](docs/PLAN.md), [docs/PRODUCT_WORKFLOW.md](docs/PRODUCT_WORKFLOW.md), and [docs/OPEN_SOURCE_OPTIONS.md](docs/OPEN_SOURCE_OPTIONS.md).

## References

- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference) and [embedded player requirements](https://developers.google.com/youtube/terms/required-minimum-functionality)
- [Android Auto parked-only clicks](https://developer.android.com/reference/androidx/car/app/model/ParkedOnlyOnClickListener)
- [Android car hardware speed API](https://developer.android.com/training/cars/apps/library/car-hardware-api)
