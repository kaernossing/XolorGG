// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentLifecycleTest {
    @Test public void stoppedListenerCleanupIsIdempotentAndCannotRegister() {
        AgentLifecycle life = new AgentLifecycle(); int epoch = life.begin(0);
        life.connected(epoch); life.stop();
        assertFalse(life.acceptListener(epoch, true));
        assertFalse(life.acceptListener(epoch, true));
        assertThrows(IllegalStateException.class, () -> life.acceptListener(epoch, false));
        assertEquals(-1, life.begin(99999));
    }
    @Test public void oldCleanupCannotClearReconnectedListener() {
        AgentLifecycle life = new AgentLifecycle(); int old = life.begin(0);
        life.connected(old); life.lost(old, 0);
        assertFalse(life.acceptListener(old, true));
        int next = life.begin(1000); life.connected(next);
        assertTrue(life.acceptListener(next, false));
        assertFalse(life.acceptListener(old, true));
        assertTrue(life.dispatch(next));
        assertThrows(IllegalStateException.class, () -> life.acceptListener(old, false));
        assertTrue(life.acceptListener(next, true));
    }
    @Test public void cleanupNeverClearsForeignOrUnknownController() {
        assertTrue(AgentLifecycle.ownsController("ours", "ours"));
        assertFalse(AgentLifecycle.ownsController("ours", "other"));
        assertFalse(AgentLifecycle.ownsController("", ""));
        assertFalse(AgentLifecycle.ownsController(null, "other"));
    }
    @Test public void deathInvalidatesQueuedWorkBeforeCleanup() {
        AgentLifecycle life = new AgentLifecycle(); int a = life.begin(0);
        assertTrue(life.connected(a)); assertTrue(life.dispatch(a));
        assertTrue(life.lost(a, 10)); assertFalse(life.dispatch(a));
        assertFalse(life.lost(a, 500)); assertEquals(-1, life.begin(1009));
        int b = life.begin(1010); assertTrue(life.connected(b));
        assertFalse(life.dispatch(a)); assertFalse(life.lost(a, 2000));
        assertTrue(life.dispatch(b));
    }
    @Test public void partialAttachmentCannotBecomeReadyAfterDeath() {
        AgentLifecycle life = new AgentLifecycle(); int epoch = life.begin(0);
        assertFalse(life.dispatch(epoch)); life.lost(epoch, 0);
        assertFalse(life.connected(epoch));
    }
    @Test public void stopWinsAgainstReconnectAndLateCallbacks() {
        AgentLifecycle life = new AgentLifecycle(); int epoch = life.begin(0);
        life.stop(); assertFalse(life.connected(epoch)); assertFalse(life.lost(epoch, 0));
        assertEquals(-1, life.begin(999999)); assertFalse(life.dispatch(epoch));
    }
    @Test public void backoffCapsAtThirtySecondsWithoutStartingParallelAttempts() {
        AgentLifecycle life = new AgentLifecycle(); long now = 0;
        long[] delays = {1000,2000,4000,8000,16000,30000,30000};
        for (long delay : delays) {
            int epoch = life.begin(now); assertTrue(epoch > 0);
            assertEquals(-1, life.begin(now)); life.lost(epoch, now);
            assertEquals(-1, life.begin(now + delay - 1)); now += delay;
        }
        int epoch = life.begin(now); assertTrue(life.connected(epoch));
        life.lost(epoch, now); assertTrue(life.begin(now + 1000) > epoch);
    }
    @Test public void explicitStopWhileDisconnectedNeverRetries() {
        AgentLifecycle life = new AgentLifecycle(); int epoch = life.begin(0);
        life.connected(epoch); life.lost(epoch, 0); life.stop();
        assertEquals(-1, life.begin(30000));
    }
    @Test public void disconnectOfOneBridgeInvalidatesBothBridges() {
        AgentLifecycle life = new AgentLifecycle(); int epoch = life.begin(0);
        life.connected(epoch); assertTrue(life.lost(epoch, 0));
        assertFalse(life.current(epoch)); assertFalse(life.lost(epoch, 20));
    }
}
