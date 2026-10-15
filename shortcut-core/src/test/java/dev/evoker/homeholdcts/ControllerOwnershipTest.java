// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import org.junit.Test;
import static org.junit.Assert.*;
public class ControllerOwnershipTest {
    @Test public void absentFieldRequiresCompleteDumpMarker() { assertEquals("",ControllerOwnership.parse("mGoingToSleepWakeLock=WakeLock")); assertThrows(IllegalStateException.class,()->ControllerOwnership.parse("Permission Denial")); }
    @Test public void foreignOwnerIsNeverInterpretedAsVacant() { assertEquals("mController=other",ControllerOwnership.parse("mController=other\nmGoingToSleepWakeLock=WakeLock")); }
    @Test public void ambiguousOwnerFailsClosed() { assertThrows(IllegalStateException.class,()->ControllerOwnership.parse("mController=one\nmController=two\nmGoingToSleepWakeLock=WakeLock")); }
    @Test public void processDetectionUsesWholeNameAndValidatesHeader() { assertTrue(SharedAgentLease.containsProcess("NAME\ncolorcamera-agent\n", "colorcamera-agent")); assertFalse(SharedAgentLease.containsProcess("NAME\ncolorcamera-agent-other\n", "colorcamera-agent")); assertThrows(IllegalStateException.class,()->SharedAgentLease.containsProcess("error", "colorcamera-agent")); }
}
