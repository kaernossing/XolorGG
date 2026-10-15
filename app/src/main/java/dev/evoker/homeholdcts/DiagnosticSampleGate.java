// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/** At most one leading/follow-up pair in flight; repeated markers never extend it. */
final class DiagnosticSampleGate {
    private boolean active, closed;
    private long last = -1;
    synchronized boolean begin(long now) {
        if (closed || active || (last >= 0 && now - last < 2500)) return false;
        active = true; last = now; return true;
    }
    synchronized void finish() { active = false; }
    synchronized void close() { closed = true; }
}
