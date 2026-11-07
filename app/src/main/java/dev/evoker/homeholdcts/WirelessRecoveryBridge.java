// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Internal UI IPC into :watcher; no external pairing or command endpoint. */
public final class WirelessRecoveryBridge extends ContentProvider {
    static final Uri URI = Uri.parse("content://com.kaernossing.xolorgg.wireless");
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != android.os.Process.myUid()) throw new SecurityException("Same application required");
        if ("diagnostics".equals(method)) {
            Bundle result = new Bundle();
            if (!"summary".equals(arg)) result.putString("applicationDiagnostics", AgentDiagnostics.readApp(getContext()));
            ActivationDiagnostics.addTo(result);
            return result;
        }
        if ("run_intent".equals(method) || "run_status".equals(method)) {
            if ("run_intent".equals(method)) RunIntent.setLocal(getContext(), extras != null && extras.getBoolean("runEnabled"));
            Bundle result = new Bundle(); result.putBoolean("runEnabled", RunIntent.localEnabled(getContext())); return result;
        }
        return WirelessRecovery.get(getContext()).command(method, extras == null ? Bundle.EMPTY : extras);
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
