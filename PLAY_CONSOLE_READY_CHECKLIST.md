# Play Console Ready Checklist

Use this before creating the production release.

## 1. Decide App Identity

- [ ] Confirm final app name: `Live Media Translator`.
- [ ] Confirm final package id before creating the Play app.
- [ ] Decide support email.
- [ ] Decide privacy policy hosting URL.

## 2. Create Play Console App

- [ ] App type: App.
- [ ] Free or paid: Free.
- [ ] Category: Tools or Productivity.
- [ ] Declarations: no ads unless added later.
- [ ] Use Play App Signing.
- [ ] Upload release AAB to internal testing first.

## 3. Store Listing

Use `store-prep/PLAY_STORE_LISTING.md`.

- [ ] Short description.
- [ ] Full description.
- [ ] App icon.
- [ ] Feature graphic.
- [ ] Phone screenshots:
  - [ ] Dashboard.
  - [ ] Setup & Settings.
  - [ ] Capture Troubleshooting.
  - [ ] Overlay over portrait media.
  - [ ] Overlay over landscape/live chat.

## 4. Policy Forms

- [ ] Privacy policy URL, based on `store-prep/PRIVACY_POLICY_DRAFT.md`.
- [ ] Data Safety form, based on `store-prep/DATA_SAFETY_DRAFT.md`.
- [ ] Content rating questionnaire.
- [ ] Target audience: adults/general, not children-focused.
- [ ] App access: no account required.
- [ ] Sensitive permissions explanation:
  - [ ] Microphone: mic fallback captions.
  - [ ] Overlay: floating transcript over media.
  - [ ] Foreground service: captions while another app is visible.
  - [ ] Media projection: user-approved internal audio capture.

## 5. Testing

- [ ] Internal testing with your own device.
- [ ] Closed testing if required by your developer account.
- [ ] Test fresh install.
- [ ] Test upgrade from debug only after uninstalling the debug app if signatures conflict.
- [ ] Test model downloads on Wi-Fi.
- [ ] Test internal audio preflight with YouTube app.
- [ ] Test internal audio preflight with mobile browser.
- [ ] Test mic fallback.
- [ ] Test overlay in portrait and landscape.

## 6. Monetization Readiness

Do not enable paid cloud mode until all are complete:

- [ ] Google Play Billing integrated.
- [ ] Play products created.
- [ ] Backend entitlement/minute ledger created.
- [ ] Server-side AI provider integrated.
- [ ] Cloud privacy policy update.
- [ ] Data Safety form updated for any off-device audio/text processing.
- [ ] Deletion/support process defined.

Recommended first monetization launch:

- Free local app first.
- Cloud mode waitlist/coming-soon language only.
- Add paid minute packs after support and billing are ready.

