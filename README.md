# Car Lyrics

An Android karaoke companion experiment for the connected Pixel 7a. The first milestone is a phone app that follows music already playing in Spotify or YouTube Music and displays user supplied, time stamped lyrics. Android Auto display support is a research gate, not an assumed capability.

See [the implementation plan](docs/PLAN.md) for scope, evidence, milestones, and acceptance checks.

## Current status

Planning only. No app has been built, installed, or published. This is a separate repository from `car-dashboard`.

## Reference project

`../car-dashboard` provides a working Gradle, Kotlin, and connected Pixel development setup. Its Android Auto service uses the Point of Interest category and a map surface for an instrument prototype. Those declarations are specific to that experiment; they must not be copied to make a lyric screen appear in Android Auto.
