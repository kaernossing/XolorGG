// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import java.io.File;
import android.content.Context;
import android.os.SystemClock;
final class CameraDiagnostics {
    private static final String SESSION = android.os.Process.myPid() + "-" + SystemClock.elapsedRealtime();
    static void shell(String event) { write(new File("/data/local/tmp/colorcamera-events.log"), event); }
    static void app(Context c, String event) { write(new File(c.getFilesDir(), "camera-events.log"), event); }
    private static synchronized void write(File f, String event) {
        try { RollingDiagnosticLog.append(f, System.currentTimeMillis() + " elapsed=" + SystemClock.elapsedRealtime()
                + " session=" + SESSION + " " + event, 128 * 1024); }
        catch (java.io.IOException e) { android.util.Log.e("ColorCamera", "Diagnostic write failed", e); }
    }
}
