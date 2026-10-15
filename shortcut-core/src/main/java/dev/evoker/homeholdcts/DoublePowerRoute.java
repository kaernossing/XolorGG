// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/** Exact OEM shortcut route and independent, cancellable request generation. */
final class DoublePowerRoute {
    static final String PREF_ENABLED = "double_power_enabled";
    static final String PREF_TARGET = "double_power_target";
    static final String WALLET = "wallet", CAMERA = "camera";
    static final String OEM_PACKAGE = "com.heytap.tas";
    static final String OEM_COMPONENT = "com.nearme.wallet.nfc.ui.NfcConsumeActivity";
    static final String OEM_ACTION = "finshell.wallet.intent.action.OPEN";
    private boolean enabled;
    private String target = WALLET;
    private int generation;
    private long lastAccepted = Long.MIN_VALUE;

    static boolean matches(int uid, String pkg, String componentPackage, String component, String action) {
        return uid == 1000 && OEM_PACKAGE.equals(pkg) && OEM_PACKAGE.equals(componentPackage)
                && OEM_COMPONENT.equals(component) && OEM_ACTION.equals(action);
    }
    static void validate(String target) {
        if (!WALLET.equals(target) && !CAMERA.equals(target)) throw new IllegalArgumentException("Unknown double-power target");
    }
    synchronized void configure(boolean next, String destination) {
        validate(destination);
        if (next != enabled || !destination.equals(target)) {
            generation++;
            enabled = next;
            target = destination;
        }
    }
    synchronized boolean enabled() { return enabled; }
    synchronized String target() { return target; }
    synchronized int accept(long now) {
        if (!enabled || (lastAccepted != Long.MIN_VALUE && now - lastAccepted < 800)) return -1;
        lastAccepted = now;
        return generation;
    }
    synchronized boolean current(int token) { return enabled && generation == token; }
    static boolean needsController(boolean desktop, boolean power) { return desktop || power; }
}
