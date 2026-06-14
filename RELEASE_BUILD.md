# Release Build

This project is configured to build a Google Play upload Android App Bundle (`.aab`) without committing signing secrets.

## Important Package Name Decision

The current Play package/application id is:

```text
com.personal.livetranslator
```

Google Play treats this as permanent after the app is created in Play Console. Decide before creating the Play app whether to keep it or switch to a public brand id such as:

```text
com.livemediatranslator.app
```

Changing it after launch means publishing a different app.

## Generate Upload Key

Run this once, then store the generated keystore somewhere private and backed up.

```powershell
.\tools\create-upload-key.ps1
```

Do not commit `.jks`, `.keystore`, passwords, or `signing.properties`.

## Build Signed Release AAB

Run:

```powershell
.\tools\build-signed-release-aab.ps1
```

Output:

```text
LiveMediaTranslator-1.0.0-release.aab
```

The script prompts for passwords, sets signing environment variables in memory for Gradle, builds `bundleRelease`, copies Gradle's internal `app-release.aab` to the branded filename above, verifies the bundle signature, then clears the environment variables.

## Build Unsigned Release AAB For Verification Only

This proves the release variant compiles, but it is not ready for Play upload.

```powershell
& "C:\Users\kmend\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat" bundleRelease
```

## Verify Release Metadata

Use Android Studio's APK Analyzer or upload the AAB to an internal testing track. Current release metadata:

```text
versionName: 1.0.0
versionCode: 100
minSdk: 29
targetSdk: 35
applicationId: com.personal.livetranslator
```

## Debug Builds

Debug APKs are still useful for phone testing but should not be uploaded to Play Console.

```powershell
& "C:\Users\kmend\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat" assembleDebug
```
