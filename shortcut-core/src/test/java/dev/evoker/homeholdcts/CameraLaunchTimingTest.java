// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
public class CameraLaunchTimingTest {
 @Test public void measuresQueueStagesAndTotalWithoutCountingLogging() {
  AtomicLong clock=new AtomicLong(2_000_000);
  CameraLaunchTiming t=new CameraLaunchTiming(clock::get,0,1,"shell");
  t.begin("user");clock.addAndGet(3_000_000);t.begin("keyguard");
  clock.addAndGet(1_000_000);t.locked(true);t.begin("resolve");
  clock.addAndGet(4_000_000);t.end();t.begin("recheck");
  clock.addAndGet(1_000_000);t.begin("launch");clock.addAndGet(20_000_000);t.end();
  t.commandResult("Status: ok\nLaunchState: WARM\nTotalTime: 12\nWaitTime: 17\n");
  String out=t.finish("ACCEPTED");
  assertTrue(out.contains("queueUs=2000"));assertTrue(out.contains("prepareUs=9000"));
  assertTrue(out.contains("userUs=3000"));assertTrue(out.contains("resolveUs=4000"));
  assertTrue(out.contains("launchUs=20000"));assertTrue(out.contains("totalUs=31000"));
  assertTrue(out.contains("androidTotalMs=12"));assertTrue(out.contains("androidThisMs=NA"));
  assertTrue(out.contains("launchState=WARM"));assertTrue(out.contains("locked=true"));
 }
 @Test public void absentAndMalformedValuesRemainUnknownRatherThanZero() {
  for(String v:new String[]{"", "TotalTime: -1", "TotalTime: null", "TotalTime: 999999999999999999999999999"})
   assertEquals("NA",CameraLaunchTiming.number(v,"TotalTime"));
  assertEquals("0",CameraLaunchTiming.number("TotalTime: 0","TotalTime"));
 }
 @Test public void doesNotPersistCommandContentOrUnknownState() {
  CameraLaunchTiming t=new CameraLaunchTiming(()->1_000_000L,0,2,"binder");
  t.commandResult("Activity: PRIVATE_PAYLOAD\nLaunchState: PRIVATE_PAYLOAD\nTotalTime: 12\n");
  String out=t.finish("FAILED");assertFalse(out.contains("PRIVATE_PAYLOAD"));
  assertTrue(out.contains("launchState=UNKNOWN"));assertTrue(out.contains("prepareUs=NA"));
 }
 @Test public void failureClosesCurrentStageAndCancellationDoesNotInventDurations() {
  AtomicLong c=new AtomicLong(1_000_000);
  CameraLaunchTiming t=new CameraLaunchTiming(c::get,0,3,"binder");
  t.begin("resolve");c.addAndGet(2_000_000);String out=t.finish("FAILED");
  assertTrue(out.contains("resolveUs=2000"));assertTrue(out.contains("launchUs=NA"));
  assertTrue(out.contains("keyguardUs=NA"));
  CameraLaunchTiming cancelled=new CameraLaunchTiming(c::get,c.get(),4,"binder");
  assertTrue(cancelled.finish("CANCELLED").contains("prepareUs=NA"));
 }
 public static class DeniedQuery {
  public int currentUser() { throw new SecurityException("Denied query"); }
 }
 @Test public void permissionDenialIsReportedWithoutReflectionWrapperOrRetry() throws Exception {
  SecurityException failure=assertThrows(SecurityException.class,()->CameraQueries.invoke(
          DeniedQuery.class.getMethod("currentUser"),new DeniedQuery()));
  assertEquals("Denied query",failure.getMessage());
 }
 @Test public void cameraEntryMustBeEnabledExportedAndInExactPackage() {
  assertTrue(CameraQueries.validTarget("com.oplus.camera","com.oplus.camera.Camera",true,true,true));
  assertFalse(CameraQueries.validTarget("other","Camera",true,true,true));
  assertFalse(CameraQueries.validTarget("com.oplus.camera","Camera",false,true,true));
  assertFalse(CameraQueries.validTarget("com.oplus.camera","Camera",true,false,true));
  assertFalse(CameraQueries.validTarget("com.oplus.camera","Camera",true,true,false));
  assertFalse(CameraQueries.validTarget("com.oplus.camera",null,true,true,true));
 }
}
