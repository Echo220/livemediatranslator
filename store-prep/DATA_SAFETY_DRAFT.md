# Google Play Data Safety Draft

Last updated: June 5, 2026

This is a draft based on the current free local build. Re-check before Play Store submission, especially after adding billing, accounts, analytics, ads, crash reporting, or cloud captions.

Google Play states that developers are responsible for providing accurate Data safety disclosures, and the privacy policy should be consistent with the Data safety section.

## Current Build Assumptions

- No account system.
- No ads.
- No analytics SDK.
- No crash reporting SDK.
- No backend.
- No enabled cloud caption API.
- Audio is processed locally for captions.
- Diagnostics are only shared if the user chooses to share them.
- Internet is used for model downloads.

## Suggested Form Answers

### Does your app collect or share any required user data types?

Draft answer: No, for the current free local build, assuming no SDK transmits user data off-device beyond user-initiated diagnostics and model downloads.

Review warning: Google may treat SDK network behavior as collection if user data is transmitted off-device. Verify ML Kit model download behavior and any future SDKs before submitting.

### Is all user data collected by your app encrypted in transit?

Draft answer: Not applicable if declaring no collected user data.

If cloud mode is enabled later, answer yes only if all transmitted user data uses HTTPS or equivalent encryption.

### Does your app provide a way for users to request that their data is deleted?

Draft answer: Not applicable for the current free local build if no data is collected by the developer.

If cloud mode, accounts, purchases, or support uploads are added, provide a deletion request process.

## Sensitive Permissions To Explain In Review Notes

- Microphone: used for mic fallback captions when internal capture is blocked.
- Screen/audio capture prompt: used only after Android user approval to capture internal playback audio.
- Draw over other apps: used to display the floating transcript over videos and live chat.
- Internet: used for speech/translation model downloads.
- Foreground service: used to keep caption capture running while another app is visible.

## Future Cloud Mode Changes

If cloud captions are enabled, update the form for any audio, transcript text, device identifiers, purchase data, diagnostics, or support data sent to the backend or AI provider.

At that point, likely disclosed data types may include:

- Audio files or voice recordings, if audio chunks leave the device.
- App interactions or diagnostics, if logged server-side.
- Purchase history, if entitlements are tracked by your backend.
- User IDs, if accounts are added.

## Official References

- Data safety form: https://support.google.com/googleplay/android-developer/answer/10787469
- User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Developer Program Policy: https://support.google.com/googleplay/android-developer/answer/15402170

