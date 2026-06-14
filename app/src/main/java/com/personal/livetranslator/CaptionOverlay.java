package com.personal.livetranslator;

import android.content.Context;
import android.content.ComponentCallbacks;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class CaptionOverlay {
    private static final String PREFS_NAME = "caption_overlay";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final int CONTROL_BACKGROUND = 0x223D4856;
    private static final int SCREEN_MARGIN_DP = 12;

    private final Context context;
    private final WindowManager windowManager;
    private final DisplayManager displayManager;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable stopAction;
    private final ComponentCallbacks componentCallbacks = new ComponentCallbacks() {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            mainHandler.post(() -> fitOverlayToCurrentDisplay(true));
        }

        @Override
        public void onLowMemory() {
            // No cached resources to release.
        }
    };
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            mainHandler.post(() -> fitOverlayToCurrentDisplay(true));
        }
    };

    private LinearLayout container;
    private ScrollView scrollView;
    private TextView modeText;
    private TextView statusText;
    private TextView transcriptText;
    private WindowManager.LayoutParams params;

    private int textSizeSp;
    private int minWidth;
    private int maxWidth;
    private int minHeight;
    private int maxHeight;
    private boolean callbacksRegistered;

    CaptionOverlay(Context context, Runnable stopAction) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        this.displayManager = (DisplayManager) this.context.getSystemService(Context.DISPLAY_SERVICE);
        this.preferences = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.stopAction = stopAction;
    }

    void show(String mode) {
        mainHandler.post(() -> {
            if (container != null) {
                modeText.setText(mode);
                return;
            }

            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            minWidth = dp(230);
            maxWidth = Math.max(minWidth, metrics.widthPixels - dp(12));
            minHeight = dp(170);
            maxHeight = Math.max(minHeight, metrics.heightPixels - dp(48));
            textSizeSp = preferences.getInt(KEY_TEXT_SIZE, 16);

            container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackgroundResource(R.drawable.caption_background);
            container.setAlpha(0.96f);

            LinearLayout header = new LinearLayout(context);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setOrientation(LinearLayout.HORIZONTAL);

            modeText = new TextView(context);
            modeText.setText(mode);
            modeText.setTextColor(0xFFEFF6FF);
            modeText.setTextSize(12f);
            modeText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            modeText.setGravity(Gravity.CENTER_VERTICAL);
            modeText.setSingleLine(true);
            modeText.setEllipsize(TextUtils.TruncateAt.END);

            header.addView(modeText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(control("A-", view -> adjustTextSize(-1)));
            header.addView(control("A+", view -> adjustTextSize(1)));
            header.addView(control("Fit", view -> fitOverlayToCurrentDisplay(true)));
            header.addView(control("Clear", view -> clearTranscript()));
            header.addView(control("Stop", view -> stopAction.run()));

            statusText = new TextView(context);
            statusText.setText("Starting captions...");
            statusText.setTextColor(0xFFB8C7D8);
            statusText.setTextSize(12f);
            statusText.setSingleLine(true);
            statusText.setEllipsize(TextUtils.TruncateAt.END);
            statusText.setPadding(0, dp(6), 0, dp(5));

            transcriptText = new TextView(context);
            transcriptText.setTextColor(0xFFFFFFFF);
            transcriptText.setTextSize(textSizeSp);
            transcriptText.setLineSpacing(dp(4), 1.0f);
            transcriptText.setText("Transcript will appear here.");
            transcriptText.setPadding(0, 0, dp(4), 0);

            scrollView = new ScrollView(context);
            scrollView.setFillViewport(false);
            scrollView.addView(transcriptText, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT));

            TextView resizeHandle = new TextView(context);
            resizeHandle.setText("Resize");
            resizeHandle.setTextColor(0xFFB8C7D8);
            resizeHandle.setTextSize(12f);
            resizeHandle.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            resizeHandle.setPadding(0, dp(5), dp(2), 0);
            resizeHandle.setOnTouchListener(new ResizeListener());

            container.addView(header);
            container.addView(statusText);
            container.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f));
            container.addView(resizeHandle);

            int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            int defaultWidth = Math.min(metrics.widthPixels - dp(32), dp(720));
            int defaultHeight = Math.min(Math.max(dp(260), metrics.heightPixels / 3), maxHeight);
            params = new WindowManager.LayoutParams(
                    clamp(preferences.getInt(KEY_WIDTH, defaultWidth), minWidth, maxWidth),
                    clamp(preferences.getInt(KEY_HEIGHT, defaultHeight), minHeight, maxHeight),
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = preferences.getInt(KEY_X, dp(16));
            params.y = preferences.getInt(KEY_Y, Math.max(dp(70), metrics.heightPixels - defaultHeight - dp(72)));

            header.setOnTouchListener(new DragListener());
            windowManager.addView(container, params);
            registerDisplayCallbacks();
            fitOverlayToCurrentDisplay(false);
        });
    }

    void updateCaption(String text) {
        setTranscriptParagraph(text);
    }

    void updateCaption(String text, boolean hint) {
        if (hint) {
            setStatus(text);
        } else {
            setTranscriptParagraph(text);
        }
    }

    void setStatus(String text) {
        mainHandler.post(() -> {
            if (statusText != null) {
                statusText.setText(text);
            }
        });
    }

    void showDraftTranslation(String text) {
        setTranscriptParagraph(text);
    }

    void appendFinalTranslation(String text, boolean clearDraft) {
        setTranscriptParagraph(text);
    }

    void setTranscriptParagraph(String text) {
        mainHandler.post(() -> {
            if (transcriptText == null) {
                return;
            }
            String cleaned = text == null ? "" : text.trim();
            transcriptText.setText(cleaned.isEmpty() ? "Transcript will appear here." : cleaned);
            scrollToBottom();
        });
    }

    void destroy() {
        mainHandler.post(() -> {
            unregisterDisplayCallbacks();
            if (container != null) {
                windowManager.removeView(container);
                container = null;
                scrollView = null;
                modeText = null;
                statusText = null;
                transcriptText = null;
            }
        });
    }

    private void registerDisplayCallbacks() {
        if (callbacksRegistered) {
            return;
        }
        context.registerComponentCallbacks(componentCallbacks);
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, mainHandler);
        }
        callbacksRegistered = true;
    }

    private void unregisterDisplayCallbacks() {
        if (!callbacksRegistered) {
            return;
        }
        try {
            context.unregisterComponentCallbacks(componentCallbacks);
        } catch (Exception ignored) {
        }
        if (displayManager != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (Exception ignored) {
            }
        }
        callbacksRegistered = false;
    }

    private void fitOverlayToCurrentDisplay(boolean persist) {
        if (params == null || container == null) {
            return;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int margin = dp(SCREEN_MARGIN_DP);
        minWidth = dp(230);
        maxWidth = Math.max(minWidth, metrics.widthPixels - margin);
        minHeight = dp(170);
        maxHeight = Math.max(minHeight, metrics.heightPixels - dp(48));

        params.width = clamp(params.width, minWidth, maxWidth);
        params.height = clamp(params.height, minHeight, maxHeight);

        int maxX = Math.max(margin / 2, metrics.widthPixels - params.width - margin / 2);
        int maxY = Math.max(dp(28), metrics.heightPixels - params.height - margin);
        params.x = clamp(params.x, margin / 2, maxX);
        params.y = clamp(params.y, dp(28), maxY);

        try {
            windowManager.updateViewLayout(container, params);
            if (persist) {
                persistFrame();
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private TextView control(String label, View.OnClickListener listener) {
        TextView textView = new TextView(context);
        textView.setText(label);
        textView.setTextColor(0xFFFFFFFF);
        textView.setTextSize(12f);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(dp(9), dp(5), dp(9), dp(5));
        textView.setBackground(rounded(CONTROL_BACKGROUND, dp(8)));
        textView.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(5), 0, 0, 0);
        textView.setLayoutParams(params);
        return textView;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void clearTranscript() {
        if (transcriptText == null) {
            return;
        }
        transcriptText.setText("Transcript cleared. New speech will appear here.");
    }

    private void scrollToBottom() {
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void adjustTextSize(int delta) {
        textSizeSp = clamp(textSizeSp + delta, 12, 24);
        preferences.edit().putInt(KEY_TEXT_SIZE, textSizeSp).apply();
        if (transcriptText != null) {
            transcriptText.setTextSize(textSizeSp);
        }
    }

    private void persistFrame() {
        if (params == null) {
            return;
        }
        preferences.edit()
                .putInt(KEY_WIDTH, params.width)
                .putInt(KEY_HEIGHT, params.height)
                .putInt(KEY_X, params.x)
                .putInt(KEY_Y, params.y)
                .apply();
    }

    private int dp(int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class DragListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float touchStartX;
        private float touchStartY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (params == null || container == null) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX + Math.round(event.getRawX() - touchStartX);
                    params.y = startY + Math.round(event.getRawY() - touchStartY);
                    windowManager.updateViewLayout(container, params);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    persistFrame();
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class ResizeListener implements View.OnTouchListener {
        private int startWidth;
        private int startHeight;
        private float touchStartX;
        private float touchStartY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (params == null || container == null) {
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startWidth = params.width;
                    startHeight = params.height;
                    touchStartX = event.getRawX();
                    touchStartY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.width = clamp(startWidth + Math.round(event.getRawX() - touchStartX), minWidth, maxWidth);
                    params.height = clamp(startHeight + Math.round(event.getRawY() - touchStartY), minHeight, maxHeight);
                    windowManager.updateViewLayout(container, params);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    persistFrame();
                    return true;
                default:
                    return true;
            }
        }
    }
}
