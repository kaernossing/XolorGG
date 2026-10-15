// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.Context;
import android.os.SystemClock;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Two bounded files per process. Callers supply structural facts, never user content. */
final class AgentDiagnostics {
    private static final int LIMIT = 128 * 1024;
    private static final String SESSION = android.os.Process.myPid() + "-" + SystemClock.elapsedRealtime();
    private static File shellFile() { return new File("/data/local/tmp/colorgoogle-agent-events.log"); }
    static void shell(String event) { write(shellFile(), event); }
    static void app(Context context, String event) {
        if (!android.app.Application.getProcessName().endsWith(":watcher")) {
            android.util.Log.e("ColorGoogleDiagnostics", "App diagnostics must be written by :watcher");
            return;
        }
        write(new File(context.getFilesDir(), "survival-" + android.os.Process.myPid() + ".log"), event);
    }
    static synchronized void write(File file, String event) {
        try {
            // :watcher owns one stable app log, including across process restarts.
            if (file.getName().startsWith("survival-")) file = new File(file.getParentFile(), "survival.log");
            String line = System.currentTimeMillis() + " elapsed=" + SystemClock.elapsedRealtime()
                    + " session=" + SESSION + " " + event;
            RollingDiagnosticLog.append(file, line, LIMIT);
        } catch (Exception e) { android.util.Log.e("ColorGoogleDiagnostics", "Cannot persist diagnostics: " + e.getClass().getSimpleName()); }
    }
    static String readApp(Context context) {
        try {
            File file = new File(context.getFilesDir(), "survival.log");
            if (!file.exists()) return "";
            byte[] bytes = Files.readAllBytes(file.toPath());
            int start = Math.max(0, bytes.length - 32768);
            return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
        } catch (Exception e) { return "DIAGNOSTICS_UNAVAILABLE:" + e.getClass().getSimpleName(); }
    }
    static String group() {
        try { return new String(Files.readAllBytes(new File("/proc/self/cgroup").toPath()), StandardCharsets.UTF_8).trim().replace('\n', ';'); }
        catch (Exception e) { return "unavailable:" + e.getClass().getSimpleName(); }
    }
    static String session() { return SESSION; }
    private AgentDiagnostics() {}
}
