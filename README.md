# Car Lyrics

An Android Auto car-screen karaoke experiment for a 2021 Mazda CX-5 and a connected Pixel 7a. The app will let the user choose albums and playlists, create lyric-ready mixes, and play only selected tracks with matched timed lyrics through Spotify Premium. Lyrics appear on the car display and all car controls use the Mazda Commander knob.

See [the product workflow](docs/PRODUCT_WORKFLOW.md), [implementation plan](docs/PLAN.md), and [open source options](docs/OPEN_SOURCE_OPTIONS.md).

## Current status

Planning only. No app has been built, installed, or published. This is a separate repository from `car-dashboard`.

## Reference project

`../car-dashboard` provides a working Gradle, Kotlin, Android Auto surface, Mazda knob, and connected Pixel development setup. Its Android Auto service uses the Point of Interest category and a map surface for an instrument prototype. That demonstrates a private technical prototype path, but Google's POI category is intended for POI functionality and a map, so distribution remains a separate feasibility question.
