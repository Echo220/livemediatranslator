# Live Media Translator

Personal Android app prototype for Japanese media audio to English captions over another app.

## What It Does

- Shows a draggable, resizable transparent transcript box over other apps.
- Opens to a focused dashboard after setup, with separate setup/settings and capture troubleshooting screens.
- Starts with Android internal playback capture using `MediaProjection` and `AudioPlaybackCaptureConfiguration`.
- Falls back to microphone capture when the source app/browser blocks internal audio.
- Uses Vosk offline Japanese speech recognition for fast lightweight captions.
- Adds an optional Better mode using sherpa-onnx SenseVoice plus local voice activity detection for cleaner Japanese, punctuation, and phrase-level context.
- Uses ML Kit on-device Japanese to English text translation after the translation model is downloaded.
- Displays a rolling English transcript that appends stable phrases instead of retranslating the whole paragraph on every update.
- Adds an internal audio preflight and remembers the latest pass/silent/error result for troubleshooting and support diagnostics.
- Includes source and caption language controls. Free local mode currently supports Japanese audio to English (US); auto-detect and other language pairs are cloud-mode placeholders.
- Mentions planned metered cloud mode, but hides paid packs until cloud billing/backend are enabled.
- Uses no paid AI token API.

## Important Limits

Android only allows internal playback capture when the source app allows it. Some media apps, streams, devices, and DRM-protected playback may be silent. If internal audio is silent, try the same video or livestream in a mobile browser, then start internal capture again. If that still fails, use mic fallback and play the audio through speakers.

This app does not bypass DRM, download videos, or read private media streams. It only uses Android's user-approved capture APIs.

## First Run

1. Open the app.
2. The app opens **Setup & Settings** until the required local checks are complete.
3. Grant microphone permission.
4. Grant floating overlay permission.
5. Tap **Download Fast Vosk Model**. This downloads `vosk-model-small-ja-0.22` from Vosk, about 48 MB.
6. Optional: tap **Download Better SenseVoice Model**. This downloads the free local SenseVoice int8 model plus VAD, about 226 MB.
7. Keep language set to Japanese -> English (US) for free local captions.
8. Tap **Test Internal Audio Capture** and approve Android's capture prompt.
9. Switch to the target media app and play audio for 15 seconds.
10. Return to the dashboard, then tap **Start Fast Internal Captions** or **Start Better Internal Captions**.
11. Approve Android's screen/audio capture prompt.
12. Switch to your media app and play Japanese audio.

The first caption session may also download the ML Kit Japanese/English translation model. After that, speech recognition and ML Kit translation run locally on the device.

## Build

This workspace includes a verified debug APK at:

```text
LiveMediaTranslator-debug.apk
```

Open this folder in Android Studio:

```text
outputs/live-youtube-translator
```

Then build and install the `app` module on an Android 10+ arm64 device.

Minimum target for this prototype is Android 10 because internal audio capture was added in Android 10. A real physical device is recommended; emulators often do not behave like a normal phone for media audio capture.

## How To Use

- Fast mode: lowest setup and most immediate partial captions. Draft text only affects the tail of the transcript.
- Better mode: heavier local model, phrase-level captions, usually cleaner Japanese and punctuation before translation. It now cuts phrases sooner for lower latency.
- Dashboard: compact daily-use screen for setup status, last audio-test status, starting captions, fallback mode, and diagnostics.
- Setup & Settings: permissions, model downloads, language choices, preflight, cloud roadmap, and troubleshooting.
- Capture Troubleshooting: shows the latest internal-audio test result, gives the next action, and exposes internal or mic fallback buttons in one place.
- Internal mode: best attempt at TransGull-style captions. Start it, approve the Android prompt, then switch to your media app.
- Capture preflight: checks whether Android can hear the current source before you rely on it.
- Language: keep Japanese -> English (US) selected for free local captions. Auto-detect and other source/target choices are shown for the planned cloud mode and will disable local start buttons.
- Mic fallback: useful when internal capture is silent. Put the phone near the speaker or play through TV/phone speakers.
- Drag the transcript box by its top row.
- Drag **Resize** at the bottom-right to change the box size.
- Use **A-** and **A+** to adjust transcript text size.
- Use **Fit** if rotation or fullscreen video pushes the box near an edge.
- Use **Clear** to reset the visible transcript.
- Tap **Stop** in the transcript box or notification to stop capture.

The transcript is optimized for stable reading: completed phrases are appended, while fast drafts only appear at the end. This avoids expensive full-paragraph retranslations and keeps the text from constantly rewriting itself.

## Monetization Prep

Version `0.7.0` starts preparing the app for a metered cloud mode without shipping cloud calls or secrets in the APK.

- Local captions remain free.
- Planned cloud trial is 5 minutes after audio capture passes preflight.
- Planned packs are hidden in-app until Google Play Billing and backend entitlements are live.
- Cloud mode is disabled in this build until Google Play Billing, a backend entitlement ledger, and server-side AI calls exist.
- The app includes a **Share Support Diagnostics** button to reduce support friction when internal audio capture is blocked.

## Release

Play Store release setup lives in:

```text
RELEASE_BUILD.md
PLAY_CONSOLE_READY_CHECKLIST.md
SUPPORT_AGENT_PLAN.md
store-prep/
```

Do not create the Play Console app until the final package id is confirmed. The current application id is `com.personal.livetranslator`.

## Files

- `app/src/main/java/com/personal/livetranslator/MainActivity.java`: setup screen and permissions.
- `app/src/main/java/com/personal/livetranslator/CaptionService.java`: foreground audio capture, Vosk/SenseVoice recognition, ML Kit translation.
- `app/src/main/java/com/personal/livetranslator/CaptionOverlay.java`: floating transcript window.
- `app/src/main/java/com/personal/livetranslator/ModelInstaller.java`: Vosk and SenseVoice model downloader/unpacker.
- `app/src/main/java/com/personal/livetranslator/MonetizationConfig.java`: planned cloud-minute pack metadata and feature flags.

## References

- Android playback capture: https://developer.android.com/media/platform/av-capture
- Vosk models: https://alphacephei.com/vosk/models
- ML Kit translation: https://developers.google.com/ml-kit/language/translation/android
- sherpa-onnx Android/Java: https://k2-fsa.github.io/sherpa/onnx/java-api/anroid-java.html
- SenseVoice model docs: https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
