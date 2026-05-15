# Mr. Duck

Mr. Duck is an Android-only on-device AI companion demo forked from Google AI Edge Gallery. It runs a multimodal Gemma model locally, shows a rubber-duck avatar, records push-to-talk audio, samples front-camera context without showing a preview, and replies as a small companion.

## Status

This is a focused prototype, not the full Gallery app. Most Gallery screens, model browsing UX, notifications, Firebase startup hooks, and storefront links have been removed or hidden while the codebase is being trimmed.

## Features

- Android companion app branded as `Mr. Duck`.
- Local LiteRT-LM inference with `Gemma-4-E2B-it`.
- Multimodal turns using microphone audio plus chronological front-camera snapshots.
- SceneView-powered `rubber_duck.glb` avatar.
- Minimal Compose UI with hold-to-talk, reset, duck glow, and latest-response display.
- On-device privacy after the model is downloaded.

## Requirements

- Android 12 or newer.
- Device with about 8 GB RAM for the bundled Gemma model.
- Internet access for first-run model download from Hugging Face.
- Camera and microphone permissions.

## Build

The Android project lives in `Android/src`.

```bash
cd Android/src
./gradlew :app:assembleRelease
```

If your shell does not already point at a compatible JDK, set `JAVA_HOME` before running Gradle:

```bash
cd Android/src
JAVA_HOME="/tmp/opencode/jdk-21" ./gradlew :app:assembleRelease
```

The release APK is written to:

```text
Android/src/app/build/outputs/apk/release/app-release.apk
```

For a shareable copy to upload to a web host:

```bash
mkdir -p dist
cp Android/src/app/build/outputs/apk/release/app-release.apk dist/mr-duck-release.apk
```

Note: the current Gradle release build is signed with the local debug keystore. That is installable for sideloading, but not a production signing identity. Configure a real release signing config before publishing through an app store or committing to long-term update compatibility.

The APK bundles the app and duck graphics. The Gemma model is downloaded by the app on first run; no API key is required for the current public model URL. Android users still need to allow sideloading/installing unknown apps, and the first launch needs internet access plus about 2.6 GB of model storage.

Run the companion-focused tests:

```bash
cd Android/src
./gradlew :app:testDebugUnitTest --tests "com.google.ai.edge.gallery.customtasks.companion.*"
```

## Key Files

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/companion/` contains the companion task, UI, model config, audio recorder, and avatar code.
- `Android/src/app/src/main/assets/models/rubber_duck.glb` is the duck avatar asset.
- `Android/src/app/src/main/AndroidManifest.xml` contains the current app label, permissions, and launcher setup.
- `Android/src/app/build.gradle.kts` contains Android dependencies and build configuration.

## Attribution

- Based on Google AI Edge Gallery.
- Rubber Duck by J-Toastie, CC BY via Poly Pizza.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
