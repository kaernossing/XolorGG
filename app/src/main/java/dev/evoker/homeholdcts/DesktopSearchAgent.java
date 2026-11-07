// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.AttributionSource;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** ADB shell entry point, loaded from this signed APK; never runs as an app service. */
public final class DesktopSearchAgent {
    private static final String SELF = "com.kaernossing.xolorgg";
    private static final String DESC = "android.app.IActivityController";
    private final java.util.concurrent.ThreadPoolExecutor launches = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private final AgentLifecycle lifecycle = new AgentLifecycle();
    private final AssistantDiagnostics assistantDiagnostics = new AssistantDiagnostics();
    private volatile String reason = "STARTING";
    private IAdbWakeListener pendingListener;
    private IBinder.DeathRecipient desktopDeath, wakeDeath;
    private volatile boolean cleanupNeeded;
    private final DesktopSearchRule rule = new DesktopSearchRule();
    private final int appUid;
    private final int launcherUid;
    private final Method callingUid;
    private final Object taskManager;
    private final Method setController;
    private final Object controller;
    private final IBinder controllerBinder;
    private Object desktopProvider;
    private volatile boolean enabled;
    private volatile boolean controllerInstalled;
    private final DoublePowerRoute doublePower = new DoublePowerRoute();
    private volatile String doublePowerError = "";
    private volatile int doublePowerRequests;
    private final java.util.concurrent.atomic.AtomicInteger cameraAttempts = new java.util.concurrent.atomic.AtomicInteger();
    private volatile CameraQueries cameraQueries;
    private volatile long lastPowerObservation;


    private String controllerIdentity = "";
    private volatile String error = "";
    private volatile int redirects;
    private volatile String liveLaunchState = "IDLE";
    private volatile long lastTrigger;
    private IBinder providerBinder;
    private IBinder wakeProviderBinder;
    private IBinder wakeProviderToken;
    private volatile SingleDirectLogdSession wakeSession;
    private volatile boolean wakeActive;
    private volatile String wakeError = "";
    private volatile int wakeGeneration;
    private IBinder providerToken;
    private final Object activityManager;
    private final Class<?> managerInterface;

