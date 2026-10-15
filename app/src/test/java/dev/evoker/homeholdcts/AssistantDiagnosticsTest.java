// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import org.junit.Test;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import static org.junit.Assert.*;

public class AssistantDiagnosticsTest {
    private static final String COMPONENT = "mComponent=com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService\n";
    private static final String OLD_SESSION = "Active session:\n mShown=true\n mBound=true\n mService=android.service.voice.IVoiceInteractionSessionService$Stub$Proxy@abc\n mSession=android.service.voice.IVoiceInteractionSession$Stub$Proxy@abc\n";

    @Test public void staleSessionCannotHideMissingMainService() {
        AssistantSnapshot snapshot = AssistantSnapshot.parse(COMPONENT + "mBound=true mService=null\n" + OLD_SESSION, 10);
        assertEquals("DISCONNECTED", snapshot.main);
        assertEquals("CONNECTED", snapshot.session);
        assertEquals("TRUE", snapshot.shown);
        assertTrue(snapshot.event().contains("ui=UNCONFIRMED"));
    }
    @Test public void mainAndSessionRemainSeparate() {
        AssistantSnapshot snapshot = AssistantSnapshot.parse(COMPONENT
                + "mBound=true mService=android.service.voice.IVoiceInteractionService$Stub$Proxy@123\n"
                + "Active session:\n mBound=true\n mService=null\n mSession=null\n mShown=false", 10);
        assertEquals("CONNECTED", snapshot.main);
        assertEquals("DISCONNECTED", snapshot.session);
        assertEquals("FALSE", snapshot.shown);
    }
    @Test public void noDumpOrUnrecognizedServiceIsUnknown() {
        assertEquals("UNKNOWN", AssistantSnapshot.parse("permission denied", 10).main);
        assertEquals("UNKNOWN", AssistantSnapshot.parse(COMPONENT + OLD_SESSION, 10).main);
        assertEquals("UNKNOWN", AssistantSnapshot.parse(COMPONENT + "mBound=true mService=unexpected", 10).main);
        assertEquals("UNKNOWN", AssistantSnapshot.parse("mComponent=another.app/.Service\nmBound=true mService=null", 10).main);
    }
    @Test public void rawArgumentsNeverEnterSnapshot() {
        AssistantSnapshot snapshot = AssistantSnapshot.parse(COMPONENT + "mBound=true mService=null\n"
                + OLD_SESSION + " mShowArgs=SECRET_VOICE_SEARCH_PAIRING\n", 10);
        assertFalse(snapshot.event().contains("SECRET"));
    }
    @Test public void timedOutDumpCannotReportPartialHealthyState() {
        AssistantSnapshot snapshot = AssistantSnapshot.parse(COMPONENT
                + "mBound=true mService=android.service.voice.IVoiceInteractionService$Stub$Proxy@123\n"
                + "*** SERVICE DUMP TIMEOUT EXPIRED ***", 10);
        assertEquals("UNKNOWN", snapshot.main);
        assertEquals("INCOMPLETE_DUMP", snapshot.reason);
    }
    @Test public void oldSampleIsNotReportedAsCurrent() {
        AssistantSnapshot s = new AssistantSnapshot("CONNECTED", "CONNECTED", "TRUE", "SAMPLED", 100);
        assertEquals("CONNECTED", s.effectiveMain(30100));
        assertEquals("UNKNOWN", s.effectiveMain(30101));
        assertEquals("UNKNOWN", s.effectiveMain(99));
    }
    @Test public void repeatedMarkersCannotQueueOrExtendSampling() {
        DiagnosticSampleGate gate = new DiagnosticSampleGate();
        assertTrue(gate.begin(0));
        assertFalse(gate.begin(500));
        assertFalse(gate.begin(4000));
        gate.finish();
        assertTrue(gate.begin(4001));
        gate.finish();
        assertFalse(gate.begin(4100));
        gate.close();
        assertFalse(gate.begin(9000));
    }
    @Test public void acceptedRequestDoesNotClaimVisibleUiInEitherLanguage() {
        String en = DiagnosticSummary.render(false, "CONNECTED", 1, "ACCEPTED", "ASSISTANT", "SAMPLED");
        String zh = DiagnosticSummary.render(true, "CONNECTED", 1, "ACCEPTED", "ASSISTANT", "SAMPLED");
        assertTrue(en.contains("Request accepted; UI appearance unverified"));
        assertTrue(zh.contains("请求已接受；未确认界面是否出现"));
        assertFalse(en.toLowerCase().contains("success"));
        assertFalse(zh.contains("唤醒成功"));
    }
    @Test public void commandTimeoutIsBounded() throws Exception {
        long start = System.nanoTime();
        try { BoundedDiagnosticCommand.run(100, 1024, "/bin/sh", "-c", "exec sleep 5"); fail(); }
        catch (TimeoutException expected) { }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000);
    }
    @Test public void commandRejectsExcessOutputAndNonzeroExit() throws Exception {
        try { BoundedDiagnosticCommand.run(2000, 8, "/bin/echo", "123456789"); fail(); }
        catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof IOException); }
        try { BoundedDiagnosticCommand.run(2000, 1024, "/bin/sh", "-c", "exit 7"); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("COMMAND_EXIT_7")); }
    }
    @Test public void drainsOutputLargerThanPipeWithoutDeadlock() throws Exception {
        String output = BoundedDiagnosticCommand.run(2000, 65536, "/bin/sh", "-c", "i=0; while [ $i -lt 9000 ]; do echo abc; i=$((i+1)); done");
        assertEquals(36000, output.length());
    }
}
