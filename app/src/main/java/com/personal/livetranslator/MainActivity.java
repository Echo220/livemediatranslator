package com.personal.livetranslator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_RECORD_AUDIO = 1002;
    private static final int REQUEST_POST_NOTIFICATIONS = 1003;
    private static final int REQUEST_PREFLIGHT_MEDIA_PROJECTION = 1004;
    private static final String LANGUAGE_PREFS = "language_settings";
    private static final String KEY_SOURCE_LANGUAGE = "source_language";
    private static final String KEY_TARGET_LANGUAGE = "target_language";
    private static final String KEY_AUTO_DETECT = "auto_detect";
    private static final String LOCAL_SOURCE_LANGUAGE = "ja";
    private static final String LOCAL_TARGET_LANGUAGE = "en-US";
    private static final LanguageOption[] SOURCE_LANGUAGES = new LanguageOption[]{
            new LanguageOption("ja", "Japanese", true),
            new LanguageOption("ko", "Korean", false),
            new LanguageOption("zh", "Chinese", false),
            new LanguageOption("es", "Spanish", false)
    };
    private static final LanguageOption[] TARGET_LANGUAGES = new LanguageOption[]{
            new LanguageOption("en-US", "English (US)", true),
            new LanguageOption("es", "Spanish", false),
            new LanguageOption("pt-BR", "Portuguese (BR)", false),
            new LanguageOption("fr", "French", false),
            new LanguageOption("de", "German", false)
    };

    private TextView micStatus;
    private TextView overlayStatus;
    private TextView fastModelStatus;
    private TextView betterModelStatus;
    private TextView languageStatus;
    private TextView cloudStatus;
    private TextView dashboardStatus;
    private TextView preflightStatus;
    private TextView activityStatus;
    private Spinner sourceLanguageSpinner;
    private Spinner targetLanguageSpinner;
    private CheckBox autoDetectCheckBox;
    private Button setupButton;
    private Button troubleshootButton;
    private Button fastInternalButton;
    private Button betterInternalButton;
    private Button fastMicButton;
    private Button betterMicButton;
    private Button downloadFastButton;
    private Button downloadBetterButton;
    private Button preflightButton;
    private Button cloudModeButton;
    private boolean pendingBetterInternalCapture;
    private Screen screen = Screen.DASHBOARD;
    private UiPalette palette;
    private SharedPreferences languagePreferences;

    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = UiPalette.from(this);
        languagePreferences = getSharedPreferences(LANGUAGE_PREFS, MODE_PRIVATE);
        applySystemBars();
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        deleteLegacyWhisperModel();
        screen = isMinimumSetupReady() ? Screen.DASHBOARD : Screen.SETUP;
        buildUi();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent intent = new Intent(this, CaptionService.class);
                intent.setAction(pendingBetterInternalCapture
                        ? CaptionService.ACTION_START_INTERNAL_BETTER
                        : CaptionService.ACTION_START_INTERNAL);
                intent.putExtra(CaptionService.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(CaptionService.EXTRA_RESULT_DATA, data);
                startCaptionService(intent);
                activityStatus.setText((pendingBetterInternalCapture ? "Better" : "Fast")
                        + " internal audio capture started. Switch to your media app now.");
            } else {
                activityStatus.setText("Screen/audio capture permission was canceled.");
            }
            pendingBetterInternalCapture = false;
        } else if (requestCode == REQUEST_PREFLIGHT_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent intent = new Intent(this, CaptionService.class);
                intent.setAction(CaptionService.ACTION_PREFLIGHT_INTERNAL);
                intent.putExtra(CaptionService.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(CaptionService.EXTRA_RESULT_DATA, data);
                startCaptionService(intent);
                activityStatus.setText("Preflight started. Switch to your media app and play audio for 15 seconds.");
            } else {
                activityStatus.setText("Internal audio preflight was canceled.");
            }
        }
    }

    private void buildUi() {
        resetUiReferences();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(palette.background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        root.setBackgroundColor(palette.background);
        scrollView.addView(root);

        if (screen == Screen.SETUP) {
            buildSetupScreen(root);
        } else if (screen == Screen.TROUBLESHOOT) {
            buildTroubleshootingScreen(root);
        } else {
            buildDashboardScreen(root);
        }

        setContentView(scrollView);
        refreshStatus();
    }

    private void buildDashboardScreen(LinearLayout root) {
        TextView title = text("Live Media Translator", 26, palette.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = text(
                "Floating English captions for Japanese videos, streams, and live chat.",
                15,
                palette.mutedText);
        subtitle.setPadding(0, dp(7), 0, dp(14));
        root.addView(subtitle);

        root.addView(miniBadge("Free local mode"));

        root.addView(sectionTitle("Dashboard"));
        dashboardStatus = statusText();
        languageStatus = statusText();
        preflightStatus = statusText();
        cloudStatus = statusText();
        activityStatus = statusText();
        root.addView(dashboardStatus);
        root.addView(languageStatus);
        root.addView(preflightStatus);
        root.addView(cloudStatus);
        root.addView(activityStatus);

        setupButton = button("Setup & Settings");
        setupButton.setOnClickListener(view -> {
            screen = Screen.SETUP;
            buildUi();
        });
        root.addView(actionBlock(setupButton, "Permissions, models, language, audio test, and troubleshooting."));

        troubleshootButton = button("Capture Troubleshooting");
        troubleshootButton.setOnClickListener(view -> {
            screen = Screen.TROUBLESHOOT;
            buildUi();
        });
        root.addView(actionBlock(troubleshootButton, "Use this if internal audio is silent or a video source behaves differently."));

        root.addView(sectionTitle("Start Captions"));
        root.addView(infoBox("Recommended: test internal audio first, then start Better internal captions. Use mic fallback only when the source blocks internal capture."));

        preflightButton = primaryButton("Test Internal Audio");
        preflightButton.setOnClickListener(view -> startInternalPreflight());
        root.addView(actionBlock(preflightButton, "Checks whether Android can hear the current video source."));

        betterInternalButton = primaryButton("Start Better Internal Captions");
        betterInternalButton.setOnClickListener(view -> startInternalCapture(true));
        root.addView(actionBlock(betterInternalButton, "Best local reading experience when the Better model is installed."));

        fastInternalButton = button("Start Fast Internal Captions");
        fastInternalButton.setOnClickListener(view -> startInternalCapture(false));
        root.addView(actionBlock(fastInternalButton, "Lower latency captions using the smaller local model."));

        root.addView(sectionTitle("Fallback"));
        betterMicButton = button("Start Better Mic Fallback");
        betterMicButton.setOnClickListener(view -> startMicCapture(true));
        root.addView(actionBlock(betterMicButton, "Use when internal audio is silent. Play the media out loud."));

        fastMicButton = button("Start Fast Mic Fallback");
        fastMicButton.setOnClickListener(view -> startMicCapture(false));
        root.addView(actionBlock(fastMicButton, "Small-model fallback for blocked internal capture."));

        Button stopButton = button("Stop Captions");
        stopButton.setOnClickListener(view -> {
            stopService(new Intent(this, CaptionService.class));
            activityStatus.setText("Caption service stopped.");
        });
        root.addView(actionBlock(stopButton, "Stops the overlay and foreground capture service."));

        root.addView(sectionTitle("Cloud Mode"));
        root.addView(infoBox("Not enabled yet. Planned cloud mode would add auto-detect and more language pairs with metered minutes."));

        cloudModeButton = button("View Cloud Roadmap");
        cloudModeButton.setOnClickListener(view -> activityStatus.setText(
                MonetizationConfig.cloudStatusLabel() + " Selected: " + selectedLanguageSummary() + "."));
        root.addView(actionBlock(cloudModeButton, "Shows current planned pricing and cloud readiness."));

        Button diagnosticsButton = button("Share Support Diagnostics");
        diagnosticsButton.setOnClickListener(view -> shareSupportDiagnostics());
        root.addView(actionBlock(diagnosticsButton, "Creates a plain text report for capture or model issues."));

        TextView note = text(
                "Internal audio depends on Android and the source app. This app does not bypass blocked or DRM-protected audio.",
                13,
                palette.mutedText);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);
    }

    private void buildSetupScreen(LinearLayout root) {
        TextView title = text("Setup & Settings", 26, palette.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = text(
                "One-time setup, language choices, audio checks, and troubleshooting.",
                15,
                palette.mutedText);
        subtitle.setPadding(0, dp(7), 0, dp(14));
        root.addView(subtitle);

        Button dashboardButton = button("Back to Dashboard");
        dashboardButton.setOnClickListener(view -> {
            screen = Screen.DASHBOARD;
            buildUi();
        });
        root.addView(actionBlock(dashboardButton, "Return to the compact daily-use screen."));

        root.addView(sectionTitle("Setup Checklist"));
        micStatus = statusText();
        overlayStatus = statusText();
        fastModelStatus = statusText();
        betterModelStatus = statusText();
        languageStatus = statusText();
        dashboardStatus = statusText();
        activityStatus = statusText();
        root.addView(micStatus);
        root.addView(overlayStatus);
        root.addView(fastModelStatus);
        root.addView(betterModelStatus);
        root.addView(languageStatus);
        root.addView(dashboardStatus);
        root.addView(activityStatus);

        root.addView(sectionTitle("Permissions"));
        Button micPermissionButton = button("Grant Microphone Permission");
        micPermissionButton.setOnClickListener(view -> requestAudioPermission());
        root.addView(actionBlock(micPermissionButton, "Required by Android for internal audio capture and mic fallback."));

        Button overlayPermissionButton = button("Grant Floating Overlay Permission");
        overlayPermissionButton.setOnClickListener(view -> openOverlaySettings());
        root.addView(actionBlock(overlayPermissionButton, "Lets the transcript stay over videos, portrait mode, and live chat."));

        root.addView(sectionTitle("Local Models"));
        downloadFastButton = button("Download Fast Vosk Model");
        downloadFastButton.setOnClickListener(view -> downloadFastModel());
        root.addView(actionBlock(downloadFastButton, "Required for free local captions. Smallest and fastest model."));

        downloadBetterButton = button("Download Better SenseVoice Model");
        downloadBetterButton.setOnClickListener(view -> downloadBetterModel());
        root.addView(actionBlock(downloadBetterButton, "Optional but recommended for cleaner phrase-level Japanese."));

        root.addView(sectionTitle("Language"));
        root.addView(infoBox("Free local captions currently support Japanese audio to English (US). Auto-detect and other languages are prepared here for cloud mode."));

        root.addView(fieldLabel("Listen to"));
        sourceLanguageSpinner = languageSpinner(
                SOURCE_LANGUAGES,
                selectedIndex(SOURCE_LANGUAGES, KEY_SOURCE_LANGUAGE, LOCAL_SOURCE_LANGUAGE));
        root.addView(sourceLanguageSpinner);

        autoDetectCheckBox = new CheckBox(this);
        autoDetectCheckBox.setText("Auto-detect source language (cloud planned)");
        autoDetectCheckBox.setTextSize(14f);
        autoDetectCheckBox.setTextColor(palette.text);
        autoDetectCheckBox.setChecked(languagePreferences.getBoolean(KEY_AUTO_DETECT, false));
        autoDetectCheckBox.setPadding(0, dp(7), 0, dp(7));
        root.addView(autoDetectCheckBox);

        root.addView(fieldLabel("Show captions in"));
        targetLanguageSpinner = languageSpinner(
                TARGET_LANGUAGES,
                selectedIndex(TARGET_LANGUAGES, KEY_TARGET_LANGUAGE, LOCAL_TARGET_LANGUAGE));
        root.addView(targetLanguageSpinner);

        root.addView(sectionTitle("Audio Test"));
        root.addView(infoBox("Run this before relying on internal captions. If it hears silence, try the same video in a browser or use mic fallback."));
        preflightStatus = statusText();
        root.addView(preflightStatus);

        preflightButton = primaryButton("Test Internal Audio Capture");
        preflightButton.setOnClickListener(view -> startInternalPreflight());
        root.addView(actionBlock(preflightButton, "Runs a 15-second check to see whether this video source can be heard."));

        troubleshootButton = button("Open Capture Troubleshooting");
        troubleshootButton.setOnClickListener(view -> {
            screen = Screen.TROUBLESHOOT;
            buildUi();
        });
        root.addView(actionBlock(troubleshootButton, "Guides you through silent internal audio and fallback choices."));

        root.addView(sectionTitle("Cloud Roadmap"));
        root.addView(infoBox("Cloud mode is planned for auto-detect, more target languages, and faster human-readable formatting. It is disabled in this build."));
        if (MonetizationConfig.CLOUD_MODE_ENABLED) {
            for (MonetizationConfig.Offer offer : MonetizationConfig.CLOUD_OFFERS) {
                root.addView(offerCard(offer));
            }
        } else {
            root.addView(infoBox("Paid cloud minute packs will appear here only after Google Play Billing and the backend entitlement ledger are live."));
        }

        cloudModeButton = button("Cloud Mode Status");
        cloudModeButton.setOnClickListener(view -> activityStatus.setText(
                MonetizationConfig.cloudStatusLabel() + " Selected: " + selectedLanguageSummary() + "."));
        root.addView(actionBlock(cloudModeButton, "Shows the current cloud-mode status for this test build."));

        root.addView(sectionTitle("Troubleshooting"));
        root.addView(infoBox("If internal audio is silent: start the video first, run preflight, try a mobile browser, then fall back to mic capture. Some apps and DRM-protected media simply block internal capture."));

        root.addView(sectionTitle("Support"));
        Button diagnosticsButton = button("Share Support Diagnostics");
        diagnosticsButton.setOnClickListener(view -> shareSupportDiagnostics());
        root.addView(actionBlock(diagnosticsButton, "Creates a plain text report for capture or model issues."));

        TextView note = text(
                "Internal audio depends on Android and the source app. This app does not bypass blocked or DRM-protected audio.",
                13,
                palette.mutedText);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        attachLanguageListeners();
    }

    private void buildTroubleshootingScreen(LinearLayout root) {
        TextView title = text("Capture Troubleshooting", 26, palette.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = text(
                "A quick path for silent internal audio, blocked apps, and fallback decisions.",
                15,
                palette.mutedText);
        subtitle.setPadding(0, dp(7), 0, dp(14));
        root.addView(subtitle);

        Button dashboardButton = button("Back to Dashboard");
        dashboardButton.setOnClickListener(view -> {
            screen = Screen.DASHBOARD;
            buildUi();
        });
        root.addView(actionBlock(dashboardButton, "Return to start captions or change setup."));

        root.addView(sectionTitle("Last Audio Test"));
        preflightStatus = statusText();
        activityStatus = statusText();
        root.addView(preflightStatus);
        root.addView(activityStatus);
        root.addView(infoBox(captureTroubleshootingCopy()));

        root.addView(sectionTitle("Try Next"));
        preflightButton = primaryButton("Run Internal Audio Test");
        preflightButton.setOnClickListener(view -> startInternalPreflight());
        root.addView(actionBlock(preflightButton, "Start the target video first, approve capture, then keep the video playing."));

        betterInternalButton = button("Start Better Internal Captions");
        betterInternalButton.setOnClickListener(view -> startInternalCapture(true));
        root.addView(actionBlock(betterInternalButton, "Use this when the audio test passes for the current source."));

        betterMicButton = button("Start Better Mic Fallback");
        betterMicButton.setOnClickListener(view -> startMicCapture(true));
        root.addView(actionBlock(betterMicButton, "Use this when the audio test is silent. Play the video through speakers."));

        fastMicButton = button("Start Fast Mic Fallback");
        fastMicButton.setOnClickListener(view -> startMicCapture(false));
        root.addView(actionBlock(fastMicButton, "Smaller-model mic fallback if Better is not installed."));

        root.addView(sectionTitle("Decision Guide"));
        root.addView(infoBox(
                "1. If the test passes, use internal captions for that app or browser.\n"
                        + "2. If the test is silent in the YouTube app, try the same video in a mobile browser.\n"
                        + "3. If the browser is also silent, use mic fallback.\n"
                        + "4. If mic fallback works but internal does not, the source is blocking Android playback capture."));

        root.addView(sectionTitle("Support"));
        Button diagnosticsButton = button("Share Support Diagnostics");
        diagnosticsButton.setOnClickListener(view -> shareSupportDiagnostics());
        root.addView(actionBlock(diagnosticsButton, "Includes permissions, model state, language, and the last audio test result."));
    }

    private void refreshStatus() {
        boolean hasMic = hasAudioPermission();
        boolean hasOverlay = Settings.canDrawOverlays(this);
        boolean hasFastModel = ModelInstaller.isModelReady(this);
        boolean hasBetterModel = ModelInstaller.isBetterModelReady(this);
        boolean localLanguageReady = isLocalLanguagePairSupported();
        CaptureCheckStore.Snapshot captureCheck = CaptureCheckStore.read(this);

        setStatusLine(micStatus, "Microphone permission", hasMic);
        setStatusLine(overlayStatus, "Floating overlay permission", hasOverlay);
        setStatusLine(fastModelStatus, "Fast Vosk model", hasFastModel);
        setOptionalStatusLine(betterModelStatus, "Better SenseVoice model", hasBetterModel);
        setStatusLine(languageStatus, "Language: " + selectedLanguageSummary(), localLanguageReady);
        setCaptureStatusLine(preflightStatus, captureCheck);
        setStatusLine(dashboardStatus, setupProgressSummary(), isMinimumSetupReady());
        setInfoLine(cloudStatus, MonetizationConfig.cloudStatusLabel());
        if (activityStatus != null && activityStatus.getText().length() == 0) {
            activityStatus.setText(isMinimumSetupReady()
                    ? "Ready to test audio or start captions."
                    : "Open setup to finish the required checks.");
        }

        boolean commonReady = hasMic && hasOverlay;
        setButtonEnabled(preflightButton, commonReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        setButtonEnabled(fastInternalButton, commonReady && localLanguageReady && hasFastModel);
        setButtonEnabled(fastMicButton, commonReady && localLanguageReady && hasFastModel);
        setButtonEnabled(betterInternalButton, commonReady && localLanguageReady && hasBetterModel);
        setButtonEnabled(betterMicButton, commonReady && localLanguageReady && hasBetterModel);
        setButtonEnabled(downloadFastButton, !hasFastModel);
        setButtonEnabled(downloadBetterButton, !hasBetterModel);
        setButtonEnabled(cloudModeButton, true);
        setButtonEnabled(setupButton, true);
        setButtonEnabled(troubleshootButton, true);
        if (setupButton != null) {
            setupButton.setText(isMinimumSetupReady() ? "Settings" : "Continue Setup");
        }
        if (downloadFastButton != null) {
            downloadFastButton.setText(hasFastModel ? "Fast Vosk Model Installed" : "Download Fast Vosk Model");
        }
        if (downloadBetterButton != null) {
            downloadBetterButton.setText(hasBetterModel
                    ? "Better SenseVoice Model Installed"
                    : "Download Better SenseVoice Model");
        }
        if (preflightButton != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            preflightButton.setText("Internal Audio Requires Android 10+");
        }
    }

    private void resetUiReferences() {
        micStatus = null;
        overlayStatus = null;
        fastModelStatus = null;
        betterModelStatus = null;
        languageStatus = null;
        cloudStatus = null;
        dashboardStatus = null;
        preflightStatus = null;
        activityStatus = null;
        sourceLanguageSpinner = null;
        targetLanguageSpinner = null;
        autoDetectCheckBox = null;
        setupButton = null;
        troubleshootButton = null;
        fastInternalButton = null;
        betterInternalButton = null;
        fastMicButton = null;
        betterMicButton = null;
        downloadFastButton = null;
        downloadBetterButton = null;
        preflightButton = null;
        cloudModeButton = null;
    }

    private boolean isMinimumSetupReady() {
        return hasAudioPermission()
                && Settings.canDrawOverlays(this)
                && ModelInstaller.isModelReady(this)
                && isLocalLanguagePairSupported();
    }

    private String setupProgressSummary() {
        int ready = 0;
        if (hasAudioPermission()) {
            ready++;
        }
        if (Settings.canDrawOverlays(this)) {
            ready++;
        }
        if (ModelInstaller.isModelReady(this)) {
            ready++;
        }
        if (isLocalLanguagePairSupported()) {
            ready++;
        }
        if (ready == 4) {
            return "Setup complete. Ready for local captions.";
        }
        return "Setup " + ready + "/4 complete. Next: " + nextSetupStep();
    }

    private String nextSetupStep() {
        if (!hasAudioPermission()) {
            return "grant microphone permission";
        }
        if (!Settings.canDrawOverlays(this)) {
            return "grant floating overlay permission";
        }
        if (!ModelInstaller.isModelReady(this)) {
            return "download the Fast Vosk model";
        }
        if (!isLocalLanguagePairSupported()) {
            return "choose Japanese -> English (US)";
        }
        return "run internal audio test";
    }

    private void deleteLegacyWhisperModel() {
        java.io.File oldModel = new java.io.File(new java.io.File(getFilesDir(), "models"), "ggml-base.bin");
        if (oldModel.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            oldModel.delete();
        }
        java.io.File oldPartial = new java.io.File(getCacheDir(), "ggml-base.bin.download");
        if (oldPartial.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            oldPartial.delete();
        }
    }

    private void setStatusLine(TextView textView, String label, boolean ok) {
        if (textView == null) {
            return;
        }
        textView.setText((ok ? "Ready - " : "Needs setup - ") + label);
        textView.setTextColor(ok ? palette.readyText : palette.warningText);
        textView.setBackground(rounded(ok ? palette.readyBackground : palette.warningBackground, dp(8), 0));
    }

    private void setOptionalStatusLine(TextView textView, String label, boolean ok) {
        if (textView == null) {
            return;
        }
        if (ok) {
            setStatusLine(textView, label, true);
            return;
        }
        setInfoLine(textView, "Optional - " + label);
    }

    private void setCaptureStatusLine(TextView textView, CaptureCheckStore.Snapshot snapshot) {
        if (textView == null) {
            return;
        }
        String age = captureAgeLabel(snapshot);
        if (snapshot.isPassed()) {
            setStatusLine(textView, "Internal audio test passed" + age, true);
        } else if (snapshot.isSilent()) {
            setStatusLine(textView, "Internal audio test silent" + age, false);
        } else if (snapshot.isRunning()) {
            setInfoLine(textView, "Checking internal audio now...");
        } else if (CaptureCheckStore.STATUS_ERROR.equals(snapshot.status)) {
            setStatusLine(textView, "Internal audio test error" + age, false);
        } else {
            setInfoLine(textView, "Not tested - internal audio source");
        }
    }

    private String captureTroubleshootingCopy() {
        CaptureCheckStore.Snapshot snapshot = CaptureCheckStore.read(this);
        return snapshot.message
                + "\n\nNext: " + snapshot.recommendation
                + "\n\nAudible reads: " + snapshot.audibleReads
                + "\nUpdated: " + captureUpdatedLabel(snapshot);
    }

    private String captureAgeLabel(CaptureCheckStore.Snapshot snapshot) {
        if (snapshot.timestampMs <= 0L) {
            return "";
        }
        return " - " + captureUpdatedLabel(snapshot);
    }

    private String captureUpdatedLabel(CaptureCheckStore.Snapshot snapshot) {
        if (snapshot.timestampMs <= 0L) {
            return "never";
        }
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - snapshot.timestampMs) / 1000L);
        if (ageSeconds < 60L) {
            return "just now";
        }
        long ageMinutes = ageSeconds / 60L;
        if (ageMinutes < 60L) {
            return ageMinutes + "m ago";
        }
        long ageHours = ageMinutes / 60L;
        if (ageHours < 24L) {
            return ageHours + "h ago";
        }
        return (ageHours / 24L) + "d ago";
    }

    private void setInfoLine(TextView textView, String label) {
        if (textView == null) {
            return;
        }
        textView.setText(label);
        textView.setTextColor(palette.infoText);
        textView.setBackground(rounded(palette.infoBackground, dp(8), 0));
    }

    private void requestAudioPermission() {
        if (!hasAudioPermission()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void downloadFastModel() {
        downloadFastButton.setEnabled(false);
        activityStatus.setText("Starting fast model download...");
        ModelInstaller.downloadJapaneseModel(this, new ModelInstaller.Callback() {
            @Override
            public void onProgress(String message, int percent) {
                activityStatus.setText(message + " " + percent + "%");
            }

            @Override
            public void onComplete(java.io.File modelDir) {
                activityStatus.setText("Fast Vosk speech model installed.");
                refreshStatus();
            }

            @Override
            public void onError(Exception exception) {
                activityStatus.setText("Model download failed: " + exception.getMessage());
                refreshStatus();
            }
        });
    }

    private void downloadBetterModel() {
        downloadBetterButton.setEnabled(false);
        activityStatus.setText("Starting better model download. This is large, so Wi-Fi is a good idea.");
        ModelInstaller.downloadBetterJapaneseModel(this, new ModelInstaller.Callback() {
            @Override
            public void onProgress(String message, int percent) {
                activityStatus.setText(message + " " + percent + "%");
            }

            @Override
            public void onComplete(java.io.File modelDir) {
                activityStatus.setText("Better SenseVoice speech model installed.");
                refreshStatus();
            }

            @Override
            public void onError(Exception exception) {
                activityStatus.setText("Better model download failed: " + exception.getMessage());
                refreshStatus();
            }
        });
    }

    private void startInternalPreflight() {
        if (!hasAudioPermission()) {
            activityStatus.setText("Grant microphone permission first. Android requires it for playback capture.");
            requestAudioPermission();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            activityStatus.setText("Grant floating overlay permission first so the preflight can show results.");
            openOverlaySettings();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            activityStatus.setText("Internal audio preflight requires Android 10 or newer.");
            return;
        }
        activityStatus.setText("Approve the Android capture prompt, then play the target video or stream.");
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_PREFLIGHT_MEDIA_PROJECTION);
    }

    private void startInternalCapture(boolean better) {
        if (!ensureReady(better)) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            activityStatus.setText("Internal audio capture requires Android 10 or newer.");
            return;
        }
        pendingBetterInternalCapture = better;
        activityStatus.setText("Approve the Android capture prompt, then switch to your media app.");
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    private void startMicCapture(boolean better) {
        if (!ensureReady(better)) {
            return;
        }
        Intent intent = new Intent(this, CaptionService.class);
        intent.setAction(better ? CaptionService.ACTION_START_MIC_BETTER : CaptionService.ACTION_START_MIC);
        startCaptionService(intent);
        activityStatus.setText((better ? "Better" : "Fast") + " mic fallback captions started. Play the media out loud.");
    }

    private void shareSupportDiagnostics() {
        String diagnostics = buildDiagnosticsText();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Live Media Translator diagnostics");
        intent.putExtra(Intent.EXTRA_TEXT, diagnostics);
        try {
            startActivity(Intent.createChooser(intent, "Share diagnostics"));
        } catch (Exception exception) {
            activityStatus.setText("No app is available to share diagnostics.");
        }
    }

    private String buildDiagnosticsText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Live Media Translator diagnostics\n");
        builder.append("App version: ").append(getAppVersion()).append('\n');
        builder.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        builder.append("Android SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        builder.append("Microphone permission: ").append(hasAudioPermission() ? "granted" : "missing").append('\n');
        builder.append("Overlay permission: ").append(Settings.canDrawOverlays(this) ? "granted" : "missing").append('\n');
        builder.append("Fast model installed: ").append(ModelInstaller.isModelReady(this)).append('\n');
        builder.append("Better model installed: ").append(ModelInstaller.isBetterModelReady(this)).append('\n');
        builder.append("Source language: ").append(selectedSourceLanguage().label).append('\n');
        builder.append("Target language: ").append(selectedTargetLanguage().label).append('\n');
        builder.append("Auto-detect source: ").append(isAutoDetectEnabled()).append('\n');
        builder.append("Local language supported: ").append(isLocalLanguagePairSupported()).append('\n');
        CaptureCheckStore.Snapshot captureCheck = CaptureCheckStore.read(this);
        builder.append("Last internal audio test: ").append(captureCheck.status).append('\n');
        builder.append("Last internal audio message: ").append(captureCheck.message).append('\n');
        builder.append("Last internal audio recommendation: ").append(captureCheck.recommendation).append('\n');
        builder.append("Last internal audio audible reads: ").append(captureCheck.audibleReads).append('\n');
        builder.append("Last internal audio updated: ").append(captureUpdatedLabel(captureCheck)).append('\n');
        builder.append("Cloud mode enabled: ").append(MonetizationConfig.CLOUD_MODE_ENABLED).append('\n');
        builder.append("Cloud status: ").append(MonetizationConfig.cloudStatusLabel()).append('\n');
        builder.append("\nIssue notes:\n");
        builder.append("- What app/browser were you playing media in?\n");
        builder.append("- Did internal audio preflight detect sound?\n");
        builder.append("- Did mic fallback work?\n");
        return builder.toString();
    }

    private String getAppVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return info.versionName + " (" + versionCode + ")";
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private boolean ensureReady(boolean better) {
        if (!hasAudioPermission()) {
            activityStatus.setText("Grant microphone permission first. Android requires it for both capture modes.");
            requestAudioPermission();
            return false;
        }
        if (!Settings.canDrawOverlays(this)) {
            activityStatus.setText("Grant floating overlay permission first.");
            openOverlaySettings();
            return false;
        }
        if (!isLocalLanguagePairSupported()) {
            activityStatus.setText(localLanguageLimitMessage());
            refreshStatus();
            return false;
        }
        if (better && !ModelInstaller.isBetterModelReady(this)) {
            activityStatus.setText("Download the Better SenseVoice model first.");
            refreshStatus();
            return false;
        }
        if (!better && !ModelInstaller.isModelReady(this)) {
            activityStatus.setText("Download the Fast Vosk model first.");
            refreshStatus();
            return false;
        }
        return true;
    }

    private void startCaptionService(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private TextView fieldLabel(String value) {
        TextView textView = text(value, 12, palette.sectionText);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setPadding(dp(3), dp(6), dp(3), dp(4));
        return textView;
    }

    private Spinner languageSpinner(LanguageOption[] options, int selectedIndex) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                languageLabels(options)) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(palette.text);
                view.setTextSize(15f);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(palette.text);
                view.setTextSize(15f);
                view.setBackgroundColor(palette.surface);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selectedIndex);
        spinner.setMinimumHeight(dp(48));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(rounded(palette.surface, dp(8), palette.stroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(7));
        spinner.setLayoutParams(params);
        return spinner;
    }

    private String[] languageLabels(LanguageOption[] options) {
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i].label + (options[i].local ? " (local)" : " (cloud planned)");
        }
        return labels;
    }

    private void attachLanguageListeners() {
        if (sourceLanguageSpinner == null || targetLanguageSpinner == null || autoDetectCheckBox == null) {
            return;
        }
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveLanguageSettings();
                refreshStatus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                saveLanguageSettings();
                refreshStatus();
            }
        };
        sourceLanguageSpinner.setOnItemSelectedListener(listener);
        targetLanguageSpinner.setOnItemSelectedListener(listener);
        autoDetectCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sourceLanguageSpinner.setEnabled(!isChecked);
            sourceLanguageSpinner.setAlpha(isChecked ? 0.45f : 1.0f);
            saveLanguageSettings();
            refreshStatus();
            activityStatus.setText(isLocalLanguagePairSupported()
                    ? "Language set to " + selectedLanguageSummary() + "."
                    : localLanguageLimitMessage());
        });
        sourceLanguageSpinner.setEnabled(!isAutoDetectEnabled());
        sourceLanguageSpinner.setAlpha(isAutoDetectEnabled() ? 0.45f : 1.0f);
    }

    private void saveLanguageSettings() {
        languagePreferences.edit()
                .putString(KEY_SOURCE_LANGUAGE, selectedSourceLanguage().key)
                .putString(KEY_TARGET_LANGUAGE, selectedTargetLanguage().key)
                .putBoolean(KEY_AUTO_DETECT, autoDetectCheckBox != null && autoDetectCheckBox.isChecked())
                .apply();
    }

    private int selectedIndex(LanguageOption[] options, String preferenceKey, String fallbackKey) {
        String selectedKey = languagePreferences.getString(preferenceKey, fallbackKey);
        for (int i = 0; i < options.length; i++) {
            if (options[i].key.equals(selectedKey)) {
                return i;
            }
        }
        return 0;
    }

    private LanguageOption selectedSourceLanguage() {
        return selectedLanguageOption(
                SOURCE_LANGUAGES,
                sourceLanguageSpinner,
                KEY_SOURCE_LANGUAGE,
                LOCAL_SOURCE_LANGUAGE);
    }

    private LanguageOption selectedTargetLanguage() {
        return selectedLanguageOption(
                TARGET_LANGUAGES,
                targetLanguageSpinner,
                KEY_TARGET_LANGUAGE,
                LOCAL_TARGET_LANGUAGE);
    }

    private LanguageOption selectedLanguageOption(
            LanguageOption[] options,
            Spinner spinner,
            String preferenceKey,
            String fallbackKey) {
        if (spinner != null && spinner.getSelectedItemPosition() >= 0
                && spinner.getSelectedItemPosition() < options.length) {
            return options[spinner.getSelectedItemPosition()];
        }
        return options[selectedIndex(options, preferenceKey, fallbackKey)];
    }

    private boolean isAutoDetectEnabled() {
        if (autoDetectCheckBox != null) {
            return autoDetectCheckBox.isChecked();
        }
        return languagePreferences.getBoolean(KEY_AUTO_DETECT, false);
    }

    private boolean isLocalLanguagePairSupported() {
        return !isAutoDetectEnabled()
                && LOCAL_SOURCE_LANGUAGE.equals(selectedSourceLanguage().key)
                && LOCAL_TARGET_LANGUAGE.equals(selectedTargetLanguage().key);
    }

    private String selectedLanguageSummary() {
        String sourceLabel = isAutoDetectEnabled() ? "Auto-detect" : selectedSourceLanguage().label;
        String modeLabel = isLocalLanguagePairSupported() ? "local free" : "cloud planned";
        return sourceLabel + " -> " + selectedTargetLanguage().label + " (" + modeLabel + ")";
    }

    private String localLanguageLimitMessage() {
        return "Free local mode only supports Japanese -> English (US). Choose that pair to start captions; auto-detect and other language pairs are planned for cloud mode.";
    }

    private TextView statusText() {
        TextView textView = text("", 13, palette.text);
        textView.setPadding(dp(11), dp(8), dp(11), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        textView.setLayoutParams(params);
        textView.setBackground(rounded(palette.surface, dp(8), palette.stroke));
        return textView;
    }

    private TextView text(String value, int sp, int color) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setLineSpacing(dp(2), 1.0f);
        return textView;
    }

    private TextView sectionTitle(String value) {
        TextView textView = text(value, 12, palette.sectionText);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setLetterSpacing(0.06f);
        textView.setAllCaps(true);
        textView.setPadding(0, dp(20), 0, dp(7));
        return textView;
    }

    private TextView miniBadge(String value) {
        TextView textView = text(value, 13, palette.infoText);
        textView.setPadding(dp(11), dp(7), dp(11), dp(7));
        textView.setBackground(rounded(palette.infoBackground, dp(8), 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        textView.setLayoutParams(params);
        return textView;
    }

    private TextView infoBox(String value) {
        TextView textView = text(value, 14, palette.mutedText);
        textView.setPadding(dp(12), dp(10), dp(12), dp(10));
        textView.setBackground(rounded(palette.surface, dp(8), palette.stroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        textView.setLayoutParams(params);
        return textView;
    }

    private TextView offerCard(MonetizationConfig.Offer offer) {
        TextView textView = text(
                offer.name + "\n" + offer.minutes + " minutes - " + offer.price + "\n" + offer.note,
                14,
                palette.text);
        textView.setPadding(dp(12), dp(10), dp(12), dp(10));
        textView.setBackground(rounded(palette.offerBackground, dp(8), palette.stroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(7));
        textView.setLayoutParams(params);
        return textView;
    }

    private LinearLayout actionBlock(Button button, String description) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(9));
        layout.setLayoutParams(params);

        TextView caption = text(description, 13, palette.mutedText);
        caption.setPadding(dp(4), dp(5), dp(4), 0);
        layout.addView(button);
        layout.addView(caption);
        return layout;
    }

    private Button button(String label) {
        return styledButton(label, false);
    }

    private Button primaryButton(String label) {
        return styledButton(label, true);
    }

    private Button styledButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(primary ? palette.primaryButtonText : palette.buttonText);
        button.setMinHeight(dp(48));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setStateListAnimator(null);
            button.setElevation(0f);
        }
        button.setBackground(rounded(primary ? palette.primary : palette.buttonBackground, dp(8),
                primary ? 0 : palette.stroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void setButtonEnabled(Button button, boolean enabled) {
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.45f);
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private void applySystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.setStatusBarColor(palette.background);
            window.setNavigationBarColor(palette.background);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = palette.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private enum Screen {
        DASHBOARD,
        SETUP,
        TROUBLESHOOT
    }

    private static final class LanguageOption {
        final String key;
        final String label;
        final boolean local;

        LanguageOption(String key, String label, boolean local) {
            this.key = key;
            this.label = label;
            this.local = local;
        }
    }

    private static final class UiPalette {
        final boolean dark;
        final int background;
        final int surface;
        final int stroke;
        final int text;
        final int mutedText;
        final int sectionText;
        final int primary;
        final int primaryButtonText;
        final int buttonBackground;
        final int buttonText;
        final int readyBackground;
        final int readyText;
        final int warningBackground;
        final int warningText;
        final int infoBackground;
        final int infoText;
        final int offerBackground;

        UiPalette(
                boolean dark,
                int background,
                int surface,
                int stroke,
                int text,
                int mutedText,
                int sectionText,
                int primary,
                int primaryButtonText,
                int buttonBackground,
                int buttonText,
                int readyBackground,
                int readyText,
                int warningBackground,
                int warningText,
                int infoBackground,
                int infoText,
                int offerBackground) {
            this.dark = dark;
            this.background = background;
            this.surface = surface;
            this.stroke = stroke;
            this.text = text;
            this.mutedText = mutedText;
            this.sectionText = sectionText;
            this.primary = primary;
            this.primaryButtonText = primaryButtonText;
            this.buttonBackground = buttonBackground;
            this.buttonText = buttonText;
            this.readyBackground = readyBackground;
            this.readyText = readyText;
            this.warningBackground = warningBackground;
            this.warningText = warningText;
            this.infoBackground = infoBackground;
            this.infoText = infoText;
            this.offerBackground = offerBackground;
        }

        static UiPalette from(Context context) {
            int nightMode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            boolean dark = nightMode == Configuration.UI_MODE_NIGHT_YES;
            if (dark) {
                return new UiPalette(
                        true,
                        0xFF0D1117,
                        0xFF151B23,
                        0xFF2A3441,
                        0xFFF4F7FA,
                        0xFFA0ABB8,
                        0xFF8AA4D6,
                        0xFF6EA8FE,
                        0xFF06111F,
                        0xFF111821,
                        0xFFF4F7FA,
                        0xFF10251B,
                        0xFF7FE0A3,
                        0xFF2B2315,
                        0xFFE9BD65,
                        0xFF101D2A,
                        0xFF9ABDFB,
                        0xFF101720);
            }
            return new UiPalette(
                    false,
                    0xFFFAFBFD,
                    0xFFFFFFFF,
                    0xFFDFE5EC,
                    0xFF111827,
                    0xFF5B6675,
                    0xFF355DB5,
                    0xFF2563EB,
                    0xFFFFFFFF,
                    0xFFFFFFFF,
                    0xFF111827,
                    0xFFEDF8F1,
                    0xFF116B37,
                    0xFFFFF7E6,
                    0xFF8A5A00,
                    0xFFEEF4FF,
                    0xFF2852B8,
                    0xFFFFFFFF);
        }
    }
}
