// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Shell-only Binder queries. No target cache, alternate command path or activity launch. */
final class CameraQueries {
    private final Object activityManager;
    private final Object packageManager;
    private final Method currentUser;
    private final Method resolveIntent;

    CameraQueries(Object activityManager) throws ReflectiveOperationException {
        this.activityManager = activityManager;
        currentUser = Class.forName("android.app.IActivityManager").getMethod("getCurrentUser");
        packageManager = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
        resolveIntent = Class.forName("android.content.pm.IPackageManager").getMethod(
                "resolveIntent", Intent.class, String.class, long.class, int.class);
    }
    int currentUser() throws ReflectiveOperationException {
        Object user = invoke(currentUser, activityManager);
        if (user == null) throw new IllegalStateException("Current user unavailable");
        return user.getClass().getField("id").getInt(user);
    }
    String resolve(boolean locked) throws ReflectiveOperationException {
        Intent intent = new Intent(locked ? "android.media.action.STILL_IMAGE_CAMERA_SECURE"
                : "android.media.action.STILL_IMAGE_CAMERA").setPackage("com.oplus.camera");
        ResolveInfo resolved = (ResolveInfo) invoke(resolveIntent, packageManager, intent, null, 0L, 0);
        ActivityInfo info = resolved == null ? null : resolved.activityInfo;
        boolean valid = info != null && info.applicationInfo != null
                && validTarget(info.packageName, info.name, info.exported, info.enabled, info.applicationInfo.enabled);
        if (!valid) throw new IllegalStateException("No enabled exported OPPO camera entry");
        return new ComponentName(info.packageName, info.name).flattenToShortString();
    }
    static Object invoke(Method method, Object receiver, Object... args) throws ReflectiveOperationException {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Camera Binder query failed: " + cause.getClass().getSimpleName(), cause);
        }
    }
    static boolean validTarget(String pkg, String name, boolean exported, boolean enabled, boolean appEnabled) {
        return "com.oplus.camera".equals(pkg) && name != null && !name.isEmpty() && exported && enabled && appEnabled;
    }
}
