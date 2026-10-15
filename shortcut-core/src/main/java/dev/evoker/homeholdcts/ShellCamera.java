// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.os.IBinder;
import android.content.Intent;

final class ShellCamera {
    static boolean locked() throws Exception {
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "window");
        if (service == null) throw new IllegalStateException("Window service unavailable");
        Object window = Class.forName("android.view.IWindowManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, service);
        return (boolean) Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(window);
    }
    static String action(boolean locked) {
        return locked ? "android.media.action.STILL_IMAGE_CAMERA_SECURE" : "android.media.action.STILL_IMAGE_CAMERA";
    }
    static void closeOldShortcut(Object taskManager) throws Exception {
        Class<?> api = Class.forName("android.app.IActivityTaskManager");
        java.util.List<?> tasks = (java.util.List<?>) api.getMethod("getTasks", int.class,
                boolean.class, boolean.class, int.class).invoke(taskManager, 100, false, false, 0);
        for (Object item : tasks) {
            android.app.ActivityManager.RunningTaskInfo task = (android.app.ActivityManager.RunningTaskInfo) item;
            Intent base = task.baseIntent;
            if (item.getClass().getField("userId").getInt(item) == 0 && base != null
                    && base.getComponent() != null
                    && DoublePowerRoute.OEM_ACTION.equals(base.getAction())
                    && DoublePowerRoute.OEM_PACKAGE.equals(base.getComponent().getPackageName())
                    && DoublePowerRoute.OEM_COMPONENT.equals(base.getComponent().getClassName())) {
                if (!(boolean) api.getMethod("removeTask", int.class).invoke(taskManager, task.taskId))
                    throw new IllegalStateException("Cannot close previous wallet shortcut task");
            }
        }
    }
}
