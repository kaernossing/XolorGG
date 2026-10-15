// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/** Camera-only preparation, with injectable platform operations for safety regression tests. */
final class CameraLaunchOperation {
    interface Host {
        boolean current();
        int user() throws Exception;
        boolean locked() throws Exception;
        String resolve(boolean locked) throws Exception;
        String start(boolean locked, String component) throws Exception;
    }
    static String run(Host host, CameraLaunchTiming timing) throws Exception {
        if (!host.current()) return "CANCELLED";
        timing.begin("user");
        if (host.user() != 0) throw new IllegalStateException("Only the primary user is supported");
        timing.begin("keyguard"); boolean locked = host.locked(); timing.locked(locked);
        timing.begin("resolve"); String component = host.resolve(locked);
        if (component == null || component.isEmpty()) throw new IllegalStateException("Camera entry unavailable");
        if (!host.current()) return "CANCELLED";
        timing.begin("recheck");
        if (host.locked() != locked) throw new IllegalStateException("Lock state changed during preparation");
        if (!host.current()) return "CANCELLED";
        timing.begin("launch"); String output = host.start(locked, component);
        timing.end(); timing.commandResult(output);
        if (!output.contains("Status: ok")) throw new IllegalStateException("Camera request rejected");
        return "ACCEPTED";
    }
}
