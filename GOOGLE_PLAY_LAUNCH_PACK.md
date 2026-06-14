# Live Media Translator - Google Play Launch Pack

## Positioning

Use **Live Media Translator** publicly. Avoid using "YouTube" in the app name or primary listing copy so the app does not imply affiliation, endorsement, or special access to a third-party platform.

Core promise:

> Translate Japanese media audio into a floating English transcript overlay.

Plain-English limits:

> Internal audio capture depends on Android and the source app. Some apps, streams, devices, and DRM-protected playback may block capture. Mic fallback is included.

## Store Listing Copy

App name, 30-character limit:

```text
Live Media Translator
```

Short description, 80-character limit:

```text
Floating English captions for Japanese media audio.
```

Alternate short descriptions:

```text
Translate Japanese media audio into floating English captions.
```

```text
Follow Japanese videos and streams with a floating transcript.
```

Full description:

```text
Live Media Translator helps you follow Japanese media audio with a floating English transcript overlay.

Start captions, approve Android's capture prompt, then switch to your video, stream, or browser playback. When internal audio capture is not available, use mic fallback and play audio through your speaker.

Features

- Floating transparent transcript over other apps
- Drag and resize the caption box
- Japanese speech recognition with English translation
- Internal audio preflight before relying on capture
- Fast local mode for lightweight captions
- Better local mode for cleaner phrase-level transcription
- Mic fallback for apps that block internal audio
- No account required for local modes

Important limits

Android decides whether internal audio capture is allowed. Some apps, streams, devices, and DRM-protected playback may be silent. Live Media Translator does not bypass DRM, download videos, or access private media streams.

Local modes run speech recognition and translation on your device after the required models are downloaded. Model downloads require an internet connection.
```

## Screenshot Captions

1. **Start captions in seconds**  
   Grant permissions, choose a model, and launch the floating overlay.

2. **Check audio first**  
   Run preflight to see whether Android can hear the media before using internal captions.

3. **Read while you watch**  
   Keep a transparent English transcript over videos, streams, or browser playback.

4. **Move it anywhere**  
   Drag, resize, clear, and adjust text size from the overlay.

5. **Fast or better local modes**  
   Choose lightweight captions or a larger local model for cleaner phrase cuts.

6. **Mic fallback included**  
   If an app blocks internal audio, translate audio playing through your speaker.

## In-App Disclosure Copy

Use this before any future cloud mode:

```text
Cloud Mode sends live audio to our translation provider to create captions. Cloud minutes are metered and may incur charges. Use Local Mode if you do not want audio sent off-device.
```

Use this for internal capture:

```text
Android controls internal audio capture. Some apps, streams, and protected playback may be silent. If internal capture does not work, try mic fallback.
```

## Data Safety Draft

Current local-only app:

- Audio: processed on device for local captions.
- Network: used for downloading speech and translation models.
- Account data: not collected.
- Payment data: not collected.
- Diagnostics: not collected unless a future debug-report feature is added.

Future paid cloud mode:

- Audio would be sent to a cloud translation provider for app functionality.
- Purchases and entitlements would be handled through Google Play Billing.
- Account or device identifiers may be needed for minute balances, abuse prevention, and purchase recovery.
- Add in-app consent before cloud audio begins.
- Privacy policy must clearly separate Local Mode from Cloud Mode.

## Monetization Guardrails

- Sell prepaid cloud minutes rather than unlimited access.
- Use Google Play Billing for in-app digital services.
- Show estimated minutes remaining during a session.
- Add auto-stop timers: 15, 30, 60 minutes.
- Run a free preflight audio test before spending cloud minutes.
- Keep local modes free as the fallback experience.
- Planned packs: 5 trial minutes after preflight, 60 minutes for $4.99, 180 minutes for $14.99, and 400 minutes for $29.99.

## Support-Minimizing Launch Plan

Beta first:

- Release to internal testing, then closed testing.
- Add a first-run "known limits" screen.
- Keep the in-app "Test Internal Audio Capture" button prominent.
- Keep the in-app "Share Support Diagnostics" button before public launch.
- Email support only; no live chat or Discord.
- Publish with "beta" language until device coverage is proven.

Support FAQ topics:

- Why is internal audio silent?
- Why is translation delayed?
- Why does mic fallback hear room noise?
- How do prepaid minutes work?
- How do I stop a session?
- How do I delete my data?

## Play Console Notes

Google Play currently lists these main store listing limits:

- App name: 30 characters
- Short description: 80 characters
- Full description: 4000 characters

For paid cloud minutes, Play-distributed apps generally need Google Play Billing for digital features and services.

Primary references:

- Store listing setup: https://support.google.com/googleplay/android-developer/answer/9859152
- Store listing best practices: https://support.google.com/googleplay/android-developer/answer/13393723
- Payments policy: https://support.google.com/googleplay/android-developer/answer/10281818
- Android playback capture: https://developer.android.com/media/platform/av-capture
