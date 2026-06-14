package com.personal.livetranslator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

final class ModelInstaller {
    static final String MODEL_NAME = "vosk-model-small-ja-0.22";
    static final String MODEL_URL = "https://alphacephei.com/vosk/models/" + MODEL_NAME + ".zip";
    static final String SENSE_VOICE_MODEL_NAME = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09";
    static final String SENSE_VOICE_MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
            + SENSE_VOICE_MODEL_NAME + ".tar.bz2";
    static final String SENSE_VOICE_VAD_FILE = "silero_vad.onnx";
    static final String SENSE_VOICE_VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
            + SENSE_VOICE_VAD_FILE;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private ModelInstaller() {
    }

    interface Callback {
        void onProgress(String message, int percent);

        void onComplete(File modelDir);

        void onError(Exception exception);
    }

    static File getModelDir(Context context) {
        return new File(new File(context.getFilesDir(), "models"), MODEL_NAME);
    }

    static File getBetterModelDir(Context context) {
        return new File(new File(context.getFilesDir(), "models"), SENSE_VOICE_MODEL_NAME);
    }

    static String getModelPath(Context context) {
        return getModelDir(context).getAbsolutePath();
    }

    static String getBetterModelPath(Context context) {
        return getBetterModelDir(context).getAbsolutePath();
    }

    static String getBetterVadPath(Context context) {
        return new File(getBetterModelDir(context), SENSE_VOICE_VAD_FILE).getAbsolutePath();
    }

    static boolean isModelReady(Context context) {
        File modelDir = getModelDir(context);
        return new File(modelDir, "am/final.mdl").isFile()
                && new File(modelDir, "conf/model.conf").isFile()
                && new File(modelDir, "graph").isDirectory();
    }

    static boolean isBetterModelReady(Context context) {
        return isBetterModelReady(getBetterModelDir(context));
    }

    private static boolean isBetterModelReady(File modelDir) {
        return new File(modelDir, "model.int8.onnx").isFile()
                && new File(modelDir, "tokens.txt").isFile()
                && new File(modelDir, SENSE_VOICE_VAD_FILE).isFile();
    }

    static void downloadJapaneseModel(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());

