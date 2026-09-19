# Car Lyrics

An Android Auto car-screen karaoke experiment for a 2021 Mazda CX-5 and a connected Pixel 7a. The first milestone is a local prototype that follows Spotify playback, draws time stamped lyrics on the car display, and works with the Mazda Commander knob. Spotify Premium is available for development.

See [the implementation plan](docs/PLAN.md) for scope, evidence, milestones, and acceptance checks, and [open source options](docs/OPEN_SOURCE_OPTIONS.md) for existing apps and lyric catalogs.

## Current status

Planning only. No app has been built, installed, or published. This is a separate repository from `car-dashboard`.

## Reference project

`../car-dashboard` provides a working Gradle, Kotlin, Android Auto surface, Mazda knob, and connected Pixel development setup. Its Android Auto service uses the Point of Interest category and a map surface for an instrument prototype. That demonstrates a private technical prototype path, but Google's POI category is intended for POI functionality and a map, so distribution remains a separate feasibility question.
