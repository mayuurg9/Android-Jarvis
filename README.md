# JARVIS for Android

A native Kotlin Android assistant inspired by MARK LVI's visual language and modular-agent ideas, rebuilt for Android.

## Included in this foundation
- Native Android app and cinematic dark HUD.
- OpenRouter chat-completions integration and editable model ID.
- API key encrypted at rest with Android Keystore (AES/GCM).
- Bounded tool-calling agent loop.
- Starter tools: launch an installed app, open an HTTP(S) URL, save a private note, read accessible on-screen text.
- Optional AccessibilityService, enabled manually by the user.
- Push-to-talk speech recognition and Android text-to-speech.

## Build
Open this repository as an Android/Gradle project in CodeAssist or Android Studio. Use JDK 17 and Android SDK 35. Android Gradle Plugin 8.7.3 and Kotlin 2.0.21 are configured. The Gradle wrapper is not included; use the IDE's installed Gradle distribution.

## First launch
1. Enter your own OpenRouter API key in SETUP.
2. Select a model ID available to your OpenRouter account. Default: openai/gpt-4o-mini.
3. Send a message to test the connection.
4. Grant microphone permission to use voice input.
5. To enable screen reading, manually enable JARVIS Screen Access in Android Accessibility settings.

## Security and limits
- Never commit API keys, signing keys, or local machine configuration.
- The API key is encrypted with a key held by Android Keystore. A compromised or unlocked device can still expose data while the app is running.
- Accessibility access is powerful. Enable it only if you understand and want the feature.
- This initial version has no always-on wake word, arbitrary unattended tapping, messaging, purchases, or hidden background control.
- Android restricts background execution and sensitive actions.
- This is a first implementation foundation, not a claim of zero bugs. Build and test on the target device before relying on it.
