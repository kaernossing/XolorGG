// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import io.github.joeyandu.colorcamera.BuildConfig;

/** The sole bootstrap endpoint: DUMP permission plus explicit shell UID validation. */
public final class CameraBridge extends ContentProvider {
    static final String AUTHORITY = "io.github.joeyandu.colorcamera.bridge";
    static volatile ICameraAgent agent;
    private static volatile String lastError = "";
    private static volatile boolean reportedExit;
    private static IBinder rememberedController;
    private static String rememberedIdentity = "";
    static boolean requested(Context c) { return c.getSharedPreferences("camera", 0).getBoolean("enabled", false); }
    static void requested(Context c, boolean enabled) {
        if (!c.getSharedPreferences("camera", 0).edit().putBoolean("enabled", enabled).commit())
            throw new IllegalStateException("Cannot save switch");
        CameraDiagnostics.app(c, "RUN_INTENT enabled=" + enabled);
        if (!enabled) {
            ICameraAgent current = agent;
            if (current != null) try { current.shutdown(); }
            catch (RemoteException e) { lastError = "Stop saved; agent disconnected"; }
        }
    }
    static Bundle status(Context c) {
        Bundle b = new Bundle();
        ICameraAgent current = agent;
        if (current != null) {
            try { b = current.getStatus(); b.putBoolean("connected", true); }
            catch (RemoteException e) { if (agent == current) agent = null; }
        }
        if (!b.getBoolean("connected")) { b.putString("state", "ABSENT"); b.putString("error", lastError); }
        b.putBoolean("requested", requested(c)); b.putInt("appVersion", BuildConfig.VERSION_CODE);
        return b;
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != 2000) throw new SecurityException("ADB shell required");
        Context c = getContext();
        Bundle out = new Bundle(); out.putInt("version", BuildConfig.VERSION_CODE);
        if ("attach".equals(method)) {
            if (extras == null || extras.getInt("version", -1) != BuildConfig.VERSION_CODE)
                throw new SecurityException("Version mismatch; reactivate using this installed APK");
            IBinder binder = extras.getBinder("agent");
            if (binder == null) throw new SecurityException("Agent Binder missing");
            ICameraAgent next = ICameraAgent.Stub.asInterface(binder);
            try { binder.linkToDeath(() -> {
                if (agent == next) { agent = null; if (!reportedExit) lastError = "Agent disappeared; cause unconfirmed. Reactivate with ADB.";
                    CameraDiagnostics.app(c, reportedExit ? "AGENT_EXIT_REPORTED" : "AGENT_DISAPPEARED cause=UNCONFIRMED"); }
            }, 0); } catch (RemoteException e) { throw new IllegalStateException("Agent already disconnected", e); }
            agent = next; lastError = ""; reportedExit = false;
            out.putBoolean("requested", requested(c));
            CameraDiagnostics.app(c, "ATTACH generation=" + extras.getInt("generation"));
        } else if ("status".equals(method)) return status(c);
        else if ("report".equals(method)) {
            lastError = extras == null ? "Unknown activation failure" : extras.getString("error", "Unknown activation failure");
            CameraDiagnostics.app(c, "AGENT_REPORT " + lastError);
        } else if ("remember".equals(method)) {
            synchronized (CameraBridge.class) {
                rememberedController = extras.getBinder("controller"); rememberedIdentity = extras.getString("identity", "");
            }
        } else if ("dead_controller".equals(method)) {
            synchronized (CameraBridge.class) {
                if (rememberedController != null && !rememberedController.pingBinder()) out.putString("identity", rememberedIdentity);
            }
        } else throw new IllegalArgumentException("Unknown method");
        return out;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
