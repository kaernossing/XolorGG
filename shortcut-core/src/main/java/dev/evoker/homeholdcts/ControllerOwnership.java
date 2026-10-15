// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/** Fail closed if the OEM activity dump cannot establish the current owner. */
final class ControllerOwnership {
    static String read() throws Exception {
        return parse(BoundedDiagnosticCommand.run(3000, 8192, "/system/bin/sh", "-c",
                "dumpsys activity processes | sed -n '/mController=/p; /mGoingToSleepWakeLock=/p'"));
    }
    static String parse(String dump) {
        if (!dump.contains("mGoingToSleepWakeLock=")) throw new IllegalStateException("Cannot inspect activity monitor ownership");
        String found = "";
        for (String line : dump.split("\\n")) {
            if (line.trim().startsWith("mController=")) {
                if (!found.isEmpty()) throw new IllegalStateException("Ambiguous activity monitor ownership");
                found = line.trim();
            }
        }
        // Android normally omits the field when null. Do not infer unknown formats are free.
        return found;
    }
}
