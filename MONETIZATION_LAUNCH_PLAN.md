# Monetization Launch Plan

## Recommendation

Launch the free local app first, then add paid cloud mode after Play Billing, backend entitlements, and privacy disclosures are complete.

The current app is ready for a free/local Play test. It is not ready to charge for cloud minutes yet.

## Why Free First

- Free local mode already works.
- It builds trust and collects compatibility feedback.
- It reveals which phones/apps block internal audio before paid users arrive.
- It reduces refund/support pressure.
- It keeps privacy/data-safety disclosures simpler until cloud audio exists.

## First Paid Offer

Use consumable minute packs, not subscription first.

Draft Play product ids:

```text
cloud_minutes_60
cloud_minutes_180
cloud_minutes_400
```

Draft pricing:

```text
60 minutes: $4.99
180 minutes: $14.99
400 minutes: $29.99
```

Keep these hidden in the app until billing/backend are enabled.

## Required Before Paid Cloud

### Android App

- Add Google Play Billing Library.
- Add purchase flow for consumable products.
- Add purchase acknowledgement/consumption flow.
- Show cloud minutes balance.
- Block cloud start when balance is zero.
- Never store backend/API secrets in the APK.

### Backend

- Verify Play purchase tokens server-side.
- Maintain entitlement ledger:
  - user/device/account id
  - purchase token
  - minutes purchased
  - minutes consumed
  - refunds/revocations
- Proxy AI provider calls server-side.
- Enforce per-user rate limits.
- Store minimal logs.
- Provide deletion/support process.

### Policy

- Update privacy policy for off-device audio/text processing.
- Update Data Safety form for any data sent to backend or AI provider.
- Add terms for paid minutes, refunds, and unused balances.
- Confirm Google Play Billing compliance.

## Cloud UX Rules

- Require internal audio preflight before paid cloud session starts.
- Warn clearly when internal audio is silent.
- Do not consume paid minutes during preflight.
- Show elapsed and remaining cloud minutes.
- Stop cloud capture automatically when balance reaches zero.
- Let users use free local mode without buying anything.

## Support Rules

- Any paid support ticket should include diagnostics.
- If internal audio is blocked by a source app, do not refund automatically unless minutes were consumed after a failed preflight.
- If mic fallback works but internal capture is silent, explain Android playback capture limitations.

## Suggested Rollout

1. Internal testing: free local only.
2. Closed testing: free local only, collect device/source compatibility.
3. Production free launch.
4. Add cloud waitlist/interest CTA.
5. Build billing/backend.
6. Internal test paid cloud with license testers.
7. Closed beta paid cloud.
8. Production paid cloud.

