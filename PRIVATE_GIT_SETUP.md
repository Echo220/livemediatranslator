# Private Git Setup

Yes: keep this app in a private Git repo before pushing it toward Play testing.

Use this folder as the repo root:

```powershell
C:\Users\kmend\Documents\Codex\2026-05-30\i-need-a-personal-android-app\outputs\live-youtube-translator
```

That keeps the Android project clean and leaves the outer Codex workspace, local APK server, QA artifacts, and downloaded tools out of the repository.

## Do Not Commit

- Upload signing keys: `*.jks`, `*.keystore`
- Signing passwords or `signing.properties`
- `local.properties`
- Generated APK/AAB files
- Gradle build folders
- Emulator logs, traces, heap dumps, and performance captures
- Any future cloud API keys or service account JSON files

Store the Play upload key somewhere outside the repo, for example:

```powershell
C:\Users\kmend\Documents\LiveMediaTranslator-upload.jks
```

Keep the passwords in a password manager.

## First Push

Create a private GitHub repo first, then run:

```powershell
cd C:\Users\kmend\Documents\Codex\2026-05-30\i-need-a-personal-android-app\outputs\live-youtube-translator
git init
git add .
git commit -m "Prepare Live Media Translator closed test release"
git branch -M main
git remote add origin https://github.com/YOUR_USER/live-media-translator.git
git push -u origin main
```

If `git` is not installed or not on PATH, use GitHub Desktop or install Git for Windows.

## Recommended Branches

- `main`: stable Play-ready builds
- `develop`: active app work
- `release/play-closed-test`: final closed-test preparation

For now, a single private repo with `main` is enough.
