// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraLaunchOperationTest {
    private static class Fake implements CameraLaunchOperation.Host {
        int user, starts, checks, queries;
        boolean locked, changed, unavailable, lockUnknown;
        int cancelAt = Integer.MAX_VALUE;
        String output = "Status: ok\nTotalTime: 123";
        boolean secureStarted;
        public boolean current() { return ++checks < cancelAt; }
        public int user() { return user; }
        public boolean locked() { if (lockUnknown) throw new IllegalStateException("Unknown lock"); return changed && queries > 0 ? !locked : locked; }
        public String resolve(boolean secure) { queries++; if (unavailable) throw new IllegalStateException("Unavailable camera"); return secure ? "secure" : "normal"; }
        public String start(boolean secure, String component) { starts++; secureStarted = secure; assertEquals(secure ? "secure" : "normal", component); return output; }
    }
    private CameraLaunchTiming timing() { return new CameraLaunchTiming(System::nanoTime, System.nanoTime(), 1, "binder"); }
    @Test public void lockedUsesOnlySecureEntry() throws Exception { Fake f=new Fake(); f.locked=true; assertEquals("ACCEPTED",CameraLaunchOperation.run(f,timing())); assertTrue(f.secureStarted); assertEquals(1,f.starts); }
    @Test public void unlockedUsesNormalEntry() throws Exception { Fake f=new Fake(); assertEquals("ACCEPTED",CameraLaunchOperation.run(f,timing())); assertFalse(f.secureStarted); }
    @Test public void unknownLockNeverLaunches() { Fake f=new Fake(); f.lockUnknown=true; assertThrows(IllegalStateException.class,()->CameraLaunchOperation.run(f,timing())); assertEquals(0,f.starts); }
    @Test public void lockChangeNeverLaunchesEitherEntry() { Fake f=new Fake(); f.changed=true; assertThrows(IllegalStateException.class,()->CameraLaunchOperation.run(f,timing())); assertEquals(0,f.starts); }
    @Test public void otherUserCannotUsePrimaryCamera() { Fake f=new Fake(); f.user=10; assertThrows(IllegalStateException.class,()->CameraLaunchOperation.run(f,timing())); assertEquals(0,f.queries); assertEquals(0,f.starts); }
    @Test public void missingCameraIsNotAnAcceptedRequest() { Fake f=new Fake(); f.unavailable=true; assertThrows(IllegalStateException.class,()->CameraLaunchOperation.run(f,timing())); assertEquals(0,f.starts); }
    @Test public void stopOrDisconnectCancelsAtEachPreparationBoundary() throws Exception {
        for(int i=1;i<=3;i++) { Fake f=new Fake(); f.cancelAt=i; assertEquals("CANCELLED",CameraLaunchOperation.run(f,timing())); assertEquals(0,f.starts); }
    }
    @Test public void commandFailureIsNotReportedAsAccepted() { Fake f=new Fake(); f.output="Error: permission denied"; assertThrows(IllegalStateException.class,()->CameraLaunchOperation.run(f,timing())); }
    @Test public void commandAcceptanceDoesNotInventDisplayTiming() throws Exception { Fake f=new Fake(); f.output="Status: ok"; CameraLaunchTiming t=timing(); assertEquals("ACCEPTED",CameraLaunchOperation.run(f,t)); assertTrue(t.finish("ACCEPTED").contains("androidTotalMs=NA")); }
}
