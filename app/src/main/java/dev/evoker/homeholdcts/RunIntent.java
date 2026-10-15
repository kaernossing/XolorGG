// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.Context;
import android.os.Bundle;

/** Durable master intent. Only :watcher reads/writes the authoritative preferences. */
final class RunIntent {
    private static synchronized android.content.SharedPreferences local(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("run_intent", 0);
        if (!p.contains("enabled") && !p.edit().putBoolean("enabled",
                c.getSharedPreferences(MainActivity.PREFS, 0).getBoolean(MainActivity.PREF_ENABLED, false)).commit())
            throw new IllegalStateException("Cannot migrate master intent");
        return p;
    }
    static boolean localEnabled(Context c) { return local(c).getBoolean("enabled", false); }
    static void setLocal(Context c, boolean enabled) {
        if (!local(c).edit().putBoolean("enabled", enabled).commit()) throw new IllegalStateException("Cannot save master intent");
        AgentDiagnostics.app(c, "RUN_INTENT enabled=" + enabled);
        if (!enabled) {
            WirelessRecovery.get(c).watcherRunning(false);
            IDesktopSearchAgent agent = WakeLogBridge.agent;
            if (agent != null) {
                try { agent.shutdown(); }
                catch (android.os.RemoteException e) { AgentDiagnostics.app(c, "STOP_PENDING bridge_disconnected"); }
            }
            RestartReceiver.cancelRestart(c);
            WatcherRescueJobService.cancelScheduled(c);
            c.stopService(new android.content.Intent(c, HomeHoldService.class));
        }
    }
    static boolean enabled(Context c) {
        if (android.app.Application.getProcessName().endsWith(":watcher")) return localEnabled(c);
        Bundle b = c.getContentResolver().call(WirelessRecoveryBridge.URI, "run_status", null, null);
        if (b == null) throw new IllegalStateException("Master intent unavailable");
        return b.getBoolean("runEnabled");
    }
    static void set(Context c, boolean enabled) {
        Bundle data = new Bundle(); data.putBoolean("runEnabled", enabled);
        Bundle result = c.getContentResolver().call(WirelessRecoveryBridge.URI, "run_intent", null, data);
        if (result == null || result.getBoolean("runEnabled") != enabled) throw new IllegalStateException("Master intent not saved");
    }
    private RunIntent() {}
}
