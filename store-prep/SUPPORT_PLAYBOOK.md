# Support Playbook

## Goal

Reduce one-off support by making users follow the in-app capture troubleshooting flow first.

## First Response Template

Please open Live Media Translator, tap Capture Troubleshooting, and check the Last Audio Test result.

If it says passed, start internal captions from the same source.

If it says silent, try the same video in a mobile browser and run the test again. If it is still silent, use mic fallback.

If it says error, grant microphone and floating overlay permissions, restart the video, then run the test again.

If it still fails, tap Share Support Diagnostics and send the report.

## Common Cases

### Internal Audio Test Passed

Internal capture works for that source. If captions are blank after passing, check that the local model is installed and Japanese audio is actually playing.

### Internal Audio Test Silent

The source probably blocks Android playback capture. Ask the user to try a mobile browser. If browser capture is also silent, recommend mic fallback.

### Mic Fallback Works But Internal Does Not

The app is functioning. The media source is blocking internal capture.

### Overlay Missing

Ask the user to re-grant floating overlay permission, then tap Fit in the overlay after rotating the phone.

### Better Mode Disabled

The Better SenseVoice model is not installed. Use Fast mode or download the Better model on Wi-Fi.

## Do Not Promise

- Do not promise internal audio works for every app.
- Do not promise DRM-protected media can be captured.
- Do not promise cloud mode until billing, backend, and privacy updates are complete.

