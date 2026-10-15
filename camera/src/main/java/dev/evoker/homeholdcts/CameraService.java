// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import android.app.*;
import android.content.*;
import android.os.*;
import io.github.joeyandu.colorcamera.R;

public final class CameraService extends Service {
    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (!CameraBridge.requested(this)) { stopSelf(); return START_NOT_STICKY; }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("camera", "Camera shortcut", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, CameraActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "camera")
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("ColorCamera")
                .setContentText("Shortcut enabled · open to check ADB agent status")
                .setContentIntent(open).setOngoing(true).build());
        CameraDiagnostics.app(this, "SERVICE_RUNNING");
        return START_STICKY;
    }
    @Override public void onDestroy() { CameraDiagnostics.app(this, "SERVICE_DESTROYED intent_unchanged"); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
