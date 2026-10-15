// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.github.joeyandu.colorcamera.BuildConfig;

/** No Google setup. A single opt-in with honest agent and notification state. */
public final class CameraActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Switch toggle;
    private boolean rendering, resumed;
    private final Runnable refresh = new Runnable() { @Override public void run() { load(); if (resumed) handler.postDelayed(this, 3000); } };
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout box, String message, int size) {
        TextView t = new TextView(this); t.setText(message); t.setTextSize(size); t.setPadding(0, dp(10), 0, dp(10)); box.addView(t); return t;
    }
    private void button(LinearLayout box, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); box.addView(b); b.setOnClickListener(v -> action.run());
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(40), dp(24), dp(32)); scroll.setFillViewport(true); scroll.addView(box); setContentView(scroll);
        text(box, "ColorCamera", 30);
        text(box, "Double-press power → system camera", 18);
        text(box, "1. Check compatibility\nDesigned for the verified OPPO shortcut on ColorOS. Full interface and secure-camera checks run during ADB activation. Other phones are unverified.", 15);
        button(box, "Check camera entries", this::checkCamera);
        text(box, "2. Keep the system shortcut enabled\nIn phone settings, enable double-press power to open the system wallet. ColorCamera redirects only that exact shortcut. Stop ColorGoogle before activating ColorCamera.", 15);
        button(box, "Open phone settings", () -> startActivity(new Intent(Settings.ACTION_SETTINGS)));
        text(box, "3. Enable, then activate with computer ADB\nNo root, Google apps, Shizuku or log-reading permission required. The camera shortcut only works while the ADB agent is connected.", 15);
        toggle = new Switch(this); toggle.setText("Enable camera shortcut"); toggle.setPadding(0, dp(16), 0, dp(16)); box.addView(toggle);
        toggle.setChecked(CameraBridge.requested(this));
        toggle.setOnCheckedChangeListener((b, enabled) -> {
            if (rendering) return;
            toggle.setEnabled(false);
            worker.execute(() -> {
                try {
                    CameraBridge.requested(this, enabled);
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        if (enabled) startForegroundService(new Intent(this, CameraService.class));
                        else stopService(new Intent(this, CameraService.class));
                        toggle.setEnabled(true); load();
                        if (enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
                                && !getPreferences(0).getBoolean("notificationAsked", false)) {
                            getPreferences(0).edit().putBoolean("notificationAsked", true).apply();
                            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                        }
                    });
                } catch (RuntimeException e) { runOnUiThread(() -> { toggle.setEnabled(true); show("Cannot change switch", e.getMessage()); load(); }); }
            });
        });
        button(box, "Copy ADB activation command", () -> {
            // Use a script file for identical macOS / PowerShell quoting; show platform-specific docs below.
            String script = "adb -s YOUR_DEVICE_SERIAL push activate-camera.sh /data/local/tmp/activate-camera.sh\n"
                    + "adb -s YOUR_DEVICE_SERIAL shell sh /data/local/tmp/activate-camera.sh";
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("ColorCamera ADB", script));
            show("Commands copied", "Download tools/activate-camera.sh from the repository and replace YOUR_DEVICE_SERIAL with the phone selected from adb devices. See the setup guide for PowerShell.");
        });
        button(box, "Installation guide / 中文说明", () -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/joeyandu/ColorGoogle/blob/main/docs/COLORCAMERA.md"))));
        status = text(box, "Checking status…", 15);
        text(box, "4. Try double-pressing power\nWhile locked, ColorCamera uses the secure system camera. It does not unlock your phone or change payment settings. Turning this switch off restores the original system shortcut.", 15);
        button(box, "Notification settings", () -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        button(box, "Background / app settings", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        button(box, "Diagnostics", () -> worker.execute(() -> {
            String log;
            try { java.io.File f = new java.io.File(getFilesDir(), "camera-events.log");
                log = f.exists() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8) : "No application events yet.";
            } catch (java.io.IOException e) { log = "Cannot read diagnostics: " + e.getClass().getSimpleName(); }
            String display = log.length() > 12000 ? log.substring(log.length()-12000) : log;
            runOnUiThread(() -> show("Local diagnostics", display + "\n\nAgent timing logs: /data/local/tmp/colorcamera-events.log (ADB pull). Two files per side, 128 KiB each. No images, audio or search content."));
        }));
        button(box, "Open-source license and notices", () -> worker.execute(() -> {
            try (java.io.InputStream license = getAssets().open("LICENSE.txt"); java.io.InputStream notice = getAssets().open("NOTICE.txt")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int n;
                while ((n = notice.read(buffer)) >= 0) bytes.write(buffer, 0, n);
                bytes.write('\n');
                while ((n = license.read(buffer)) >= 0) bytes.write(buffer, 0, n);
                String content = bytes.toString("UTF-8"); runOnUiThread(() -> show("GPL-3.0-only", content));
            } catch (java.io.IOException e) { runOnUiThread(() -> show("License unavailable", e.getClass().getSimpleName())); }
        }));
        text(box, "1.0.0 · Preview: physical-device acceptance pending\nReboot or ADB daemon restart may end the agent. Reconnect an authorized computer to activate again.\n\nOpen source · GPL-3.0-only\nBased on ColorGoogle and MindTrigger Assist by EvokerUniverse, with upstream MiCTS attribution. Not affiliated with OPPO or Google.", 13);
    }
    private void checkCamera() {
        StringBuilder info = new StringBuilder();
        for (boolean locked : new boolean[]{false, true}) {
            Intent i = new Intent(ShellCamera.action(locked)).setPackage("com.oplus.camera");
            android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(i, 0);
            info.append(locked ? "Secure camera: " : "Normal camera: ").append(r != null && r.activityInfo != null ? "entry visible" : "not visible").append('\n');
        }
        info.append("This app-side check does not prove shell permissions or shortcut interception. ADB activation validates those before enabling interception."); show("Compatibility", info.toString());
    }
    private void show(String title, String message) { if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }
    private void load() {
        if (worker.isShutdown()) return;
        worker.execute(() -> {
            Bundle b = CameraBridge.status(this);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                rendering = true; toggle.setChecked(b.getBoolean("requested")); rendering = false;
                String mode = !b.getBoolean("requested") ? "Off — original system shortcut" : b.getBoolean("active") ? "Agent connected — camera shortcut ready" : "Not ready — activate with ADB / inspect error";
                status.setText(mode + "\nAgent: " + b.getString("state", "ABSENT")
                        + "\n" + b.getString("error", "") + "\nLast request: " + b.getString("lastRequest", "None")
                        + "\nNotifications: " + (getSystemService(NotificationManager.class).areNotificationsEnabled() ? "allowed" : "restricted (does not mean the agent stopped)"));
            });
        });
    }
    @Override protected void onResume() { super.onResume(); resumed = true; if (CameraBridge.requested(this)) startForegroundService(new Intent(this, CameraService.class)); handler.post(refresh); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(refresh); worker.shutdown(); super.onDestroy(); }
}
