package com.personal.livetranslator;

import android.content.Context;
import android.content.SharedPreferences;

final class CaptureCheckStore {
    static final String STATUS_NONE = "none";
    static final String STATUS_RUNNING = "running";
    static final String STATUS_PASSED = "passed";
    static final String STATUS_SILENT = "silent";
    static final String STATUS_ERROR = "error";

    private static final String PREFS_NAME = "capture_check";
    private static final String KEY_STATUS = "status";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_RECOMMENDATION = "recommendation";
    private static final String KEY_TIMESTAMP_MS = "timestamp_ms";
    private static final String KEY_AUDIBLE_READS = "audible_reads";

    private CaptureCheckStore() {
    }

    static void markRunning(Context context) {
        save(
                context,
                STATUS_RUNNING,
                "Internal audio test is running.",
                "Play the target video now and keep it audible until the test finishes.",
                0);
    }

    static void markPassed(Context context, int audibleReads) {
        save(
                context,
                STATUS_PASSED,
                "Internal audio works for this source.",
                "Start internal captions for this app or video source.",
                audibleReads);
    }

    static void markSilent(Context context, int audibleReads) {
        save(
                context,
                STATUS_SILENT,
                "No internal audio was heard.",
                "Try the same video in a mobile browser. If it is still silent, use mic fallback.",
                audibleReads);
    }

    static void markError(Context context, String message) {
        String detail = message == null || message.trim().isEmpty()
                ? "Internal audio test could not run."
                : "Internal audio test failed: " + message.trim();
        save(
                context,
                STATUS_ERROR,
                detail,
                "Check permissions, restart the video, then run the audio test again.",
                0);
    }

    static Snapshot read(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new Snapshot(
                preferences.getString(KEY_STATUS, STATUS_NONE),
                preferences.getString(KEY_MESSAGE, "Internal audio has not been tested yet."),
                preferences.getString(KEY_RECOMMENDATION, "Run Test Internal Audio before relying on internal captions."),
                preferences.getLong(KEY_TIMESTAMP_MS, 0L),
                preferences.getInt(KEY_AUDIBLE_READS, 0));
    }

    private static void save(
            Context context,
            String status,
            String message,
            String recommendation,
            int audibleReads) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATUS, status)
                .putString(KEY_MESSAGE, message)
                .putString(KEY_RECOMMENDATION, recommendation)
                .putLong(KEY_TIMESTAMP_MS, System.currentTimeMillis())
                .putInt(KEY_AUDIBLE_READS, audibleReads)
                .apply();
    }

    static final class Snapshot {
        final String status;
        final String message;
        final String recommendation;
        final long timestampMs;
        final int audibleReads;

        Snapshot(String status, String message, String recommendation, long timestampMs, int audibleReads) {
            this.status = status == null ? STATUS_NONE : status;
            this.message = message == null ? "" : message;
            this.recommendation = recommendation == null ? "" : recommendation;
            this.timestampMs = timestampMs;
            this.audibleReads = audibleReads;
        }

        boolean isPassed() {
            return STATUS_PASSED.equals(status);
        }

        boolean isSilent() {
            return STATUS_SILENT.equals(status);
        }

        boolean isRunning() {
            return STATUS_RUNNING.equals(status);
        }

        boolean hasResult() {
            return !STATUS_NONE.equals(status);
        }
    }
}
