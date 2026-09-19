# Car Lyrics

A private, experimental Android Auto karaoke mirror for a 2021 Mazda CX-5 and a Pixel 7a. Morphe YouTube plays an instrumental karaoke video on the phone; Android's screen-sharing prompt lets Car Lyrics capture that app window and draw it on the car display. The Mazda Commander knob can focus **Show video**, **Hide video**, and **Stop sharing**. The host's parked-only action gates Show video, and an available vehicle-speed reading blanks the car image after movement is reported.

This is a technical experiment, not an Android Auto video app supported for ordinary publication. The Car App Library surface used here is documented for maps in navigation, POI, and weather apps, not for video. Android Auto video apps are currently an early-access path. The Mazda host may reject this use of the POI category. The phone's screen-sharing API may also return black video for protected content. Neither the Mazda display nor Morphe capture has been verified yet.

## Try it while parked

1. Install `app/build/outputs/apk/debug/app-debug.apk` on the phone. Morphe YouTube (`app.morphe.android.youtube`) must already be installed.
2. Open **Car Lyrics** on the phone. Open Morphe through the app, optionally using a YouTube karaoke video URL. Choose a karaoke upload without lead vocals and with large, static lyrics.
3. Return to Car Lyrics and tap **Start sharing**. In Android's prompt choose **a single app** and select Morphe YouTube, then return to Morphe and play the video. Android asks again each sharing session.
4. With the car parked and Android Auto connected, open **Car Lyrics** on the Mazda display. Turn and press the Commander knob on **Show video**. Use **Hide video** or **Stop sharing** with the knob when finished.

The knob controls the mirror, not Morphe's search or playback UI. Video selection and playback controls remain on the phone in this first spike. Audio routing through Android Auto and video visibility require a real-device check.

## Build

Use JDK 17 and an Android SDK with API 36. From this directory run `./gradlew :app:assembleDebug`. On this development machine:

```sh
JAVA_HOME=/home/doomslug/.local/share/mise/installs/java/17.0.2 \
ANDROID_HOME=/home/doomslug/.local/share/mise/installs/android-sdk/23.0 \
./gradlew :app:assembleDebug
```

The initial APK was built and installed on the connected Pixel 7a on 2026-09-19. Its phone activity launched. The app was not validated in the Mazda or a working Desktop Head Unit session. Stop and reassess this mirror approach if Android Auto blocks the surface, Morphe frames are protected, or the car screen fails to hide content reliably when moving.

## Sources and earlier research

- [Android for Cars app categories](https://developer.android.com/training/cars) and [video early access](https://developer.android.com/training/cars/whats-new)
- [Map surface API](https://developer.android.com/training/cars/apps/library/draw-maps) and [parked-only click listener](https://developer.android.com/reference/androidx/car/app/model/ParkedOnlyOnClickListener)
- [Android MediaProjection](https://developer.android.com/media/grow/media-projection) and [car hardware speed](https://developer.android.com/training/cars/apps/library/car-hardware-api)
- [Morphe project](https://github.com/MorpheApp/morphe-manager)

The prior Spotify and lyric-library exploration is in [docs/PLAN.md](docs/PLAN.md), [docs/PRODUCT_WORKFLOW.md](docs/PRODUCT_WORKFLOW.md), and [docs/OPEN_SOURCE_OPTIONS.md](docs/OPEN_SOURCE_OPTIONS.md). Those are alternatives for later, not features of this APK.
