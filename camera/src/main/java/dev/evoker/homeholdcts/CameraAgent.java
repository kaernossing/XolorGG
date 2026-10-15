// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import io.github.joeyandu.colorcamera.BuildConfig;

/** Shell agent: one protected app connection, no logcat, assistants or networking. */
public final class CameraAgent {
    private final AgentLifecycle life = new AgentLifecycle();
    private final DoublePowerRoute route = new DoublePowerRoute();
    private final ThreadPoolExecutor launches = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());
    private final Object am, tm;
    private final Class<?> amApi, tmApi;
    private final Method callerUid, setController;
    private final CameraQueries camera;
    private final int appUid;
    private final Binder controllerBinder;
    private final Object controller;
    private volatile IBinder providerBinder;
    private Object provider;
    private IBinder providerToken;
    private IBinder.DeathRecipient death;
    private volatile boolean cleanup, installed;
    private volatile String owned = "", error = "", result = "NONE";
    private int attempts;

    private static final class BridgeUnavailable extends Exception {}

    private CameraAgent() throws Exception {
        am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        tm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        amApi = Class.forName("android.app.IActivityManager"); tmApi = Class.forName("android.app.IActivityTaskManager");
        camera = new CameraQueries(am);
        appUid = packageUid();
        callerUid = Intent.class.getMethod("getCallingUid");
        Class<?> ctl = Class.forName("android.app.IActivityController");
        setController = tmApi.getMethod("setActivityController", ctl, boolean.class);
        controllerBinder = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { reply.writeString("android.app.IActivityController"); return true; }
                if (code < 1 || code > 6) return super.onTransact(code, data, reply, flags);
                data.enforceInterface("android.app.IActivityController");
                int answer = 1;
                if (code == 1) {
                    long received = SystemClock.elapsedRealtimeNanos();
                    Intent i = data.readInt() == 0 ? null : Intent.CREATOR.createFromParcel(data);
                    String pkg = data.readString();
                    int epoch = life.generation();
                    if (ready(epoch) && matches(i, pkg)) {
                        int token = route.accept(SystemClock.elapsedRealtime());
                        answer = 0;
                        if (token >= 0) launches.execute(() -> launch(epoch, token, received));
                    }
                } else if (code == 4 || code == 5) answer = 0;
                else if (code == 6) answer = -1;
                reply.writeNoException(); reply.writeInt(answer); return true;
            }
        };
        controller = Proxy.newProxyInstance(ctl.getClassLoader(), new Class<?>[]{ctl}, (o,m,a) -> {
            if (m.getName().equals("asBinder")) return controllerBinder;
            throw new UnsupportedOperationException(m.getName());
        });
    }
    private boolean ready(int epoch) {
        IBinder p = providerBinder;
        return installed && life.dispatch(epoch) && route.enabled() && p != null && p.isBinderAlive();
    }
    private boolean matches(Intent i, String pkg) {
        if (i == null || i.getComponent() == null) return false;
        try { return DoublePowerRoute.matches((int) callerUid.invoke(i), pkg, i.getComponent().getPackageName(),
                i.getComponent().getClassName(), i.getAction()); }
        catch (ReflectiveOperationException e) { fail("Caller UID unavailable"); return false; }
    }
    private void fail(String message) {
        error = message; route.configure(false, DoublePowerRoute.CAMERA); life.stop();
    }
    private void stop() { route.configure(false, DoublePowerRoute.CAMERA); life.stop(); CameraDiagnostics.shell("EXPLICIT_STOP"); }
    private Bundle status() {
        Bundle b = new Bundle(); b.putString("state", life.state().name());
        b.putInt("pid", android.os.Process.myPid()); b.putInt("version", BuildConfig.VERSION_CODE);
        b.putBoolean("active", ready(life.generation())); b.putString("error", error);
        b.putString("lastRequest", result); b.putInt("generation", life.generation()); return b;
    }
    private void verifyCaller() {
        int uid = Binder.getCallingUid(); if (uid != appUid && uid != 2000) throw new SecurityException("Own app or ADB required");
    }
    private ICameraAgent rpc(int epoch) {
        return new ICameraAgent.Stub() {
            @Override public Bundle getStatus() { verifyCaller(); return status(); }
            @Override public void shutdown() {
                verifyCaller();
                if (life.current(epoch)) stop(); // Old connections cannot stop a newer generation.
            }
        };
    }
    private Bundle call(String method, Bundle data) throws Exception {
        AttributionSource source = new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
        Bundle b = (Bundle) Class.forName("android.content.IContentProvider").getMethod("call", AttributionSource.class,
                String.class, String.class, String.class, Bundle.class)
                .invoke(provider, source, CameraBridge.AUTHORITY, method, null, data);
        if (b == null) throw new IllegalStateException("Bridge returned no state");
        return b;
    }
    private void attach(int epoch) throws Exception {
        if (packageUid() != appUid) throw new SecurityException("App identity changed");
        providerToken = new Binder();
        Object holder = amApi.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
                .invoke(am, CameraBridge.AUTHORITY, 0, providerToken, "ColorCamera ADB agent");
        if (holder == null) throw new BridgeUnavailable();
        provider = holder.getClass().getField("provider").get(holder);
        providerBinder = ((IInterface) provider).asBinder();
        death = () -> lost(epoch, "APP_CONNECTION_LOST"); providerBinder.linkToDeath(death, 0);
        Bundle args = new Bundle(); args.putBinder("agent", rpc(epoch).asBinder());
        args.putInt("version", BuildConfig.VERSION_CODE); args.putInt("generation", epoch);
        Bundle config = call("attach", args);
        if (config.getInt("version", -1) != BuildConfig.VERSION_CODE) throw new SecurityException("App version changed");
        if (!config.getBoolean("requested", false)) { stop(); return; }
        if (camera.currentUser() != 0) throw new SecurityException("Only the primary user is supported");
        camera.resolve(false); camera.resolve(true); ShellCamera.locked();
        if (!life.current(epoch)) return;
        installController();
        if (life.connected(epoch)) {
            route.configure(true, DoublePowerRoute.CAMERA);
            CameraDiagnostics.shell("CONNECTED generation=" + epoch);
        }
    }
    private void installController() throws Exception {
        String current = ControllerOwnership.read();
        if (!current.isEmpty() && !current.equals(call("dead_controller", null).getString("identity", "")))
            throw new SecurityException("Another activity monitor is active. Stop it before activation.");
        ShellCamera.closeOldShortcut(tm);
        if (!current.equals(ControllerOwnership.read())) throw new SecurityException("Activity monitor changed during activation");
        setController.invoke(tm, controller, false);
        String identity = ControllerOwnership.read();
        if (identity.isEmpty() || identity.equals(current)) throw new SecurityException("Cannot establish controller ownership");
        owned = identity; installed = true;
        Bundle b = new Bundle(); b.putBinder("controller", controllerBinder); b.putString("identity", owned); call("remember", b);
    }
    private void lost(int epoch, String reason) {
        if (life.lost(epoch, SystemClock.elapsedRealtime())) {
            route.configure(false, DoublePowerRoute.CAMERA); launches.getQueue().clear(); cleanup = true;
            CameraDiagnostics.shell(reason + " generation=" + epoch);
        }
    }
    private void detach() {
        cleanup = false; route.configure(false, DoublePowerRoute.CAMERA); launches.getQueue().clear();
        try { if (AgentLifecycle.ownsController(owned, ControllerOwnership.read())) setController.invoke(tm, null, false); }
        catch (Exception e) { CameraDiagnostics.shell("RELEASE_FAILED " + e.getClass().getSimpleName()); }
        installed = false; owned = "";
        if (providerBinder != null && death != null) providerBinder.unlinkToDeath(death, 0);
        if (providerToken != null) try {
            amApi.getMethod("removeContentProviderExternalAsUser", String.class, IBinder.class, int.class)
                    .invoke(am, CameraBridge.AUTHORITY, providerToken, 0);
        } catch (ReflectiveOperationException e) { CameraDiagnostics.shell("BRIDGE_RELEASE_FAILED"); }
        providerBinder = null; provider = null; providerToken = null;
    }
    private void launch(int epoch, int token, long received) {
        CameraLaunchTiming timing = new CameraLaunchTiming(SystemClock::elapsedRealtimeNanos, received, ++attempts, "binder");
        String outcome = "CANCELLED";
        try {
            outcome = CameraLaunchOperation.run(new CameraLaunchOperation.Host() {
                public boolean current() { return ready(epoch) && route.current(token); }
                public int user() throws Exception { return camera.currentUser(); }
                public boolean locked() throws Exception { return ShellCamera.locked(); }
                public String resolve(boolean locked) throws Exception { return camera.resolve(locked); }
                public String start(boolean locked, String component) throws Exception {
                    return BoundedDiagnosticCommand.run(8000, 16384, "/system/bin/am", "start", "-W", "--user", "0",
                            "-a", ShellCamera.action(locked), "-n", component);
                }
            }, timing);
            if ("ACCEPTED".equals(outcome)) {
                error = ""; result = "Request accepted; camera display is not independently confirmed";
            }
        } catch (Exception e) {
            outcome = "FAILED"; result = "Failed: " + e.getClass().getSimpleName();
            error = "Camera launch failed: " + e.getMessage();
        } finally { CameraDiagnostics.shell(timing.finish(outcome)); }
    }
    private static int packageUid() throws Exception {
        String out = BoundedDiagnosticCommand.run(3000, 8192, "/system/bin/cmd", "package", "list", "packages", "-U", "--user", "0", BuildConfig.APPLICATION_ID);
        String prefix = "package:" + BuildConfig.APPLICATION_ID + " uid:";
        for (String line : out.split("\\n")) if (line.startsWith(prefix)) return Integer.parseInt(line.substring(prefix.length()).trim());
        throw new SecurityException("ColorCamera package missing");
    }
    public static void main(String[] args) {
        int exit = 0;
        try {
            if (android.os.Process.myUid() != 2000) throw new SecurityException("ADB shell required");
            Looper.prepareMainLooper();
            try (SharedAgentLease lease = new SharedAgentLease("colorgoogle-search")) {
                CameraAgent a = new CameraAgent();
                CameraDiagnostics.shell("START");
                try {
                    long checked = 0;
                    while (a.life.state() != AgentLifecycle.State.STOPPING) {
                        if (a.cleanup) a.detach();
                        int epoch = a.life.begin(SystemClock.elapsedRealtime());
                        if (epoch >= 0) try { a.attach(epoch); }
                        catch (Exception e) {
                            Throwable cause = e; while (cause.getCause() != null) cause = cause.getCause();
                            if (cause instanceof DeadObjectException || cause instanceof BridgeUnavailable) a.lost(epoch, "APP_DIED_DURING_ATTACH");
                            else { a.fail("Activation failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage()); }
                        }
                        if (a.installed && SystemClock.elapsedRealtime() - checked > 3000) {
                            checked = SystemClock.elapsedRealtime();
                            if (!AgentLifecycle.ownsController(a.owned, ControllerOwnership.read())) a.fail("Another monitor replaced the controller");
                        }
                        Thread.sleep(250);
                    }
                } finally {
                    a.life.stop();
                    {
                        CameraDiagnostics.shell(a.error.isEmpty() ? "STOPPED" : "ERROR " + a.error);
                        if (a.provider != null) try { Bundle report = new Bundle(); report.putString("error", a.error.isEmpty() ? "Stopped; enable and reactivate to resume" : a.error); a.call("report", report); }
                        catch (Exception e) { CameraDiagnostics.shell("REPORT_FAILED " + e.getClass().getSimpleName()); }
                    }
                    a.detach(); a.launches.shutdownNow(); CameraDiagnostics.shell("EXIT");
                }
            }
        } catch (Exception e) { CameraDiagnostics.shell("FATAL " + e.getClass().getSimpleName() + ": " + e.getMessage()); System.err.println(e); exit = 1; }
        System.exit(exit);
    }
}
