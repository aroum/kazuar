// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.diagnostics;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Process;
import android.util.AtomicFile;
import io.github.aroum.kazuar.inputmethod.latin.R;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded, private, non-backed-up report. No editor, clipboard, or logcat collection. */
public final class CrashReporter {
    static final int MAX_CHARS = 16000;
    private static Handler installed;
    private CrashReporter() {}

    public static synchronized void install(Context context) {
        if (installed != null) return;
        installed = new Handler(context.getApplicationContext(), Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(installed);
    }

    public static synchronized void uninstall() {
        if (installed != null && Thread.getDefaultUncaughtExceptionHandler() == installed) {
            Thread.setDefaultUncaughtExceptionHandler(installed.delegate);
        }
        installed = null;
    }

    static final class Handler implements Thread.UncaughtExceptionHandler {
        private final Context context;
        private final Thread.UncaughtExceptionHandler delegate;
        private final AtomicBoolean handling = new AtomicBoolean();
        Handler(Context context, Thread.UncaughtExceptionHandler delegate) {
            this.context = context; this.delegate = delegate;
        }
        @Override public void uncaughtException(Thread thread, Throwable failure) {
            try {
                if (handling.compareAndSet(false, true)) {
                    save(context, System.currentTimeMillis(), Process.myPid(), format(context, thread, failure));
                }
            } catch (Throwable ignored) {
                // Reporting must never prevent Android's normal crash handling.
            } finally {
                if (delegate != null) delegate.uncaughtException(thread, failure);
                else { Process.killProcess(Process.myPid()); System.exit(10); }
            }
        }
    }

    private static String metadata(Context context) {
        String version = "unknown";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (Exception ignored) {}
        return "kazuar " + version + "\nBuild: " + context.getString(R.string.build_variant)
                + "\nRevision: " + context.getString(R.string.build_revision)
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + "\n";
    }

    static String format(Context context, Thread thread, Throwable failure) {
        StringBuilder text = new StringBuilder(metadata(context));
        text.append("Time (UTC epoch ms): ").append(System.currentTimeMillis())
                .append("\nThread: ").append(limit(thread.getName(), 128)).append("\n\n");
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        for (int cause = 0; current != null && cause < 12 && seen.add(current); cause++) {
            text.append(cause == 0 ? "" : "Caused by: ").append(current.getClass().getName())
                    .append(": ").append(limit(current.getMessage(), 1024)).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 80) && text.length() < MAX_CHARS; i++) {
                text.append("    at ").append(limit(frames[i].toString(), 512)).append('\n');
            }
            if (text.length() >= MAX_CHARS) break;
            current = current.getCause();
        }
        return limit(text.toString(), MAX_CHARS);
    }

    private static String limit(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(), "last-crash.json"));
    }

    static boolean save(Context context, long timestamp, int pid, String text) {
        FileOutputStream output = null;
        AtomicFile file = null;
        try {
            file = file(context);
            byte[] bytes = new JSONObject().put("timestamp", timestamp).put("pid", pid)
                    .put("text", limit(text, MAX_CHARS)).toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65536) return false;
            output = file.startWrite(); output.write(bytes); file.finishWrite(output);
            return true;
        } catch (Exception ignored) {
            if (file != null && output != null) file.failWrite(output);
            return false;
        }
    }

    private static JSONObject read(Context context) {
        try {
            AtomicFile file = file(context);
            if (file.getBaseFile().length() > 65536) return null;
            try (java.io.FileInputStream input = file.openRead()) {
                byte[] bytes = input.readNBytes(65537);
                if (bytes.length > 65536) return null;
                return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { return null; }
    }

    public static String report(Context context) {
        JSONObject record = read(context);
        return record == null ? null : record.optString("text", null);
    }

    public static boolean takeNotice(Context context) {
        JSONObject record = read(context);
        if (record == null) return false;
        long time = record.optLong("timestamp", 0);
        android.content.SharedPreferences state = context.getSharedPreferences("crash-report-state", Context.MODE_PRIVATE);
        if (time <= state.getLong("seen", Long.MIN_VALUE)) return false;
        state.edit().putLong("seen", time).apply();
        return true;
    }

    /** Android can also identify native crashes/ANRs, which bypass Java's uncaught handler. */
    public static void recoverSystemExit(Context context) {
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return;
            JSONObject previous = read(context);
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5)) {
                int reason = exit.getReason();
                if (reason != ApplicationExitInfo.REASON_CRASH && reason != ApplicationExitInfo.REASON_CRASH_NATIVE
                        && reason != ApplicationExitInfo.REASON_ANR) continue;
                if (previous != null && (exit.getTimestamp() <= previous.optLong("timestamp")
                        || (exit.getPid() == previous.optInt("pid")
                        && Math.abs(exit.getTimestamp() - previous.optLong("timestamp")) < 120000))) return;
                String text = "System-only crash report; previous build and Java stack are unavailable.\n"
                        + "Collector: " + metadata(context) + "\nProcess: " + exit.getProcessName()
                        + "\nReason: " + reason + "\nStatus/signal: " + exit.getStatus()
                        + "\nTime (UTC epoch ms): " + exit.getTimestamp()
                        + "\nDescription: " + limit(exit.getDescription(), 2048);
                save(context, exit.getTimestamp(), exit.getPid(), text);
                return;
            }
        } catch (Exception ignored) {
            // OEMs may restrict access; Java crash reports remain available.
        }
    }
}