    private DesktopSearchAgent() throws Exception {
        appUid = packageUid(SELF);
        launcherUid = packageUid("com.android.launcher");
        callingUid = Intent.class.getMethod("getCallingUid");
        taskManager = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Class<?> ctl = Class.forName(DESC);
        setController = Class.forName("android.app.IActivityTaskManager")
                .getMethod("setActivityController", ctl, boolean.class);
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { reply.writeString(DESC); return true; }
                if (code < 1 || code > 6) return super.onTransact(code, data, reply, flags);
                data.enforceInterface(DESC);
                int result = 1;
                if (code == 1) {
                    long callbackStarted = SystemClock.elapsedRealtimeNanos();
                    Intent intent = data.readInt() != 0 ? Intent.CREATOR.createFromParcel(data) : null;
                    String pkg = data.readString();
                    int epoch = lifecycle.generation();
                    boolean connected = lifecycle.dispatch(epoch) && providerBinder != null && providerBinder.isBinderAlive()
                            && wakeProviderBinder != null && wakeProviderBinder.isBinderAlive();
                    if (connected && isDoublePowerRequest(intent, pkg)) {
                        long now = SystemClock.elapsedRealtime();
                        // Observe the exact callback before opt-in; no user payload is retained.
                        if (now - lastPowerObservation > 500) {
                            lastPowerObservation = now;
                            AgentDiagnostics.shell("DOUBLE_POWER_MATCH source=system target=oem_shortcut");
                        }
                        if (doublePower.enabled()) {
                            result = 0;
                            int token = doublePower.accept(now);
                            String target = doublePower.target();
                            AgentDiagnostics.shell("DOUBLE_POWER_BLOCK target=" + target + " accepted=" + (token >= 0));
                            if (token >= 0) launches.execute(() -> launchDoublePower(epoch, token, target, callbackStarted));
                        }
                    }
                    if (enabled && lifecycle.dispatch(epoch) && providerBinder != null && providerBinder.isBinderAlive()
                            && wakeProviderBinder != null && wakeProviderBinder.isBinderAlive() && isDockRequest(intent, pkg)) {
                        result = 0;
                        if (rule.accept(SystemClock.elapsedRealtime())) {
                            lastTrigger = SystemClock.elapsedRealtime();
                            AgentDiagnostics.shell("DESKTOP_BLOCK generation=" + epoch);
                            launches.execute(() -> { if (lifecycle.dispatch(epoch)) launchGoogle(); });
                        }
                    }
                } else if (code == 4 || code == 5) result = 0;
                else if (code == 6) result = -1;
                // Resumes are allowed; crash/ANR callbacks retain normal system handling.
                reply.writeNoException(); reply.writeInt(result); return true;
            }
        };
        controllerBinder = callback;
        controller = Proxy.newProxyInstance(ctl.getClassLoader(), new Class<?>[]{ctl}, (o,m,a) -> {
            if (m.getName().equals("asBinder")) return callback;
            throw new UnsupportedOperationException(m.getName());
        });
        activityManager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        managerInterface = Class.forName("android.app.IActivityManager");
    }

    private boolean isDockRequest(Intent intent, String pkg) {
        if (intent == null || intent.getComponent() == null) return false;
        try {
            return DesktopSearchRule.matches((int) callingUid.invoke(intent), launcherUid, pkg,
                    intent.getComponent().getClassName(), intent.getAction(), intent.getDataString());
        } catch (ReflectiveOperationException e) {
            error = "Cannot read ColorOS caller UID: " + e;
            return false;
        }
    }

    private boolean isDoublePowerRequest(Intent intent, String pkg) {
        if (intent == null || intent.getComponent() == null) return false;
        try {
            return DoublePowerRoute.matches((int) callingUid.invoke(intent), pkg,
                    intent.getComponent().getPackageName(), intent.getComponent().getClassName(), intent.getAction());
        } catch (ReflectiveOperationException e) {
            doublePowerError = "Cannot verify shortcut caller UID";
            return false;
        }
    }

    private void checkCaller() {
        int uid = Binder.getCallingUid();
        if (uid != appUid && uid != 2000) throw new SecurityException("ColorGoogle or ADB required");
    }

    private synchronized Bundle status() {
        if (controllerInstalled) {
            try {
                if (!controllerIdentity.equals(readControllerIdentity())) {
                    enabled = false;
                    controllerInstalled = false;
                    doublePower.configure(false, doublePower.target());
                    doublePowerError = "Another debugging monitor replaced the controller";
                    error = "Another debugging monitor replaced the controller; disable that monitor before enabling again";
                }
            } catch (Exception e) { error = "Cannot verify controller: " + e; }
        }
        Bundle b = new Bundle();
        b.putString("lifecycle", lifecycle.state().name());
        b.putInt("generation", lifecycle.generation());
        b.putString("reason", reason);
        b.putString("session", AgentDiagnostics.session());
        b.putInt("version", BuildConfig.VERSION_CODE);
        b.putInt("pid", android.os.Process.myPid());
        b.putBoolean("enabled", enabled);
        b.putBoolean("controllerInstalled", controllerInstalled);
        b.putBoolean("doublePowerEnabled", doublePower.enabled());
        b.putString("doublePowerTarget", doublePower.target());
        b.putString("doublePowerError", doublePowerError);
        b.putInt("doublePowerRequests", doublePowerRequests);
        b.putLong("doublePowerObservedAt", lastPowerObservation);
        b.putString("error", error);
        b.putBoolean("wakeActive", wakeActive);
        b.putString("wakeError", wakeError);
        b.putInt("redirects", redirects);
        b.putLong("lastTrigger", lastTrigger);
        b.putString("geminiLiveState", liveLaunchState);
        assistantDiagnostics.addTo(b);
        return b;
    }

    private synchronized void enable(boolean value) throws Exception {
        if (value == enabled) return;
        if (value) closeOldSearchTasks();
        ensureController(DoublePowerRoute.needsController(value, doublePower.enabled()));
        enabled = value;
    }

    private synchronized void configureDoublePower(boolean value, String target, int epoch) throws Exception {
        if (!lifecycle.dispatch(epoch)) throw new IllegalStateException("Stale agent connection");
        DoublePowerRoute.validate(target);
        if (value) {
            if (DoublePowerRoute.CAMERA.equals(target) && BuildConfig.CAMERA_BINDER_QUERIES) {
                CameraQueries queries = new CameraQueries(activityManager);
                if (queries.currentUser() != 0) throw new IllegalStateException("Only the primary user is supported");
                queries.resolve(false);
                queries.resolve(true);
                cameraQueries = queries;
            } else {
                resolvePowerTarget(target, false);
                if (DoublePowerRoute.CAMERA.equals(target)) resolvePowerTarget(target, true);
            }
            // Verify the secure state API now, before any OEM request can be blocked.
            keyguardLocked();
            closeOldWalletTasks();
        }
        if (!lifecycle.dispatch(epoch)) throw new IllegalStateException("Connection ended during configuration");
        ensureController(DoublePowerRoute.needsController(enabled, value));
        if (!lifecycle.dispatch(epoch)) throw new IllegalStateException("Connection ended during controller activation");
        doublePower.configure(value, target);
        doublePowerError = "";
        AgentDiagnostics.shell("DOUBLE_POWER_CONFIG enabled=" + value + " target=" + target);
    }

    private synchronized void ensureController(boolean value) throws Exception {
        if (value == controllerInstalled) return;
        // Never synchronously launch another activity from activityStarting: system holds its lock.
        String current = readControllerIdentity();
        if (value && !current.isEmpty()) {
            Bundle dead = callDesktopProvider("dead_controller", null);
            if (!current.equals(dead.getString("identity", "")))
                throw new IllegalStateException("Another activity monitor is already running");
            System.out.println("REPLACE_DEAD_OWN_CONTROLLER " + current);
        }
        if (!value && !AgentLifecycle.ownsController(controllerIdentity, current)) {
            controllerInstalled = false;
            error = "Controller was replaced; leaving the other monitor untouched";
            return;
        }
        if (value) {
            String latest = readControllerIdentity();
            if (!latest.isEmpty() && !latest.equals(current))
                throw new IllegalStateException("Activity monitor changed during activation");
        }
        setController.invoke(taskManager, value ? controller : null, false);
        controllerInstalled = value;
        controllerIdentity = value ? readControllerIdentity() : "";
        Bundle record = new Bundle();
        record.putBinder("controller", value ? controllerBinder : null);
        record.putString("identity", controllerIdentity);
        callDesktopProvider("remember_controller", record);
        error = "";
        AgentDiagnostics.shell("CONTROLLER enabled=" + controllerInstalled);
    }

    private IDesktopSearchAgent rpc(int epoch) {
        return new IDesktopSearchAgent.Stub() {
            private void verify() {
                checkCaller();
                if (!lifecycle.current(epoch)) throw new IllegalStateException("Stale agent connection");
            }
            @Override public Bundle getStatus() { checkCaller(); return status(); }
            @Override public Bundle setEnabled(boolean value) {
                verify();
                if (!lifecycle.dispatch(epoch)) throw new IllegalStateException("Agent is reconnecting");
                try { enable(value); } catch (Exception e) { error = "Controller change failed: " + e.getClass().getSimpleName(); }
                return status();
            }
            @Override public Bundle setDoublePower(boolean value, String target) {
                verify();
                if (!lifecycle.dispatch(epoch)) throw new IllegalStateException("Agent is reconnecting");
                try { configureDoublePower(value, target, epoch); }
                catch (Exception e) {
                    doublePowerError = "Configuration failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                    AgentDiagnostics.shell("DOUBLE_POWER_CONFIG_FAILED " + e.getClass().getSimpleName());
                }
                return status();
            }
            @Override public Bundle requestGeminiLive() {
                verify();
                Bundle out = new Bundle();
                if (!lifecycle.dispatch(epoch)) {
                    out.putString("reason", "AGENT_DISCONNECTED");
                    return out;
                }
                try {
                    if (keyguardLocked()) {
                        out.putString("reason", "SCREEN_LOCKED");
                        return out;
                    }
                    liveLaunchState = "QUEUED";
                    launches.execute(() -> {
                        if (!lifecycle.dispatch(epoch)) return;
                        try {
                            boolean launched = GeminiLiveShell.launch();
                            liveLaunchState = launched ? "LIVE_REQUESTED" : "LIVE_BUTTON_NOT_FOUND";
                            AgentDiagnostics.shell("GEMINI_LIVE state=" + liveLaunchState);
                        } catch (Exception e) {
                            liveLaunchState = "FAILED_" + e.getClass().getSimpleName();
                            AgentDiagnostics.shell("GEMINI_LIVE state=" + liveLaunchState);
                        }
                    });
                    out.putBoolean("queued", true);
                } catch (Exception e) {
                    out.putString("reason", e.getClass().getSimpleName());
                }
                return out;
            }
            @Override public void setWakeListener(IAdbWakeListener listener) {
                checkCaller();
                synchronized (DesktopSearchAgent.this) {
                    if (!lifecycle.acceptListener(epoch, listener == null)) return;
                    pendingListener = listener;
                    if (lifecycle.dispatch(epoch)) replaceWakeSession(listener);
                }
            }
            @Override public void shutdown() {
                checkCaller();
                stop("EXPLICIT_STOP");
            }
        };
    }

    private void stop(String why) {
        reason = why;
        lifecycle.stop();
        AgentDiagnostics.shell("STOP_REQUEST reason=" + why);
    }

    private void disconnected(int epoch, String why) {
        if (lifecycle.lost(epoch, SystemClock.elapsedRealtime())) {
            reason = why;
            cleanupNeeded = true;
            AgentDiagnostics.shell("CONNECTION_LOST generation=" + epoch + " reason=" + why);
        }
    }

    private Bundle attachProvider(String authority, IBinder token, int epoch,
                                  IDesktopSearchAgent rpc) throws Exception {
        Object holder = managerInterface.getMethod("getContentProviderExternal", String.class,
                int.class, IBinder.class, String.class).invoke(activityManager,
                authority, 0, token, "ColorGoogle ADB agent");
        if (holder == null) throw new IllegalStateException("Bridge unavailable");
        Object provider = holder.getClass().getField("provider").get(holder);
        IBinder binder = ((IInterface) provider).asBinder();
        IBinder.DeathRecipient death = () -> disconnected(epoch, "BRIDGE_DIED:" + authority);
        if (authority.equals(DesktopSearchBridge.AUTHORITY)) {
            providerBinder = binder; desktopProvider = provider; desktopDeath = death;
        } else { wakeProviderBinder = binder; wakeDeath = death; }
        binder.linkToDeath(death, 0);
        Bundle extras = new Bundle(); extras.putBinder("agent", rpc.asBinder());
        extras.putInt("version", BuildConfig.VERSION_CODE);
        extras.putString("session", AgentDiagnostics.session());
        extras.putInt("generation", epoch);
        AttributionSource source = new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
        Bundle result = (Bundle) Class.forName("android.content.IContentProvider").getMethod("call",
                AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                .invoke(provider, source, authority, "attach", null, extras);
        if (result == null) throw new IllegalStateException("No bridge response");
        if (result.getInt("version", -1) != BuildConfig.VERSION_CODE) throw new SecurityException("Agent version changed; reactivate with ADB");
        if (!lifecycle.current(epoch)) throw new IllegalStateException("Connection lost while attaching");
        return result;
    }

    private Bundle callDesktopProvider(String method, Bundle extras) throws Exception {
        AttributionSource source = new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
        return (Bundle) Class.forName("android.content.IContentProvider").getMethod("call",
                AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                .invoke(desktopProvider, source, DesktopSearchBridge.AUTHORITY, method, null, extras);
    }

    private void attach(int epoch) throws Exception {
        if (packageUid(SELF) != appUid) throw new SecurityException("Application UID changed");
        providerToken = new Binder(); wakeProviderToken = new Binder();
        IDesktopSearchAgent remote = rpc(epoch);
        Bundle desktop = attachProvider(DesktopSearchBridge.AUTHORITY, providerToken, epoch, remote);
        Bundle wake = attachProvider(WakeLogBridge.AUTHORITY, wakeProviderToken, epoch, remote);
        if (!wake.getBoolean("runEnabled")) { stop("MASTER_DISABLED"); return; }
        if (!lifecycle.connected(epoch)) return;
        reason = "CONNECTED";
        error = "";
        try { enable(desktop.getBoolean("enabled", false)); }
        catch (Exception e) {
            // Controller conflicts are feature errors, not a broken app connection.
            // Keep the error visible; do not repeatedly reconnect or replace a foreign monitor.
            error = "Desktop search unavailable: " + e.getClass().getSimpleName();
            AgentDiagnostics.shell("CONTROLLER_ENABLE_FAILED " + e.getClass().getSimpleName());
        }
        try { configureDoublePower(desktop.getBoolean("doublePowerRequested", false),
                desktop.getString("doublePowerTarget", DoublePowerRoute.WALLET), epoch); }
        catch (Exception e) {
            doublePowerError = "Double-power unavailable: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            AgentDiagnostics.shell("DOUBLE_POWER_ATTACH_FAILED " + e.getClass().getSimpleName());
        }
        synchronized (this) {
            if (lifecycle.dispatch(epoch)) replaceWakeSession(pendingListener);
        }
        AgentDiagnostics.shell("CONNECTED generation=" + epoch);
        assistantDiagnostics.request();
    }

    private void detach() {
        cleanupNeeded = false;
        launches.getQueue().clear();
        synchronized (this) { pendingListener = null; replaceWakeSession(null); }
        // A dead app must not stop cleanup; never clear another monitor's controller.
        try {
            String current = readControllerIdentity();
            if (AgentLifecycle.ownsController(controllerIdentity, current))
                setController.invoke(taskManager, null, false);
        } catch (Exception e) { AgentDiagnostics.shell("CONTROLLER_RELEASE_FAILED " + e.getClass().getSimpleName()); }
        enabled = false; controllerInstalled = false; controllerIdentity = "";
        doublePower.configure(false, doublePower.target());
        if (providerBinder != null && desktopDeath != null) providerBinder.unlinkToDeath(desktopDeath, 0);
        if (wakeProviderBinder != null && wakeDeath != null) wakeProviderBinder.unlinkToDeath(wakeDeath, 0);
        if (providerToken != null) {
            try { releaseProvider(DesktopSearchBridge.AUTHORITY, providerToken); }
            catch (Exception e) { AgentDiagnostics.shell("DESKTOP_RELEASE_FAILED " + e.getClass().getSimpleName()); }
        }
        if (wakeProviderToken != null) {
            try { releaseProvider(WakeLogBridge.AUTHORITY, wakeProviderToken); }
            catch (Exception e) { AgentDiagnostics.shell("WAKE_RELEASE_FAILED " + e.getClass().getSimpleName()); }
        }
        providerBinder = null; wakeProviderBinder = null; desktopProvider = null;
        providerToken = null; wakeProviderToken = null;
    }

    private void releaseProvider(String authority, IBinder token) throws Exception {
        managerInterface.getMethod("removeContentProviderExternalAsUser", String.class,
                IBinder.class, int.class).invoke(activityManager, authority, token, 0);
    }

    private synchronized void replaceWakeSession(IAdbWakeListener sink) {
        int generation = ++wakeGeneration;
        if (wakeSession != null) wakeSession.close();
        wakeSession = null;
        wakeActive = false;
        wakeError = "";
        if (sink == null) return;
        final long started = SystemClock.elapsedRealtime();
        final int epoch = lifecycle.generation();
        wakeSession = new SingleDirectLogdSession(new SingleDirectLogdSession.Listener() {
            private boolean current() { return wakeGeneration == generation && lifecycle.dispatch(epoch); }
            @Override public void onActive() {
                if (!current()) return;
                try {
                    wakeActive = true;
                    sink.onConnected();
                    AgentDiagnostics.shell("WAKE_LOGS_VERIFIED generation=" + epoch);
                } catch (RemoteException e) { onAllLanesLost("Wake listener disconnected"); }
            }
            @Override public void onLine(String line) {
                long now = SystemClock.elapsedRealtime();
                if (!current() || now - started < 700 || !TriggerClassifier.isCandidateLine(line)) return;
                assistantDiagnostics.request();
                try {
                    sink.onLine(line, now);
                    AgentDiagnostics.shell("WAKE_EVENT generation=" + epoch);
                } catch (RemoteException e) { onAllLanesLost("Wake listener disconnected"); }
            }
            @Override public void onAllLanesLost(String reason) {
                if (!current()) return;
                wakeActive = false;
                wakeError = reason;
                try { sink.onDisconnected(reason); } catch (RemoteException ignored) { }
                System.err.println("WAKE_LOGS_LOST " + reason);
            }
        });
        wakeSession.start();
    }

    private void closeOldSearchTasks() throws Exception {
        Class<?> api = Class.forName("android.app.IActivityTaskManager");
        // Close only the old ColorOS search page when opting in. Preserve its package/data
        // and other apps' tasks. A pre-existing OEM task can be resurfaced before START_ABORTED.
        java.util.List<?> tasks = (java.util.List<?>) api.getMethod("getTasks", int.class,
                boolean.class, boolean.class, int.class).invoke(taskManager, 100, false, false, 0);
        for (Object item : tasks) {
            android.app.ActivityManager.RunningTaskInfo task = (android.app.ActivityManager.RunningTaskInfo) item;
            int userId = item.getClass().getField("userId").getInt(item);
            if (userId == 0 && task.baseActivity != null
                    && DesktopSearchRule.TARGET.equals(task.baseActivity.getPackageName())
                    && DesktopSearchRule.COMPONENT.equals(task.baseActivity.getClassName())) {
                boolean removed = (boolean) api.getMethod("removeTask", int.class).invoke(taskManager, task.taskId);
                if (!removed) throw new IllegalStateException("Could not close previous system search page");
                System.out.println("CLOSED_OLD_SEARCH_TASK " + task.taskId);
            }
        }
    }

    private void closeOldWalletTasks() throws Exception { ShellCamera.closeOldShortcut(taskManager); }

    private static boolean keyguardLocked() throws Exception { return ShellCamera.locked(); }

    private static String powerAction(String target, boolean locked) {
        return DoublePowerRoute.WALLET.equals(target) ? Intent.ACTION_MAIN
                : (locked ? "android.media.action.STILL_IMAGE_CAMERA_SECURE" : "android.media.action.STILL_IMAGE_CAMERA");
    }

    private static String resolvePowerTarget(String target, boolean locked) throws Exception {
        String pkg = DoublePowerRoute.WALLET.equals(target) ? "com.google.android.apps.walletnfcrel" : "com.oplus.camera";
        java.util.ArrayList<String> args = new java.util.ArrayList<>(java.util.Arrays.asList(
                "/system/bin/cmd", "package", "resolve-activity", "--brief", "--user", "0", "-a", powerAction(target, locked), "-p", pkg));
        if (DoublePowerRoute.WALLET.equals(target)) { args.add("-c"); args.add(Intent.CATEGORY_LAUNCHER); }
        String out = BoundedDiagnosticCommand.run(3000, 8192, args.toArray(new String[0]));
        for (String line : out.split("\\n")) {
            String candidate = line.trim();
            android.content.ComponentName component = android.content.ComponentName.unflattenFromString(candidate);
            if (component != null && pkg.equals(component.getPackageName())) return candidate;
        }
        throw new IllegalStateException("No available activity in " + pkg);
    }

    private void launchDoublePower(int epoch, int token, String target, long received) {
        boolean camera = DoublePowerRoute.CAMERA.equals(target);
        CameraLaunchTiming timing = camera ? new CameraLaunchTiming(SystemClock::elapsedRealtimeNanos,
                received, cameraAttempts.incrementAndGet(), BuildConfig.CAMERA_BINDER_QUERIES ? "binder" : "shell") : null;
        String outcome = "CANCELLED";
        String timingLine;
        try {
            if (!lifecycle.dispatch(epoch) || !doublePower.current(token)) return;
            if (timing != null) timing.begin("user");
            int user;
            CameraQueries queries = cameraQueries;
            if (camera && BuildConfig.CAMERA_BINDER_QUERIES) {
                if (queries == null) throw new IllegalStateException("Camera Binder queries not initialized");
                user = queries.currentUser();
            } else user = Integer.parseInt(command("/system/bin/am", "get-current-user").trim());
            if (user != 0) throw new IllegalStateException("Only the primary user is supported");
            if (timing != null) timing.begin("keyguard");
            boolean locked = keyguardLocked();
            if (timing != null) { timing.locked(locked); timing.begin("resolve"); }
            String component = camera && BuildConfig.CAMERA_BINDER_QUERIES
                    ? queries.resolve(locked) : resolvePowerTarget(target, locked);
            if (timing != null) timing.end();
            java.util.ArrayList<String> args = new java.util.ArrayList<>(java.util.Arrays.asList(
                    "/system/bin/am", "start", "-W", "--user", "0", "-a", powerAction(target, locked), "-n", component));
            if (DoublePowerRoute.WALLET.equals(target)) { args.add("-c"); args.add(Intent.CATEGORY_LAUNCHER); }
            if (!lifecycle.dispatch(epoch) || !doublePower.current(token)) return;
            if (timing != null) timing.begin("recheck");
            if (keyguardLocked() != locked) throw new IllegalStateException("Keyguard state changed during launch preparation");
            if (timing != null) timing.begin("launch");
            String out = BoundedDiagnosticCommand.run(8000, 16384, args.toArray(new String[0]));
            if (timing != null) { timing.end(); timing.commandResult(out); }
            if (!out.contains("Status: ok")) throw new IllegalStateException("Activity request was not accepted");
            doublePowerRequests++;
            doublePowerError = "";
            outcome = "ACCEPTED";
            // Finish timing before persistent logging: log I/O is not launch/display time.
            timingLine = timing == null ? null : timing.finish(outcome);
            if (timingLine != null) AgentDiagnostics.shell(timingLine);
            AgentDiagnostics.shell("DOUBLE_POWER_REQUEST_ACCEPTED target=" + target + " locked=" + locked);
        } catch (Exception e) {
            outcome = "FAILED";
            if (timing != null) AgentDiagnostics.shell(timing.finish(outcome));
            doublePowerError = "Launch failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            AgentDiagnostics.shell("DOUBLE_POWER_REQUEST_FAILED target=" + target + " reason=" + e.getClass().getSimpleName());
        } finally {
            if (timing != null && "CANCELLED".equals(outcome)) AgentDiagnostics.shell(timing.finish(outcome));
        }
    }

    private static String readControllerIdentity() throws Exception { return ControllerOwnership.read(); }

    private void launchGoogle() {
        if (!enabled || !lifecycle.dispatch(lifecycle.generation())) return;
        try {
            String output = command("/system/bin/am", "start", "-W", "--user", "0", "-a",
                    "android.search.action.GLOBAL_SEARCH", "-p", "com.google.android.googlequicksearchbox");
            if (!output.contains("Status: ok")) throw new IllegalStateException(output);
            redirects++;
            error = "";
            AgentDiagnostics.shell("GOOGLE_REQUEST count=" + redirects);
        } catch (Exception e) {
            error = "Google launch failed: " + e;
            System.err.println(error);
        }
    }

    private static String command(String... args) throws Exception {
        java.lang.Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!p.waitFor(8, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("Command timed out"); }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input = p.getInputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
        }
        String out = bytes.toString(StandardCharsets.UTF_8.name());
        if (p.exitValue() != 0) throw new IllegalStateException(out);
        return out;
    }

    private static int packageUid(String pkg) throws Exception {
        String output = command("/system/bin/cmd", "package", "list", "packages", "-U", "--user", "0", pkg);
        for (String line : output.split("\\n")) {
            String prefix = "package:" + pkg + " uid:";
            if (line.startsWith(prefix)) return Integer.parseInt(line.substring(prefix.length()).trim());
        }
        throw new IllegalStateException("Package missing: " + pkg);
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            runAgent();
        } catch (Throwable failure) {
            AgentDiagnostics.shell("FATAL exception=" + failure.getClass().getSimpleName());
            failure.printStackTrace();
            exitCode = 1;
        }
        // app_process Binder threads must not retain the file lock after main fails.
        System.exit(exitCode);
    }

    private static void runAgent() throws Exception {
        if (android.os.Process.myUid() != 2000) throw new SecurityException("Start using ADB");
        android.os.Looper.prepareMainLooper();
        try (SharedAgentLease shared = new SharedAgentLease("colorcamera-agent");
             RandomAccessFile file = new RandomAccessFile("/data/local/tmp/colorgoogle-search.lock", "rw");
             FileLock lock = file.getChannel().tryLock()) {
            if (lock == null) throw new IllegalStateException("Agent already running");
            AgentDiagnostics.shell("START group=" + AgentDiagnostics.group() + " adbdPid=" + command("/system/bin/pidof", "adbd").trim());
            DesktopSearchAgent agent = new DesktopSearchAgent();
            try {
                while (agent.lifecycle.state() != AgentLifecycle.State.STOPPING) {
                    if (agent.cleanupNeeded) agent.detach();
                    int epoch = agent.lifecycle.begin(SystemClock.elapsedRealtime());
                    if (epoch >= 0) {
                        try { agent.attach(epoch); }
                        catch (Exception e) {
                            Throwable cause = e;
                            while (cause.getCause() != null) cause = cause.getCause();
                            if (cause instanceof SecurityException) {
                                agent.error = "Security or version validation failed; reactivate with ADB";
                                agent.stop("VALIDATION_FAILED");
                            } else {
                                agent.disconnected(epoch, "ATTACH_FAILED:" + cause.getClass().getSimpleName());
                            }
                        }
                    }
                    Thread.sleep(1000);
                }
            } finally {
                agent.lifecycle.stop();
                agent.detach();
                agent.launches.shutdownNow();
                agent.assistantDiagnostics.close();
                AgentDiagnostics.shell("EXIT reason=" + agent.reason);
            }
        }
    }
}
