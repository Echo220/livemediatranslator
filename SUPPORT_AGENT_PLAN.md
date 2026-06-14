# Support Agent Plan

Codex can act as a support copilot when support messages, diagnostics, Play reviews, or logs are pasted here or connected through a tool.

## Recommended Support Setup

- Create a support email address.
- Use the in-app Share Support Diagnostics button.
- Ask users to include:
  - The app/browser they played media in.
  - Whether internal audio test passed or was silent.
  - Whether mic fallback worked.
  - Their diagnostics report.

## Triage Buckets

### Internal Audio Passed, Captions Blank

Likely model, language, or Japanese speech recognition issue.

Actions:

- Confirm Japanese -> English (US) is selected.
- Confirm Fast or Better model is installed.
- Ask for diagnostics.

### Internal Audio Silent, Mic Works

Likely source app blocks Android playback capture.

Actions:

- Ask user to try the same video in a mobile browser.
- Recommend mic fallback if browser is also silent.
- Do not promise a workaround for DRM/protected sources.

### Overlay Missing Or Offscreen

Likely overlay permission, rotation, or window position issue.

Actions:

- Re-grant overlay permission.
- Start captions again.
- Tap Fit in overlay.

### Better Mode Disabled

Better model missing.

Actions:

- Download Better SenseVoice model on Wi-Fi.
- Use Fast mode meanwhile.

## Support Reply Macro

Thanks for the report. Please open Live Media Translator, tap Capture Troubleshooting, run Test Internal Audio while the target video is playing, then tap Share Support Diagnostics and send that report.

If the test says silent, try the same video in a mobile browser. If it is still silent there, use mic fallback because that source is blocking Android playback capture.

