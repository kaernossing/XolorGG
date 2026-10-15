// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

final class DesktopSearchActivation {
    static final String START_SCRIPT = BuildConfig.AGENT_START_SCRIPT;
    static final String SCRIPT = START_SCRIPT;
    static String command() {
        return "adb -s SERIAL shell '" + SCRIPT.replace("'", "'\\''") + "'";
    }
    private DesktopSearchActivation() {}
}
