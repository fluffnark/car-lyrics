# Native display investigation probe

Separate development APK, **not included in the Car Lyrics application or release bundle**. Tested October 2, 2026 on Pixel 7a, Android 17/API 37. See [findings and implementation plan](../../docs/MORPHE_NATIVE.md).

## Build

From the Car Lyrics repository root, with Java 17, Android SDK 36/build tools and `ffmpeg` available:

```sh
# Generate this disposable debug key once. Do not commit it.
keytool -genkeypair -keystore tools/native-display-probe/probe-debug.keystore \
  -storepass android -keypass android -alias androiddebugkey \
  -dname 'CN=Native Display Probe' -keyalg RSA -keysize 2048 -validity 3650
bash tools/native-display-probe/create-video.sh
./gradlew -p tools/native-display-probe assembleDebug
adb install -r 'tools/native-display-probe/build/outputs/apk/debug/Native display probe-debug.apk'
```

The generated test video and debug key are ignored by Git. The video is a synthetic silent test pattern, not a YouTube download. Use `adb -s SERIAL` instead of `adb` when multiple devices are attached.

## Native Morphe experiment

1. Open the probe's surface receiver:

```sh
adb shell am start -S -n com.doomslug.carlyrics.displayprobe/.ProbeActivity --es phase shell-display
adb push 'tools/native-display-probe/build/outputs/apk/debug/Native display probe-debug.apk' /data/local/tmp/car-lyrics-native-probe.apk
```

2. Start the display host in its own terminal. It requires the already authorized ADB shell; it does not root the phone or obtain a MediaProjection token.

```sh
adb shell CLASSPATH=/data/local/tmp/car-lyrics-native-probe.apk app_process / com.doomslug.carlyrics.displayprobe.ShellDisplayHost
```

It prints `Native shell display=<ID> uid=2000 flags=132`. Keep this process running. A private display with `OWN_CONTENT_ONLY | TRUSTED` receives its output Surface from the probe over Binder. No default-display mirror is requested. The exported probe provider accepts only UID 2000 and only the `surface` operation; it is an isolated lab component, not a production bridge.

3. In a second terminal, use the **printed display ID** in place of `DISPLAY_ID`:

```sh
adb shell am start --display DISPLAY_ID -a android.intent.action.VIEW \
  -d 'https://www.youtube.com/watch?v=G9LQ3HWPB2Q' \
  -n app.morphe.android.youtube/com.google.android.apps.youtube.app.application.Shell_UrlActivity
adb shell am start --display DISPLAY_ID -a android.intent.action.VIEW \
  -d 'https://www.youtube.com/watch?v=j5FH4FfRxFA' \
  -n app.morphe.android.youtube/com.google.android.apps.youtube.app.application.Shell_UrlActivity
adb shell input -d DISPLAY_ID keyevent KEYCODE_MEDIA_PAUSE
adb shell input -d DISPLAY_ID keyevent KEYCODE_MEDIA_PLAY
adb shell dumpsys media_projection
adb shell dumpsys activity activities
adb shell dumpsys media_session
```

`dumpsys media_projection` must report `null` to reproduce the clean no-capture experiment. Stop any existing sharing session through its app first. Verify actual changing video frames and that Morphe's activity remains on the printed display; a media state alone is insufficient. Media key commands are a lab convenience and may target the active media session; production controls must explicitly target Morphe.

4. Cleanup: pause playback, move Morphe back to the phone, then interrupt the display-host terminal with Ctrl-C and stop the probe:

```sh
adb shell input -d DISPLAY_ID keyevent KEYCODE_MEDIA_PAUSE
adb shell am start --display 0 -n app.morphe.android.youtube/.morphe_black_1
# Ctrl-C in the display-host terminal now.
adb shell am force-stop com.doomslug.carlyrics.displayprobe
adb shell rm /data/local/tmp/car-lyrics-native-probe.apk
```

The launcher alias above matches the installed Morphe build used in this investigation. Resolve the enabled launcher component again if Morphe's branding/version changes. The shell helper uses hidden Android APIs and is only verified on this Pixel's Android 17 build. It models the privileges a Shizuku user service would have; **Shizuku integration itself has not been implemented or tested**.

The probe's receiving Surface belongs to its phone Activity. Switching away can destroy it. A production receiver must live with the car session and use a stable rendering input across host-surface changes.

## Other probe phases

```sh
adb shell am start -S -n com.doomslug.carlyrics.displayprobe/.ProbeActivity --es phase plain
adb shell am start -S -n com.doomslug.carlyrics.displayprobe/.ProbeActivity --es phase embedded-presentation
adb shell am start -S -n com.doomslug.carlyrics.displayprobe/.ProbeActivity --es phase native-video
adb logcat -d -s NativeDisplayProbe:I '*:S'
```

- `plain`: normal app creates an own-content virtual display; its own Activity launch is denied. Installed Morphe's launch preflight is also denied.
- `embedded-presentation`: native Presentation renders, but an Activity with `allowEmbedded=true` still cannot launch. A Presentation does not establish an Activity's UID presence on the display.
- `native-video`: a separate application process receives a Surface through Messenger/Binder and renders the synthetic local video using MediaPlayer. Proves the basic native video surface handoff, not a Morphe decoder patch.

Do not use the ordinary app-owned, untrusted display as the final Morphe host: ADB can initially put Morphe there, but its internal URL-to-player redirect was rejected and returned to the phone. The shell-created trusted display passed that same song-change test.
