// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import org.junit.Test;
import static org.junit.Assert.*;

public class PendingVoiceLiveUnlockTest {
    @Test public void onlyOneLaunchAfterValidUnlock() {
        PendingVoiceLiveUnlock pending = new PendingVoiceLiveUnlock();
        assertFalse(pending.consume(1000));
        pending.arm(1000);
        assertTrue(pending.consume(1500));
        assertFalse(pending.consume(1600));
    }

    @Test public void expiredOrClockReversedRequestIsDiscarded() {
        PendingVoiceLiveUnlock pending = new PendingVoiceLiveUnlock();
        pending.arm(0);
        assertFalse(pending.consume(45_001));
        pending.arm(10_000);
        assertFalse(pending.consume(9_999));
    }

    @Test public void cancellingOnDisablePreventsDelayedLaunch() {
        PendingVoiceLiveUnlock pending = new PendingVoiceLiveUnlock();
        pending.arm(1000);
        pending.cancel();
        assertFalse(pending.consume(2000));
    }

    @Test public void latestRequestReplacesOlderOne() {
        PendingVoiceLiveUnlock pending = new PendingVoiceLiveUnlock();
        pending.arm(1000);
        pending.arm(50_000);
        assertTrue(pending.consume(50_500));
    }
}
