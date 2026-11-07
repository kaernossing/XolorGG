// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/**
 * One-shot, short-lived XiaoBu voice request recorded while the device is
 * locked. Not persisted: no launch after a watcher restart or reboot.
 */
final class PendingVoiceLiveUnlock {
    static final long MAX_WAIT_MS = 45_000L;
    private long armedAt = -1L;

    void arm(long now) {
        armedAt = now;
    }

    boolean consume(long now) {
        long start = armedAt;
        armedAt = -1L;
        return start >= 0L && now >= start && now - start <= MAX_WAIT_MS;
    }

    boolean isPending(long now) {
        if (armedAt < 0L) return false;
        if (now < armedAt || now - armedAt > MAX_WAIT_MS) {
            armedAt = -1L;
            return false;
        }
        return true;
    }

    void cancel() {
        armedAt = -1L;
    }

    boolean wasCancelled() {
        return armedAt < 0L;
    }
}