        EXECUTOR.execute(() -> {
            try {
                postProgress(main, callback, "Connecting to Vosk model host...", 1);
                File zipFile = new File(appContext.getCacheDir(), MODEL_NAME + ".zip");
                downloadZip(zipFile, main, callback);

                postProgress(main, callback, "Unpacking Japanese speech model...", 95);
                File targetDir = getModelDir(appContext);
                File extractRoot = new File(appContext.getCacheDir(), MODEL_NAME + "-extract");
                deleteRecursive(extractRoot);
                if (!extractRoot.mkdirs() && !extractRoot.isDirectory()) {
                    throw new IOException("Could not create model extraction folder.");
                }
                unzip(zipFile, extractRoot);

                File extractedModel = new File(extractRoot, MODEL_NAME);
                if (!new File(extractedModel, "conf/model.conf").isFile()) {
                    throw new IOException("Downloaded zip did not contain the expected Vosk model files.");
                }

                deleteRecursive(targetDir);
                File modelParent = targetDir.getParentFile();
                if (modelParent != null && !modelParent.exists() && !modelParent.mkdirs()) {
                    throw new IOException("Could not create app model folder.");
                }
                copyDirectory(extractedModel, targetDir);
                deleteRecursive(extractRoot);

                main.post(() -> callback.onComplete(targetDir));
            } catch (Exception exception) {
                main.post(() -> callback.onError(exception));
            }
        });
    }

    static void downloadBetterJapaneseModel(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());

        EXECUTOR.execute(() -> {
            try {
                postProgress(main, callback, "Connecting to SenseVoice model host...", 1);
                File tarFile = new File(appContext.getCacheDir(), SENSE_VOICE_MODEL_NAME + ".tar.bz2");
                downloadFile(SENSE_VOICE_MODEL_URL, tarFile, "Downloading better local speech model...", 2, 86, main, callback);

                postProgress(main, callback, "Unpacking better local speech model...", 89);
                File targetDir = getBetterModelDir(appContext);
                File extractRoot = new File(appContext.getCacheDir(), SENSE_VOICE_MODEL_NAME + "-extract");
                deleteRecursive(extractRoot);
                File extractedModel = new File(extractRoot, SENSE_VOICE_MODEL_NAME);
                if (!extractedModel.mkdirs() && !extractedModel.isDirectory()) {
                    throw new IOException("Could not create model extraction folder.");
                }
                unpackSenseVoiceTar(tarFile, extractedModel);

                File vadFile = new File(extractedModel, SENSE_VOICE_VAD_FILE);
                downloadFile(SENSE_VOICE_VAD_URL, vadFile, "Downloading voice activity model...", 96, 3, main, callback);

                if (!isBetterModelReady(extractedModel)) {
                    throw new IOException("Downloaded files did not contain the expected SenseVoice model files.");
                }

                deleteRecursive(targetDir);
                File modelParent = targetDir.getParentFile();
                if (modelParent != null && !modelParent.exists() && !modelParent.mkdirs()) {
                    throw new IOException("Could not create app model folder.");
                }
                copyDirectory(extractedModel, targetDir);
                deleteRecursive(extractRoot);
                //noinspection ResultOfMethodCallIgnored
                tarFile.delete();

                main.post(() -> callback.onComplete(targetDir));
            } catch (Exception exception) {
                main.post(() -> callback.onError(exception));
            }
        });
    }

    private static void downloadZip(File zipFile, Handler main, Callback callback) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "LiveMediaTranslator/0.6");

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("Model download failed with HTTP " + responseCode + ".");
        }

        int totalBytes = connection.getContentLength();
        byte[] buffer = new byte[32 * 1024];
        long downloaded = 0L;
        int lastPercent = 0;

        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(zipFile)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (totalBytes > 0) {
                    int percent = 2 + (int) Math.min(92, (downloaded * 92L) / totalBytes);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        postProgress(main, callback, "Downloading Japanese speech model...", percent);
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadFile(
            String url,
            File outputFile,
            String progressMessage,
            int progressStart,
            int progressSpan,
            Handler main,
            Callback callback) throws IOException {
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "LiveMediaTranslator/0.6");

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IOException("Download failed with HTTP " + responseCode + ".");
        }

        int totalBytes = connection.getContentLength();
        byte[] buffer = new byte[64 * 1024];
        long downloaded = 0L;
        int lastPercent = 0;

        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(outputFile)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (totalBytes > 0) {
                    int percent = progressStart + (int) Math.min(progressSpan, (downloaded * progressSpan) / totalBytes);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        postProgress(main, callback, progressMessage, percent);
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        postProgress(main, callback, progressMessage, progressStart + progressSpan);
    }

    private static void unpackSenseVoiceTar(File tarBzFile, File destinationRoot) throws IOException {
        String destinationPath = destinationRoot.getCanonicalPath() + File.separator;
        String prefix = SENSE_VOICE_MODEL_NAME + "/";
        byte[] buffer = new byte[64 * 1024];

        try (InputStream input = new BufferedInputStream(new FileInputStream(tarBzFile));
             BZip2CompressorInputStream bzip = new BZip2CompressorInputStream(input);
             TarArchiveInputStream tar = new TarArchiveInputStream(bzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                String relativeName;
                if ((prefix + "model.int8.onnx").equals(name)) {
                    relativeName = "model.int8.onnx";
                } else if ((prefix + "tokens.txt").equals(name)) {
                    relativeName = "tokens.txt";
                } else if ((prefix + "README.md").equals(name)) {
                    relativeName = "README.md";
                } else {
                    continue;
                }

                File outputFile = new File(destinationRoot, relativeName);
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(destinationPath)) {
                    throw new IOException("Blocked unsafe tar entry: " + name);
                }

                File parent = outputFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create " + parent);
                }
                try (FileOutputStream output = new FileOutputStream(outputFile)) {
                    int read;
                    while ((read = tar.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static void unzip(File zipFile, File destinationRoot) throws IOException {
        String destinationPath = destinationRoot.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[32 * 1024];

        try (ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                File outputFile = new File(destinationRoot, entry.getName());
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(destinationPath)) {
                    throw new IOException("Blocked unsafe zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw new IOException("Could not create " + outputFile);
                    }
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create " + parent);
                    }
                    try (FileOutputStream output = new FileOutputStream(outputFile)) {
                        int read;
                        while ((read = zipInput.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        }
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Could not create " + target);
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyDirectory(child, new File(target, child.getName()));
            }
        } else {
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
        }
    }

    private static void deleteRecursive(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Could not delete " + file);
        }
    }

    private static void postProgress(Handler main, Callback callback, String message, int percent) {
        main.post(() -> callback.onProgress(message, percent));
    }
}
