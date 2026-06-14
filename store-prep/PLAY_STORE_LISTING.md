# Play Store Listing Draft

## App Name

Live Media Translator

## Short Description

Floating English captions for Japanese videos, streams, and live chat.

## Full Description

Live Media Translator helps you follow Japanese media with a floating English transcript that stays over your video, stream, or live chat.

Start with free local captions. The app uses Android's approved audio capture flow, on-device Japanese speech recognition, and on-device translation after the local translation model is downloaded.

What it does:

- Shows a draggable, resizable floating transcript over other apps.
- Captures internal playback audio when Android and the source app allow it.
- Provides microphone fallback when internal audio is blocked.
- Supports free local Japanese audio to English (US) captions.
- Includes a Better local mode for cleaner phrase-level Japanese when the larger model is installed.
- Remembers the latest internal-audio test result and guides you when capture is silent.
- Keeps cloud mode disabled in this build; cloud language expansion is planned later.

Important limits:

- Some apps, videos, livestreams, devices, and DRM-protected sources block Android playback capture.
- If internal audio is silent, try the same video in a mobile browser or use microphone fallback.
- The app does not bypass DRM, download videos, or access private streams.
- Free local mode currently supports Japanese audio to English (US).

## What's New

- Added capture troubleshooting with remembered internal-audio test results.
- Dashboard now shows whether internal audio passed, was silent, errored, or has not been tested.
- Support diagnostics now include the last audio-test result.

## Screenshot Checklist

- Dashboard with setup complete.
- Setup & Settings with language controls.
- Capture Troubleshooting showing a passed preflight.
- Overlay over portrait video.
- Overlay over landscape video or live chat.

