# Development notes

## Build app locally

Android project root for Gradle and Android Studio:

- `Android/src`

If you want to use the in-app Hugging Face download flow, you need to configure your own HuggingFace Developer Application ([official doc](https://huggingface.co/docs/hub/oauth#creating-an-oauth-app)).

This is required for model download functionality, but it is not required if you only want to test the local LAN server with a manually imported compatible model file.

After you've created a developer application:

1. In [`ProjectConfig.kt`](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt), replace the placeholders for `clientId` and `redirectUri` with the values from your HuggingFace developer application.

1. In [`app/build.gradle.kts`](https://github.com/google-ai-edge/gallery/blob/c1b50e160a66d5ea2ec2d8d8e63088b3cc0761bc/Android/src/app/build.gradle.kts#L41-L44), modify the `manifestPlaceholders["appAuthRedirectScheme"]` value to match the redirect URL you configured in your HuggingFace developer application.

## Build commands

From `Android/src`:

1. Build a debug APK with `./gradlew assembleDebug` on Unix-like systems.
2. Build a debug APK with `.\gradlew.bat assembleDebug` on Windows.

## JDK note

Use a supported JDK for the Android/Gradle toolchain in this project.

- Recommended: JDK 17 or JDK 21.
- Do not assume a very new JDK works automatically.

During validation in this repository, Java 26 was visible to Gradle but the build failed immediately before Kotlin compilation, so treat Java 26 as unsupported for now unless the Android/Gradle stack is updated.

## Current first-test-version server contract

The current server slice is intentionally small and aimed at first LAN testing.

- `GET /healthz` reports server liveness.
- `GET /readyz` reports whether the server is ready, busy, missing a selected model, or still initializing.
- `GET /v1/models` returns known models.
- `POST /v1/chat/completions` runs against the currently selected and initialized model from the app UI.
- Only one active inference request is supported at a time in this version.
- Request-level model switching is not supported yet.
