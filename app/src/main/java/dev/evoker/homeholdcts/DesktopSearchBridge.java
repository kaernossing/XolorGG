// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/** Shell-only bootstrap. Normal apps cannot install a privileged Binder here. */
public final class DesktopSearchBridge extends ContentProvider {
    static final String AUTHORITY = "com.kaernossing.xolorgg.desktopsearch";
    static final String PREF = "desktop_search_google";
    static volatile IDesktopSearchAgent agent;
    private static IBinder lastController;
    private static String lastControllerIdentity = "";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != 2000) throw new SecurityException("ADB shell required");
        Bundle out = new Bundle();
        try {
            if ("attach".equals(method)) {
                IBinder binder = extras == null ? null : extras.getBinder("agent");
                if (binder == null) throw new IllegalArgumentException("Missing agent");
                if (extras.getInt("version", -1) != BuildConfig.VERSION_CODE) throw new SecurityException("Agent version mismatch");
                out.putInt("version", BuildConfig.VERSION_CODE);
                IDesktopSearchAgent next = IDesktopSearchAgent.Stub.asInterface(binder);
                binder.linkToDeath(() -> { if (agent == next) agent = null; }, 0);
                agent = next;
                out.putBoolean("enabled", getContext().getSharedPreferences(
                        MainActivity.PREFS, 0).getBoolean(PREF, false));
                out.putBoolean("doublePowerRequested", getContext().getSharedPreferences(MainActivity.PREFS, 0)
                        .getBoolean(DoublePowerRoute.PREF_ENABLED, false));
                out.putString("doublePowerTarget", getContext().getSharedPreferences(MainActivity.PREFS, 0)
                        .getString(DoublePowerRoute.PREF_TARGET, DoublePowerRoute.WALLET));
            } else if ("remember_controller".equals(method)) {
                lastController = extras == null ? null : extras.getBinder("controller");
                lastControllerIdentity = extras == null ? "" : extras.getString("identity", "");
                out.putBoolean("saved", true);
            } else if ("dead_controller".equals(method)) {
                // isBinderAlive alone may be cached until this particular proxy transacts.
                if (lastController != null && !lastController.pingBinder())
                    out.putString("identity", lastControllerIdentity);
            } else if ("status".equals(method)) {
                IDesktopSearchAgent current = agent;
                boolean connected = false;
                if (current != null) {
                    try { out = current.getStatus(); connected = true; }
                    catch (android.os.RemoteException e) { if (agent == current) agent = null; }
                }
                if (!connected) out.putString("error", "Agent not connected");
                // Internal provider belongs to :watcher; do not forward the shell identity.
                long identity = Binder.clearCallingIdentity();
                try {
                    out.putBoolean("runEnabled", RunIntent.enabled(getContext()));
                    out.putInt("appVersionCode", BuildConfig.VERSION_CODE);
                    out.putBoolean("agentConnected", connected);
                    boolean requested = getContext().getSharedPreferences(MainActivity.PREFS, 0)
                            .getBoolean(PREF, false);
                    out.putBoolean("desktopRequested", requested);
                    if ("compact".equals(arg)) {
                        Bundle compact = new Bundle();
                        compact.putBoolean("runEnabled", out.getBoolean("runEnabled"));
                        compact.putInt("appVersionCode", BuildConfig.VERSION_CODE);
                        compact.putBoolean("agentConnected", connected);
                        compact.putInt("agentVersionCode", out.getInt("version", -1));
                        compact.putInt("pid", out.getInt("pid", -1));
                        compact.putString("lifecycle", out.getString("lifecycle", "ABSENT"));
                        compact.putBoolean("wakeActive", out.getBoolean("wakeActive"));
                        compact.putBoolean("desktopRequested", requested);
                        compact.putBoolean("desktopEnabled", out.getBoolean("enabled"));
                        return compact;
                    }
                    Bundle diagnostics = getContext().getContentResolver().call(WirelessRecoveryBridge.URI, "diagnostics", null, null);
                    if (diagnostics != null) out.putAll(diagnostics);
                } finally { Binder.restoreCallingIdentity(identity); }
            } else if ("stop".equals(method)) {
                if (agent != null) agent.shutdown();
                out.putBoolean("stopped", true);
            } else throw new IllegalArgumentException("Unknown method");
        } catch (android.os.RemoteException e) {
            throw new IllegalStateException("Agent disconnected", e);
        }
        return out;
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
