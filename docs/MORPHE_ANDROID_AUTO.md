# Morphe and Android Auto investigation

Checked September 20, 2026 against [Morphe Patches](https://github.com/MorpheApp/morphe-patches) and the Morphe documentation.

## Pixel verification (September 20, 2026)

On the connected Pixel 7a, Morphe YouTube is installed as `app.morphe.android.youtube` with Morphe GmsCore/Revanced GMS. A Sing King karaoke URL launched successfully through Morphe and played while signed in. The app exposes an active Android `MediaSession`; Android media key events paused and resumed the video, and its metadata reported `Queen - Bohemian Rhapsody (Karaoke Version)`.

The Morphe package also exposes `MainAppMediaBrowserService`, so Android Auto can discover its media controls if the host accepts the service. Its current session did not expose a queue (`queueTitle=null`, `size=0`), so a queue in Car Lyrics would need to remain app-owned and launch one selected URL at a time.

## What Morphe provides

Morphe patches the official YouTube APK. The current patch list includes an **Automotive** form-factor option, fullscreen scaling, background playback, media notification controls, and a video queue. The Automotive option changes the YouTube UI layout; it does not add an Android Auto `CarAppService` or a car launcher activity.

The repository has a **Bypass certificate checks** patch specifically under YouTube Music's Android Auto patches. That is for YouTube Music's native Android Auto media integration. I did not find a corresponding Android Auto integration for the regular YouTube package.

Non-root patched installs use Morphe MicroG/GmsCore support. Google sign-in happens inside the patched app on the Pixel; Car Lyrics should never handle or store the Google password.

## What can work

### 1. Morphe as the phone-side player

Install patched YouTube plus Morphe MicroG, sign in on the Pixel, and verify that playback exposes a normal Android `MediaSession`. Car Lyrics could then:

- send a selected YouTube URL to Morphe;
- observe the active title, artist/channel, duration, and position;
- display lyrics and queue controls through our Android Auto service.

This gives Morphe ownership of playback and login. It does **not** put Morphe's video pixels on the Mazda display. Android Auto media sessions provide audio metadata and controls, not an arbitrary phone app window.

### 2. Add a car service to a Morphe fork

This would require modifying the patched YouTube APK to include a `CarAppService`, car manifest metadata, Android Auto host compatibility, and a custom surface/player bridge. It is a substantial binary-patching project because Morphe is a patch set applied to a version-specific stock APK, not a standalone YouTube source tree. Every YouTube APK update could break the patch anchors and the car integration.

The result would also need a distinct package/signature and would not be a normal Play-distributed YouTube app. Morphe Patches are GPLv3 with additional attribution and branding conditions; derivative code must preserve notices and use distinct branding.

### 3. Mirror Morphe's phone screen

MediaProjection or an external screen-capture path could capture Morphe on the phone, but Android Auto does not provide a general third-party screen-share channel to put those pixels on a Mazda head unit. A capture stream would still need a supported Android Auto parked/video surface, repeated consent, and a custom transport. It would not automatically provide reliable Mazda knob control. It is unsuitable for the “phone stays in pocket” flow.

Car Lyrics now contains an opt-in development experiment for this route. It requests MediaProjection consent on the phone, asks the user to select Morphe in Android's single-app picker, starts a foreground capture service, and feeds the captured frames into the existing `SurfaceCallback` virtual display. This is a hypothesis test for the Pixel/Mazda combination, not a claim that Android Auto will accept arbitrary mirrored pixels. The capture is intentionally off by default.

## Recommended experiment

1. Install Morphe YouTube and Morphe MicroG on the Pixel without sharing credentials with the development environment.
2. Sign in and play a known karaoke video on the phone.
3. Verify its package, media session, title, position, pause, next, and queue behavior with ADB.
4. Connect Android Auto and check whether Morphe YouTube appears in the launcher. Expect it not to appear unless it has a supported car service; the Morphe Android Auto instructions primarily apply to YouTube Music.
5. If the media session is usable, add a Car Lyrics “Play in Morphe” bridge. Keep our existing embedded player as the display path and use Morphe for videos that reject embedding.

If the requirement is that Morphe's actual video pixels appear on the 2021 Mazda display, the forked car-service route or an approved Android Auto parked-video app is required. A normal Morphe APK installed on the phone cannot expose its full UI through Android Auto by itself.

## Relevant references

- [Morphe patch list and supported versions](https://github.com/MorpheApp/morphe-patches)
- [Morphe Android Auto troubleshooting](https://github.com/MorpheApp/morphe-documentation/blob/main/docs/morphe-resources/troubleshooting_questions.md#34-patched-app-does-not-work-with-android-auto)
- [Android Auto parked app support](https://developer.android.com/training/cars/parked/auto)
- [Android for Cars app categories](https://developer.android.com/design/ui/cars/guides/foundations/cal)
