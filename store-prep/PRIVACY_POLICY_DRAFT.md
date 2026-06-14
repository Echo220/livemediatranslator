# Privacy Policy Draft

Last updated: June 5, 2026

This is a draft for review before publishing.

## Overview

Live Media Translator provides floating translated captions for media playing on your Android device. The current build is designed for local use and does not require an account.

## Data The App Accesses

The app may access:

- Microphone audio, when you use microphone fallback.
- Internal media playback audio, only after you approve Android's screen/audio capture prompt.
- Basic device and app state needed to run captions, foreground services, overlays, model downloads, and troubleshooting.

## How Audio Is Used

Audio is used to generate captions. In the current free local build, speech recognition and translation run on the device after required models are installed.

The app does not intentionally store recordings of your audio.

The app does not download videos, bypass DRM, or access private media streams.

## Model Downloads

The app uses internet access to download speech and translation models. Model downloads are needed so captions can run locally after setup.

## Support Diagnostics

The app includes a Share Support Diagnostics button. This creates a plain text report that may include:

- App version.
- Device manufacturer and model.
- Android SDK version.
- Permission status.
- Installed model status.
- Selected caption languages.
- Last internal-audio test result.

Diagnostics are only shared if you choose to send them using Android's share sheet.

## Cloud Mode

Cloud mode is not enabled in the current build. If cloud features are added later, this policy must be updated before release to explain what audio or text is sent off-device, how it is processed, how long it is kept, and how users can request deletion.

## Third-Party Services And Libraries

The app uses third-party libraries for local speech recognition, on-device translation, model downloads, and Android app functionality. Some model downloads may be provided by third-party hosts or Google services.

Before publishing, review each SDK and model download provider for current data practices.

## Contact

Support email: [add support email]

## References For Review

- Google Play Data safety form: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play Developer Program Policy: https://support.google.com/googleplay/android-developer/answer/15402170

