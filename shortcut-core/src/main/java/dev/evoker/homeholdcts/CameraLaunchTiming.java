// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Structural timing only: never retains command output, camera content or intents. */
final class CameraLaunchTiming {
    private final LongSupplier clock;
    private final long received;
    private final long worker;
    private final int ordinal;
    private final String mode;
    private final Map<String, Long> stages = new LinkedHashMap<>();
    private String active;
    private long stageStart;
    private long launchStart = -1;
    private String locked = "UNKNOWN";
    private String launchState = "UNKNOWN";
    private String androidThis = "NA", androidTotal = "NA", androidWait = "NA";

    CameraLaunchTiming(LongSupplier clock, long received, int ordinal, String mode) {
        this.clock = clock; this.received = received; this.worker = clock.getAsLong();
        this.ordinal = ordinal; this.mode = mode;
    }
    void begin(String stage) {
        end(); active = stage; stageStart = clock.getAsLong();
        if ("launch".equals(stage)) launchStart = stageStart;
    }
    void end() {
        if (active == null) return;
        stages.put(active, Math.max(0, clock.getAsLong() - stageStart));
        active = null;
    }
    void locked(boolean value) { locked = Boolean.toString(value); }
    void commandResult(String output) {
        androidThis = number(output, "ThisTime");
        androidTotal = number(output, "TotalTime");
        androidWait = number(output, "WaitTime");
        Matcher state = Pattern.compile("(?m)^\\s*LaunchState:\\s*(COLD|WARM|HOT)\\s*$").matcher(output);
        if (state.find()) launchState = state.group(1);
    }
    static String number(String output, String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + ":\\s*(\\d+)\\s*$").matcher(output);
        if (!m.find()) return "NA";
        try { return Long.toString(Long.parseLong(m.group(1))); }
        catch (NumberFormatException e) { return "NA"; }
    }
    String finish(String outcome) {
        end(); long finished = clock.getAsLong();
        StringBuilder s = new StringBuilder("CAMERA_TIMING mode=").append(mode)
                .append(" ordinal=").append(ordinal).append(" locked=").append(locked)
                .append(" outcome=").append(outcome)
                .append(" receivedElapsedMs=").append(received / 1_000_000L)
                .append(" queueUs=").append(us(worker - received))
                .append(" prepareUs=").append(launchStart < 0 ? "NA" : us(launchStart - worker));
        for (String stage : new String[]{"user", "keyguard", "resolve", "recheck", "launch"}) {
            Long value = stages.get(stage);
            s.append(' ').append(stage).append("Us=").append(value == null ? "NA" : us(value));
        }
        return s.append(" totalUs=").append(us(finished - received))
                .append(" androidThisMs=").append(androidThis)
                .append(" androidTotalMs=").append(androidTotal)
                .append(" androidWaitMs=").append(androidWait)
                .append(" launchState=").append(launchState).toString();
    }
    private static long us(long ns) { return Math.max(0, ns) / 1000L; }
}
