package com.personal.livetranslator;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CaptionService extends Service {
    static final String ACTION_START_INTERNAL = "com.personal.livetranslator.START_INTERNAL";
    static final String ACTION_START_MIC = "com.personal.livetranslator.START_MIC";
    static final String ACTION_START_INTERNAL_BETTER = "com.personal.livetranslator.START_INTERNAL_BETTER";
    static final String ACTION_START_MIC_BETTER = "com.personal.livetranslator.START_MIC_BETTER";
    static final String ACTION_PREFLIGHT_INTERNAL = "com.personal.livetranslator.PREFLIGHT_INTERNAL";
    static final String ACTION_STOP = "com.personal.livetranslator.STOP";
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "caption_service";
    private static final int NOTIFICATION_ID = 40;
    private static final int SAMPLE_RATE = 16000;
    private static final int AUDIO_READ_BUFFER_BYTES = 2048;
    private static final long PARTIAL_TRANSLATION_INTERVAL_MS = 450L;
    private static final long PREFLIGHT_DURATION_MS = 15000L;
    private static final int MAX_CONTEXT_SEGMENTS = 18;
    private static final int MAX_ENGLISH_SEGMENTS = 32;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger partialTranslationCounter = new AtomicInteger();
    private final AtomicInteger displayedPartialSequence = new AtomicInteger();
    private final ArrayDeque<String> japaneseContext = new ArrayDeque<>();
    private final ArrayDeque<String> englishTranscript = new ArrayDeque<>();
    private final HashMap<Integer, String> pendingFinalTranslations = new HashMap<>();
    private final Object sherpaLock = new Object();
    private final Object transcriptLock = new Object();

    private ExecutorService worker;
    private ExecutorService sherpaDecoderWorker;
    private CaptionOverlay overlay;
    private Translator translator;
    private Model model;
    private Recognizer recognizer;
    private OfflineRecognizer sherpaRecognizer;
    private Vad sherpaVad;
    private AudioRecord audioRecord;
    private MediaProjection mediaProjection;
    private Thread audioThread;
    private volatile boolean listening;
    private Engine engine = Engine.VOSK;
    private String lastSubmittedJapanese = "";
    private String lastFinalJapanese = "";
    private String livePartialJapanese = "";
    private String liveEnglishPartial = "";
    private long lastPartialTranslationMs;
    private long lastSenseVoiceSpeechNoticeMs;
    private int nextFinalTranslationSequence = 1;
    private int nextFinalDisplaySequence = 1;

    private enum Engine {
        VOSK,
        SENSE_VOICE
    }

    @Override
    public void onCreate() {
        super.onCreate();
        worker = Executors.newSingleThreadExecutor();
        sherpaDecoderWorker = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isStartAction(action)) {
            return START_NOT_STICKY;
        }

        if (ACTION_PREFLIGHT_INTERNAL.equals(action)) {
            startAsForeground(action);
            if (!Settings.canDrawOverlays(this)) {
                stopSelf();
                return START_NOT_STICKY;
            }

            stopCapture();
            overlay = new CaptionOverlay(this, this::stopSelf);
            overlay.show("Internal audio preflight - drag top");
            updateOverlay("Preparing audio capture test...", true);
            worker.execute(() -> startPreflightInternalAudio(intent));
            return START_NOT_STICKY;
        }

        engine = isBetterAction(action) ? Engine.SENSE_VOICE : Engine.VOSK;
        startAsForeground(action);
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        stopCapture();
        String modePrefix = isInternalAction(action) ? "Internal" : "Mic";
        String mode = modePrefix + (engine == Engine.SENSE_VOICE
                ? " better transcript - drag top"
                : " fast transcript - drag top");
        overlay = new CaptionOverlay(this, this::stopSelf);
        overlay.show(mode);
        updateOverlay("Preparing Japanese to English captions...", true);
        prepareTranslatorThenStart(intent, action);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopCapture();
        if (translator != null) {
            translator.close();
            translator = null;
        }
        if (overlay != null) {
            overlay.destroy();
            overlay = null;
        }
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        if (sherpaDecoderWorker != null) {
            sherpaDecoderWorker.shutdownNow();
            sherpaDecoderWorker = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isStartAction(String action) {
        return ACTION_START_INTERNAL.equals(action)
                || ACTION_START_MIC.equals(action)
                || ACTION_START_INTERNAL_BETTER.equals(action)
                || ACTION_START_MIC_BETTER.equals(action)
                || ACTION_PREFLIGHT_INTERNAL.equals(action);
    }

    private boolean isInternalAction(String action) {
        return ACTION_START_INTERNAL.equals(action)
                || ACTION_START_INTERNAL_BETTER.equals(action)
                || ACTION_PREFLIGHT_INTERNAL.equals(action);
    }

    private boolean isBetterAction(String action) {
        return ACTION_START_INTERNAL_BETTER.equals(action) || ACTION_START_MIC_BETTER.equals(action);
    }

    private void prepareTranslatorThenStart(Intent intent, String action) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build();
        translator = Translation.getClient(options);

        updateOverlay("Checking local translation model...", true);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(unused -> worker.execute(() -> startRecognition(intent, action)))
                .addOnFailureListener(exception -> {
                    updateOverlay("Could not load ML Kit translation model: " + exception.getMessage(), true);
                    mainHandler.postDelayed(this::stopSelf, 5000);
                });
    }

    private void startRecognition(Intent intent, String action) {
        if (engine == Engine.SENSE_VOICE && !ModelInstaller.isBetterModelReady(this)) {
            updateOverlay("Better SenseVoice model is missing. Open the app and download it first.", true);
            mainHandler.postDelayed(this::stopSelf, 5000);
            return;
        }
        if (engine == Engine.VOSK && !ModelInstaller.isModelReady(this)) {
            updateOverlay("Fast Vosk model is missing. Open the app and download it first.", true);
            mainHandler.postDelayed(this::stopSelf, 5000);
            return;
        }

        try {
            if (engine == Engine.SENSE_VOICE) {
                prepareSenseVoice();
            } else {
                model = new Model(ModelInstaller.getModelPath(this));
                recognizer = new Recognizer(model, SAMPLE_RATE);
            }

            if (isInternalAction(action)) {
                startInternalAudio(intent);
            } else {
                startMicrophoneAudio();
            }
        } catch (Exception exception) {
            updateOverlay("Caption startup failed: " + exception.getMessage(), true);
            mainHandler.postDelayed(this::stopSelf, 5000);
        }
    }

    private void prepareSenseVoice() {
        File modelDir = new File(ModelInstaller.getBetterModelPath(this));

        OfflineSenseVoiceModelConfig senseVoiceConfig = new OfflineSenseVoiceModelConfig();
        senseVoiceConfig.setModel(new File(modelDir, "model.int8.onnx").getAbsolutePath());
        senseVoiceConfig.setLanguage("ja");
        senseVoiceConfig.setUseInverseTextNormalization(true);

        OfflineModelConfig modelConfig = new OfflineModelConfig();
        modelConfig.setSenseVoice(senseVoiceConfig);
        modelConfig.setTokens(new File(modelDir, "tokens.txt").getAbsolutePath());
        modelConfig.setNumThreads(Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 2)));

        OfflineRecognizerConfig recognizerConfig = new OfflineRecognizerConfig();
        recognizerConfig.setModelConfig(modelConfig);
        sherpaRecognizer = new OfflineRecognizer(null, recognizerConfig);

        SileroVadModelConfig sileroConfig = new SileroVadModelConfig();
        sileroConfig.setModel(ModelInstaller.getBetterVadPath(this));
        sileroConfig.setThreshold(0.45f);
        sileroConfig.setMinSpeechDuration(0.12f);
        sileroConfig.setMinSilenceDuration(0.22f);
        sileroConfig.setMaxSpeechDuration(3.2f);

        VadModelConfig vadConfig = new VadModelConfig();
        vadConfig.setSileroVadModelConfig(sileroConfig);
        vadConfig.setSampleRate(SAMPLE_RATE);
        vadConfig.setNumThreads(1);
        sherpaVad = new Vad(null, vadConfig);
    }

    private void startInternalAudio(Intent intent) throws IOException {
        prepareInternalAudioRecord(intent);
        startAudioLoop(engine == Engine.SENSE_VOICE
                ? "Better mode listening to internal audio..."
                : "Listening to internal media audio...", true);
    }

    private void prepareInternalAudioRecord(Intent intent) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new IOException("Internal audio capture requires Android 10 or newer.");
        }
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode == 0 || resultData == null) {
            throw new IOException("Missing Android capture permission result.");
        }

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            throw new IOException("Could not create MediaProjection.");
        }
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                mainHandler.post(() -> {
                    updateOverlay("Android stopped audio capture.", true);
                    stopSelf();
                });
            }
        }, mainHandler);

        int bufferSize = getBufferSize();
        AudioPlaybackCaptureConfigurationCompat captureConfig = new AudioPlaybackCaptureConfigurationCompat(mediaProjection);
        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(captureConfig.build())
                .build();
        ensureAudioRecordReady();
    }

    private void startPreflightInternalAudio(Intent intent) {
        try {
            CaptureCheckStore.markRunning(this);
            prepareInternalAudioRecord(intent);
            startPreflightLoop();
        } catch (Exception exception) {
            CaptureCheckStore.markError(this, exception.getMessage());
            updateOverlay("Preflight failed: " + exception.getMessage(), true);
            mainHandler.postDelayed(this::stopSelf, 5000);
        }
    }

    private void startPreflightLoop() {
        listening = true;
        updateOverlay("Play the target video now. Checking for internal audio for 15 seconds.", true);
        setOverlayParagraph("Waiting for internal audio...");

        audioThread = new Thread(() -> {
            byte[] buffer = new byte[AUDIO_READ_BUFFER_BYTES];
            long startedAt = SystemClock.elapsedRealtime();
            long deadline = startedAt + PREFLIGHT_DURATION_MS;
            long lastStatusAt = 0L;
            int audibleReads = 0;

            try {
                audioRecord.startRecording();
                while (listening && SystemClock.elapsedRealtime() < deadline) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read <= 0) {
                        continue;
                    }

                    long now = SystemClock.elapsedRealtime();
                    if (hasAudibleSamples(buffer, read)) {
                        audibleReads++;
                        if (now - lastStatusAt > 900L) {
                            lastStatusAt = now;
                            updateOverlay("Audio detected. Keep playing for the rest of the test.", true);
                            setOverlayParagraph("Internal audio is coming through. This source looks compatible.");
                        }
                    } else if (now - lastStatusAt > 1500L) {
                        lastStatusAt = now;
                        int remainingSeconds = Math.max(0, (int) ((deadline - now) / 1000L));
                        updateOverlay("Still listening... " + remainingSeconds + "s left.", true);
                    }
                }

                if (audibleReads >= 6) {
                    CaptureCheckStore.markPassed(this, audibleReads);
                    updateOverlay("Preflight passed.", true);
                    setOverlayParagraph("Internal audio works for this source. Return to the app and start internal captions.");
                } else {
                    CaptureCheckStore.markSilent(this, audibleReads);
                    updateOverlay("No internal audio detected.", true);
                    setOverlayParagraph("This source may block Android playback capture. Try the same video in a mobile browser, or use mic fallback.");
                }
                mainHandler.postDelayed(this::stopSelf, 7000);
            } catch (Exception exception) {
                if (listening) {
                    CaptureCheckStore.markError(this, exception.getMessage());
                    updateOverlay("Preflight stopped: " + exception.getMessage(), true);
                    mainHandler.postDelayed(this::stopSelf, 5000);
                }
            }
        }, "caption-preflight-loop");
        audioThread.start();
    }

    private void startMicrophoneAudio() throws IOException {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new IOException("Microphone permission is not granted.");
        }

        int bufferSize = getBufferSize();
        audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(audioFormat())
                .setBufferSizeInBytes(bufferSize)
                .build();
        ensureAudioRecordReady();
        startAudioLoop(engine == Engine.SENSE_VOICE
                ? "Better mode listening through the microphone..."
                : "Listening through the microphone...", false);
    }

    private void startAudioLoop(String startingMessage, boolean internalMode) {
        listening = true;
        updateOverlay(startingMessage, true);

        audioThread = new Thread(() -> {
            byte[] buffer = new byte[AUDIO_READ_BUFFER_BYTES];
            long startedAt = SystemClock.elapsedRealtime();
            long lastStrongAudioAt = startedAt;
            long lastSilenceNoticeAt = 0L;

            try {
                audioRecord.startRecording();
                while (listening) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read <= 0) {
                        continue;
                    }

                    long now = SystemClock.elapsedRealtime();
                    if (hasAudibleSamples(buffer, read)) {
                        lastStrongAudioAt = now;
                    } else if (internalMode
                            && now - startedAt > 10000
                            && now - lastStrongAudioAt > 8000
                            && now - lastSilenceNoticeAt > 10000) {
                        lastSilenceNoticeAt = now;
                        updateOverlay("No internal audio detected yet. Try a mobile browser, or use mic fallback.", true);
                    }

                    if (engine == Engine.SENSE_VOICE) {
                        handleSenseVoiceAudio(buffer, read);
                    } else if (recognizer.acceptWaveForm(buffer, read)) {
                        handleVoskJson(recognizer.getResult(), true);
                    } else {
                        handleVoskJson(recognizer.getPartialResult(), false);
                    }
                }
                if (engine == Engine.SENSE_VOICE) {
                    flushSenseVoiceAudio();
                } else {
                    handleVoskJson(recognizer.getFinalResult(), true);
                }
            } catch (Exception exception) {
                if (listening) {
                    updateOverlay("Audio capture stopped: " + exception.getMessage(), true);
                }
            }
        }, "caption-audio-loop");
        audioThread.start();
    }

    private void handleVoskJson(String json, boolean finalResult) {
        try {
            JSONObject object = new JSONObject(json);
            String key = finalResult ? "text" : "partial";
            String japanese = object.optString(key, "").trim();
            if (japanese.isEmpty()) {
                return;
            }

            long now = SystemClock.elapsedRealtime();
            if (!finalResult && now - lastPartialTranslationMs < PARTIAL_TRANSLATION_INTERVAL_MS) {
                return;
            }
            if (!finalResult) {
                lastPartialTranslationMs = now;
            }
            if (finalResult && japanese.equals(lastFinalJapanese)) {
                return;
            }
            if (!finalResult && japanese.equals(lastSubmittedJapanese)) {
                return;
            }
            if (finalResult) {
                lastFinalJapanese = japanese;
                lastSubmittedJapanese = "";
                livePartialJapanese = "";
                appendJapaneseContext(japanese);
                clearLivePartial();
            } else {
                lastSubmittedJapanese = japanese;
                livePartialJapanese = japanese;
            }
            translateJapaneseSegment(japanese, finalResult);
        } catch (Exception exception) {
            // Vosk returns JSON; ignore malformed partials defensively.
        }
    }

    private void handleSenseVoiceAudio(byte[] buffer, int length) {
        Vad activeVad = sherpaVad;
        if (activeVad == null) {
            return;
        }

        float[] samples = pcm16BytesToFloats(buffer, length);
        if (samples.length == 0) {
            return;
        }

        activeVad.acceptWaveform(samples);
        long now = SystemClock.elapsedRealtime();
        if (activeVad.isSpeechDetected() && now - lastSenseVoiceSpeechNoticeMs > 1200) {
            lastSenseVoiceSpeechNoticeMs = now;
            updateOverlay("Hearing Japanese... translating each phrase.", true);
        }
        drainSenseVoiceSegments(false);
    }

    private void flushSenseVoiceAudio() {
        Vad activeVad = sherpaVad;
        if (activeVad == null) {
            return;
        }
        activeVad.flush();
        drainSenseVoiceSegments(true);
    }

    private void drainSenseVoiceSegments(boolean force) {
        Vad activeVad = sherpaVad;
        if (activeVad == null) {
            return;
        }
        while (!activeVad.empty()) {
            SpeechSegment segment = activeVad.front();
            float[] samples = segment.getSamples();
            activeVad.pop();
            if (samples != null && (force || samples.length > SAMPLE_RATE / 6)) {
                decodeSenseVoiceSegment(samples);
            }
        }
    }

    private void decodeSenseVoiceSegment(float[] samples) {
        ExecutorService decoder = sherpaDecoderWorker;
        if (decoder == null || sherpaRecognizer == null || !listening) {
            return;
        }

        decoder.execute(() -> {
            synchronized (sherpaLock) {
                OfflineRecognizer activeRecognizer = sherpaRecognizer;
                if (!listening || activeRecognizer == null) {
                    return;
                }
                OfflineStream stream = null;
                try {
                    stream = activeRecognizer.createStream();
                    stream.acceptWaveform(samples, SAMPLE_RATE);
                    activeRecognizer.decode(stream);
                    String japanese = activeRecognizer.getResult(stream).getText();
                    if (listening) {
                        handleFinalJapanese(japanese);
                    }
                } catch (Exception exception) {
                    if (listening) {
                        updateOverlay("Better transcription failed: " + exception.getMessage(), true);
                    }
                } finally {
                    if (stream != null) {
                        stream.release();
                    }
                }
            }
        });
    }

    private float[] pcm16BytesToFloats(byte[] buffer, int length) {
        int sampleCount = length / 2;
        float[] samples = new float[sampleCount];
        for (int i = 0, sampleIndex = 0; i + 1 < length; i += 2, sampleIndex++) {
            int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8);
            samples[sampleIndex] = sample / 32768.0f;
        }
        return samples;
    }

    private void handleFinalJapanese(String japanese) {
        String cleaned = japanese == null ? "" : japanese.trim();
        if (cleaned.isEmpty() || cleaned.equals(lastFinalJapanese)) {
            return;
        }

        lastFinalJapanese = cleaned;
        lastSubmittedJapanese = "";
        livePartialJapanese = "";
        appendJapaneseContext(cleaned);
        clearLivePartial();
        translateJapaneseSegment(cleaned, true);
    }

    private void translateJapaneseSegment(String japanese, boolean finalResult) {
        Translator activeTranslator = translator;
        if (activeTranslator == null) {
            return;
        }
        String segment = prepareJapaneseForTranslation(japanese, finalResult);
        if (segment.isEmpty()) {
            return;
        }

        if (finalResult) {
            translateFinalJapaneseSegment(activeTranslator, segment);
        } else {
            translatePartialJapaneseSegment(activeTranslator, segment);
        }
    }

    private void translateFinalJapaneseSegment(Translator activeTranslator, String japanese) {
        int sequence;
        synchronized (transcriptLock) {
            sequence = nextFinalTranslationSequence++;
        }
        activeTranslator.translate(japanese)
                .addOnSuccessListener(english -> {
                    String polished = polishEnglishSegment(english, true);
                    handleFinalEnglishTranslation(sequence, polished);
                })
                .addOnFailureListener(exception -> {
                    updateOverlay("Translation failed: " + exception.getMessage(), true);
                    handleFinalEnglishTranslation(sequence, "");
                });
    }

    private void translatePartialJapaneseSegment(Translator activeTranslator, String japanese) {
        int sequence = partialTranslationCounter.incrementAndGet();
        activeTranslator.translate(japanese)
                .addOnSuccessListener(english -> {
                    String polished = polishEnglishSegment(english, false);
                    if (polished.isEmpty()) {
                        return;
                    }
                    if (sequence >= displayedPartialSequence.get()) {
                        displayedPartialSequence.set(sequence);
                        setLivePartial(polished);
                    }
                });
    }

    private void handleFinalEnglishTranslation(int sequence, String english) {
        String paragraph;
        synchronized (transcriptLock) {
            pendingFinalTranslations.put(sequence, english == null ? "" : english);
            while (pendingFinalTranslations.containsKey(nextFinalDisplaySequence)) {
                appendEnglishSegmentLocked(pendingFinalTranslations.remove(nextFinalDisplaySequence));
                nextFinalDisplaySequence++;
            }
            liveEnglishPartial = "";
            paragraph = buildEnglishTranscriptLocked();
        }
        setOverlayParagraph(paragraph);
    }

    private void appendEnglishSegmentLocked(String segment) {
        if (segment == null || segment.trim().isEmpty()) {
            return;
        }
        String cleaned = segment.trim();
        if (!englishTranscript.isEmpty() && cleaned.equals(englishTranscript.peekLast())) {
            return;
        }
        englishTranscript.addLast(cleaned);
        while (englishTranscript.size() > MAX_ENGLISH_SEGMENTS) {
            englishTranscript.removeFirst();
        }
    }

    private void setLivePartial(String partial) {
        String paragraph;
        synchronized (transcriptLock) {
            liveEnglishPartial = partial;
            paragraph = buildEnglishTranscriptLocked();
        }
        setOverlayParagraph(paragraph);
    }

    private void clearLivePartial() {
        synchronized (transcriptLock) {
            liveEnglishPartial = "";
        }
    }

    private String buildEnglishTranscriptLocked() {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String segment : englishTranscript) {
            if (builder.length() > 0) {
                builder.append(count > 0 && count % 4 == 0 ? "\n\n" : " ");
            }
            builder.append(segment);
            count++;
        }
        if (!liveEnglishPartial.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(liveEnglishPartial);
            if (!liveEnglishPartial.endsWith("...")) {
                builder.append("...");
            }
        }
        return builder.toString().trim();
    }

    private void appendJapaneseContext(String japanese) {
        String cleaned = japanese == null ? "" : japanese.trim();
        if (cleaned.isEmpty()) {
            return;
        }
        if (!japaneseContext.isEmpty() && cleaned.equals(japaneseContext.peekLast())) {
            return;
        }
        japaneseContext.addLast(cleaned);
        while (japaneseContext.size() > MAX_CONTEXT_SEGMENTS) {
            japaneseContext.removeFirst();
        }
    }

    private String prepareJapaneseForTranslation(String segment, boolean finalSegment) {
        String cleaned = segment == null ? "" : segment.trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        if (finalSegment && !endsWithJapanesePunctuation(cleaned)) {
            return cleaned + '\u3002';
        }
        return cleaned;
    }

    private boolean endsWithJapanesePunctuation(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        return last == '\u3002' || last == '\uFF01' || last == '\uFF1F' || last == '.' || last == '!' || last == '?';
    }

    private String polishEnglishSegment(String text, boolean finalResult) {
        String cleaned = text
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.!?;:])", "$1")
                .replaceAll("^[\\s,.;:!?-]+", "")
                .trim();
        if (cleaned.isEmpty()) {
            return cleaned;
        }

        StringBuilder builder = new StringBuilder(cleaned);
        for (int i = 0; i < builder.length(); i++) {
            char value = builder.charAt(i);
            if (Character.isLetter(value)) {
                builder.setCharAt(i, Character.toUpperCase(value));
                break;
            }
        }
        if (finalResult && !endsWithEnglishPunctuation(builder.charAt(builder.length() - 1))) {
            builder.append('.');
        }
        return builder.toString();
    }

    private boolean endsWithEnglishPunctuation(char value) {
        return value == '.' || value == '!' || value == '?' || value == '"' || value == '\'';
    }

    private void updateOverlay(String text, boolean hint) {
        CaptionOverlay activeOverlay = overlay;
        if (activeOverlay != null) {
            activeOverlay.setStatus(text);
        }
    }

    private void setOverlayParagraph(String text) {
        CaptionOverlay activeOverlay = overlay;
        if (activeOverlay != null) {
            activeOverlay.setTranscriptParagraph(text);
        }
    }

    private boolean hasAudibleSamples(byte[] buffer, int length) {
        long sum = 0L;
        int samples = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8);
            sum += Math.abs(sample);
            samples++;
        }
        return samples > 0 && (sum / samples) > 250;
    }

    private int getBufferSize() {
        int min = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        return Math.max(min, SAMPLE_RATE);
    }

    private AudioFormat audioFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
    }

    private void ensureAudioRecordReady() throws IOException {
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IOException("Android could not initialize audio capture.");
        }
    }

    private void stopCapture() {
        listening = false;

        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record != null) {
            try {
                record.stop();
            } catch (Exception ignored) {
            }
            record.release();
        }

        Thread thread = audioThread;
        audioThread = null;
        if (thread != null && Thread.currentThread() != thread) {
            try {
                thread.join(1000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        synchronized (sherpaLock) {
            if (sherpaVad != null) {
                sherpaVad.release();
                sherpaVad = null;
            }
            if (sherpaRecognizer != null) {
                sherpaRecognizer.release();
                sherpaRecognizer = null;
            }
        }

        MediaProjection projection = mediaProjection;
        mediaProjection = null;
        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception ignored) {
            }
        }

        lastSubmittedJapanese = "";
        lastFinalJapanese = "";
        livePartialJapanese = "";
        liveEnglishPartial = "";
        japaneseContext.clear();
        englishTranscript.clear();
        pendingFinalTranslations.clear();
        lastPartialTranslationMs = 0L;
        lastSenseVoiceSpeechNoticeMs = 0L;
        nextFinalTranslationSequence = 1;
        nextFinalDisplaySequence = 1;
        partialTranslationCounter.set(0);
        displayedPartialSequence.set(0);
    }

    private void startAsForeground(String action) {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isInternalAction(action)) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, CaptionService.class);
        stopIntent.setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Live Media Translator")
                .setContentText("Japanese to English captions are running")
                .setOngoing(true)
                .addAction(R.drawable.ic_notification, "Stop", stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Caption service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private static final class AudioPlaybackCaptureConfigurationCompat {
        private final MediaProjection projection;

        AudioPlaybackCaptureConfigurationCompat(MediaProjection projection) {
            this.projection = projection;
        }

        android.media.AudioPlaybackCaptureConfiguration build() {
            return new android.media.AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
        }
    }
}
