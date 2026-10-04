# Car Lyrics Play Console materials

## App setup

- App name: `Car Lyrics`
- Package name: `com.doomslug.carlyrics`
- Default language: English (United States)
- App type: App
- Price: Free
- Category: Music & Audio
- Version: `0.5.0` / version code `16`
- Release artifact: `../app/build/outputs/bundle/release/app-release.aab`

## Store listing

### Short description

Browse, queue, and play karaoke videos from your Android Auto car screen.

### Full description

Car Lyrics is a private Android Auto karaoke prototype.

Browse Sing King and other YouTube karaoke videos with the car's rotary controller, search by song or artist, use voice input through Android Auto, explore genre and album collections, build a queue, and curate local karaoke playlists. Passengers can search YouTube and add videos from the phone.

Playback uses the installed Morphe YouTube app and requires internet access. Enable Morphe playback controls once on the phone, then choose optional Shizuku native mode or Android screen-sharing backup. Native mode keeps the phone available for passenger search and queue editing; backup mode needs Morphe visible on the unlocked phone. Video availability depends on the account, region, and Morphe playback support.

### Release notes

Added Now playing shortcuts throughout the car menus, knob-selectable timeline seeking, Repair picture, and aspect fit inside the visible car area. Added optional native Morphe playback with Shizuku so passengers can use the phone queue while car video continues. Fixed playback updates after returning from menus and automatic startup of a pending selection after screen-sharing approval.

## Tester notes

Car Lyrics has no account system. Install and set up Morphe YouTube on the phone; its own sign-in and video restrictions apply. Enable Car Lyrics Morphe controls in notification access. Choose Shizuku native mode (authorize in Shizuku) or screen-sharing backup (approve Share one app → YouTube Morphe). Connect Android Auto, open Car Lyrics, and select a video. Passengers can add videos using Queue or Mix on the phone. The Play bundle uses the existing POI host; the local Native Dev navigation-host test is a separate variant and must not be uploaded. Physical Mazda verification is still needed for this version.

## Content and policy answers

- Target audience: 13 and older
- Designed for children: No
- In-app purchases: No
- Login required: No
- App-owned advertising SDK: No
- App access credentials: None
- Permissions: internet and Android Auto surface access; optional notification listener access for Morphe media controls; foreground media-projection service for user-approved sharing backup; optional Shizuku authorization for the native display helper. Review the matching Play declarations for this implementation.

Review Play's Data safety questionnaire for public YouTube search, third-party playback, voice-provider processing, and the optional sharing/helper behavior described in the privacy policy. Car Lyrics does not operate an account system or backend and stores queue, playlist, and saved-song metadata locally on the device.

## Graphics

- `icon-512.png`: 512×512 app icon
- `feature-graphic.png`: 1024×500 feature graphic
- `phone-main.png`: 1080×2200 phone screenshot
- `phone-library.png`: 1080×1100 catalog screenshot

Existing store screenshots are from the older phone companion and should be refreshed before a public release. New DHU evidence is in `../docs/images/native-v16-*.png`; it is developer test evidence from the local navigation-host variant, not a physical Mazda screenshot or a screenshot of the standard Play host. No Play upload was performed by this update.
