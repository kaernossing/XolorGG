// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/** Structural fields only. Never retains the raw service dump or invocation arguments. */
final class AssistantSnapshot {
    final String main, session, shown, reason;
    final long sampledAt;

    AssistantSnapshot(String main, String session, String shown, String reason, long sampledAt) {
        this.main = main; this.session = session; this.shown = shown;
        this.reason = reason; this.sampledAt = sampledAt;
    }

    static AssistantSnapshot unknown(String reason, long now) {
        return new AssistantSnapshot("UNKNOWN", "UNKNOWN", "UNKNOWN", reason, now);
    }

    static AssistantSnapshot parse(String dump, long now) {
        if (dump.contains("DUMP TIMEOUT") || dump.contains("DUMP FAILED"))
            return unknown("INCOMPLETE_DUMP", now);
        boolean inSession = false, google = false;
        String main = "UNKNOWN", session = "UNKNOWN", shown = "UNKNOWN";
        for (String raw : dump.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("mComponent=")) {
                google = line.substring("mComponent=".length()).startsWith(
                        "com.google.android.googlequicksearchbox/");
            }
            if (line.equals("Active session:")) { inSession = true; continue; }
            if (!inSession && line.startsWith("mBound=") && line.contains(" mService=")) {
                String value = line.substring(line.indexOf(" mService=") + 10).trim();
                if (value.equals("null")) main = "DISCONNECTED";
                else if (value.startsWith("android.service.voice.IVoiceInteractionService$")) main = "CONNECTED";
            }
            if (inSession && line.startsWith("mSession=")) {
                String value = line.substring(9);
                if (value.equals("null")) session = "DISCONNECTED";
                else if (value.startsWith("android.service.voice.IVoiceInteractionSession$")) session = "CONNECTED";
            }
            if (inSession && line.equals("mShown=true")) shown = "TRUE";
            if (inSession && line.equals("mShown=false")) shown = "FALSE";
        }
        if (!google) return unknown("GOOGLE_COMPONENT_NOT_CONFIRMED", now);
        return new AssistantSnapshot(main, session, shown,
                main.equals("UNKNOWN") ? "UNRECOGNIZED_DUMP" : "SAMPLED", now);
    }

    String effectiveMain(long now) {
        return sampledAt < 0 || now < sampledAt || now - sampledAt > 30000 ? "UNKNOWN" : main;
    }

    String event() {
        return "main=" + main + " session=" + session + " shown=" + shown
                + " reason=" + reason + " ui=UNCONFIRMED";
    }
}
