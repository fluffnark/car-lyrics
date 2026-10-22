# Car Lyrics

An Android phone and Android Auto karaoke companion. Search YouTube karaoke videos, build playlists, queue songs, and play through the installed Morphe YouTube app. Morphe handles video playback, audio and Google sign-in; Car Lyrics provides browsing, saved lists and car controls.

This repository contains an experimental Android for Cars App Library surface integration. Compatibility depends on the phone, Morphe build and Android Auto host. See [verification notes](docs/RELEASE_V26.md) for what has actually been tested.

## Device prerequisites

- An Android phone running Android 10/API 29 or later. Android 11 or later supports Shizuku startup through wireless debugging; older supported phones need a computer or root for startup.
- Android Auto and a compatible head unit, or Google's Desktop Head Unit (DHU) for development.
- A compatible [Morphe YouTube](https://github.com/MorpheApp/morphe-patches) installation using package `app.morphe.android.youtube`. Open Morphe and confirm a YouTube video plays before setting up Car Lyrics. Sign in there if you want account recommendations. Morphe is installed separately and is not bundled here.
- [Shizuku](https://shizuku.rikka.app/guide/setup/) for native display mode. Root is not required. Start Shizuku with wireless debugging or ADB, and authorize the installed Car Lyrics app copy.
- Internet access for online search, recommendations, thumbnails and playback. Saved lists and the bundled title index remain available locally; videos are not downloaded for offline playback.

Optional: connect Spotify to create karaoke versions of playlists. Its connection is read-only and independent of the Google account used by Morphe.

## First-time setup

1. Install Morphe and Shizuku. Enable Android Developer options, then use Shizuku's instructions to pair/start through wireless debugging or start it over USB. Confirm Shizuku says it is running.
2. Install Car Lyrics. For a physical Android Auto head unit, use a trusted distribution source such as a Play internal testing track. [Android Auto's Unknown sources option does not apply to Car App Library apps](https://developer.android.com/training/cars/testing#test-in-real-vehicles). A local development APK is suitable for DHU testing.
3. Open Car Lyrics on the phone and tap **Setup**. Enable **Morphe controls** in Android notification access. This permits reading and controlling Morphe's media session; the app does not read message notifications.
4. Choose the Shizuku/native connection and approve its authorization prompt. The header changes to **Ready** when Morphe, playback controls and the connection are available. Official and development app copies need their own authorization and settings.
5. Connect Android Auto, open Car Lyrics from its launcher, and select a video. Set Morphe's fullscreen orientation to Landscape if its layout does not fit the car surface.

On an unrooted phone, Shizuku normally needs to be started again after a reboot. Its root-only start-on-boot setting does not remove this requirement. Start Shizuku before reconnecting Car Lyrics. Device battery/background restrictions can also stop it; follow [Shizuku's device-specific guidance](https://shizuku.rikka.app/guide/setup/).

### Screen-sharing alternative

If native mode is unavailable, open **Setup → More options → Use screen sharing**, approve Android's prompt, and choose the Morphe app. Keep Morphe visible and the phone unlocked in this mode. Android may require fresh consent after sharing ends. Native mode uses a separate display so the phone remains available for browsing.

## Daily use

- **Home** shows your signed-in Morphe recommendations as native phone and car lists, with thumbnails and normal playback actions. Android 11 or later and native/Shizuku setup are required. Refresh reloads the account feed; More recommendations reads the next portion. The local helper reads only Morphe's visible recommendation cards on its own display, then verifies each title and channel against public YouTube metadata to obtain a playable link. Unverified links stay unavailable rather than selecting another video. No Morphe cookies or account credentials are imported. Home content is kept in memory; recommendations are not limited to karaoke. Morphe UI/language changes can affect this bridge; open Morphe once to check sign-in if Home is empty.
- **Search** supports typed or spoken song/artist queries, thumbnails, provider preferences, karaoke preference and library-only filtering. A song's **⋮** menu offers Play now, Play next, Queue and Add to playlist.
- **Playlists** supports name filtering, new empty playlists, renaming, deleting, song removal and ordering. Imported Spotify lists can be copied into editable local mixes.
- **History** is available from the phone header and the car browse menu. It stores up to 200 recent video IDs/titles and available resume positions locally, including identifiable Morphe playback/autoplay while media controls are enabled. Select an entry to resume; use its menu to play from the beginning or remove it. Clear local history from the phone history options. This is independent of YouTube's account watch history.
- **Current video** on the phone and **Play current video** in car browsing return to the selected/playing video. The phone shortcut also works without an active Android Auto session and can reopen the last local history entry.
- Switching phone tabs, opening a playlist and returning from Morphe retain each phone browse/search view's position. Phone positions also survive Activity recreation. Car browsing keeps the original Screen, query and playlist page underneath playback. Scroll and rotary focus are host-controlled: the tested host restored Home's selected row but reset a scrolled Queue to its first row. Exact car scroll restoration remains a limitation; phone restoration is independent of it.

The car player keeps its primary controls compact. **Browse** returns to the list/search underneath the video. **Seek current video**, **Previous video**, **Repair picture** and Favorites are available from browsing. Returning to a video does not start it again from zero. Manual Next/Previous follows the selected list and reads updated queue contents. Automatic queue advancement and reconciliation with Morphe's own autoplay remain separate work.

### Spotify imports

Open **Playlists → Spotify Import & matching**, connect in the browser, then import playlists. **Continue matching** resumes saved work. Import options include refresh, CSV and rechecking unmatched songs. Source restrictions can produce labelled public previews; import a complete CSV to fill those gaps. Matching requires song and artist identity and cannot guarantee every song has a karaoke version. Review songs to choose a different version manually. Spotify originals are never modified.

The matching index includes 22,074 public video titles from KaraFun, Sing King, Sing2Piano and Funbox Karaoke. Local matching runs before online searches, reuses verified recording matches between playlists, and preserves manual choices. Partial preview refreshes retain previously known songs and show read/known/declared totals. A larger index improves coverage of the songs that were imported; it cannot recover songs missing from a restricted source preview.

## Recovery and updates

| Symptom | Action |
| --- | --- |
| Setup says Shizuku stopped | Start it again in Shizuku, or use USB repair below. |
| Missing authorization | Allow the correct Car Lyrics app copy in Shizuku. |
| Missing playback controls | Enable Car Lyrics Morphe controls in Android notification access. |
| Black/stale car picture | Use Repair picture; reconnect Android Auto if the host surface was lost. |
| Screen-sharing video disappears while browsing | Keep Morphe visible, or use native mode. |
| Home is empty or links are unavailable | Check Morphe sign-in, Shizuku authorization and internet access, then Refresh. The bridge requires compatible Morphe UI metadata. |
| Online search/import stops | Retry/Continue; completed matches and lists remain saved. |
| Update reports signature mismatch | Update through the same distribution/signing source. An upload-key APK cannot replace a Play-signed installation. |

Updates should install in place. Do not clear app data or uninstall to work around a signing mismatch: that removes local history, queues and playlists.

For USB diagnostics, install Android SDK Platform Tools, enable USB debugging, connect the unlocked phone and approve its debugging prompt:

```sh
export ANDROID_HOME="$HOME/Android/Sdk" # change to your SDK location
export PATH="$ANDROID_HOME/platform-tools:$PATH"
adb devices
./tools/caraoke doctor
./tools/caraoke repair
./tools/caraoke logs
```

`repair` starts the installed Shizuku service without clearing app data. `reload` builds and updates only the separate `.dev` app. Use `--serial DEVICE_SERIAL` when several devices are connected, or `--package com.doomslug.carlyrics.dev` to diagnose that copy. Python 3 is required for the helper.

## Build and development

Prerequisites: JDK 17, Android SDK Platform 36, Android SDK Build Tools 36.0.0, Platform Tools, and internet access for the first Gradle dependency download. The repository includes the Gradle wrapper.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease \
  :app:assemblePoiProbe :app:assembleRelease :app:bundleRelease
adb install -r app/build/outputs/apk/poiProbe/app-poiProbe.apk
```

| Output | Purpose |
| --- | --- |
| `app/build/outputs/apk/poiProbe/app-poiProbe.apk` | Separate `.dev` package, standard POI host, for DHU/device testing. |
| `app/build/outputs/apk/release/app-release.apk` | Release APK; signing must match its existing installation. |
| `app/build/outputs/bundle/release/app-release.aab` | Standard release bundle for the configured Play test track. |
| `nativeProbe` / `fullscreenProbe` variants | Private navigation-host experiments; do not upload these variants as a karaoke release. |

Release signing is configured by an ignored `signing/keystore.properties` file. It contains `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; `storeFile` is resolved from the repository root. Keep keys/credentials out of Git and back them up privately. Without that local configuration, Gradle does not produce a release signed with your upload key. The debug-based POI variant can still be built for local testing. Publishing requires separate Play Console access; an upload signing key does not grant that access.

### Desktop Head Unit

Install the DHU from Android Studio's SDK Tools. Enable Android Auto developer mode on the phone and start its head unit server, then run:

```sh
adb forward tcp:5277 tcp:5277
"$ANDROID_HOME/extras/google/auto/desktop-head-unit" \
  --adb=localhost:5277 --config=tools/dhu-mazda.ini
```

The included wide rotary profile is a test target; use the geometry/input configuration appropriate for your head unit. `tools/dhu-compact.ini` provides a compact alternative. On Linux, install the C++ runtime libraries required by the DHU if it reports missing `libc++.so.1`/`libc++abi.so.1`. See [Google's DHU setup](https://developer.android.com/training/cars/testing/dhu).

## Release and verification records

The current internal testing release is **0.9.0 (26)**, published October 22, 2026. Development builds use the same version code with a separate package; building or pushing source does not publish a Play update. See [current verification](docs/RELEASE_V26.md), [v25 playlist/matching verification](docs/RELEASE_V25.md), [native playback](docs/MORPHE_NATIVE.md), and [earlier recovery tests](docs/RELEASE_V20.md).

Physical head-unit behavior, cabin microphones, A/V synchronization and device reboot recovery need device-specific checks. The use of a POI custom surface is experimental and does not establish public Play eligibility.

[Privacy policy](docs/privacy-policy.md) · [Implementation notes](docs/MORPHE_ANDROID_AUTO.md)
