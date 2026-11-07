// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import org.junit.Test;
import static org.junit.Assert.*;
import static dev.evoker.homeholdcts.TriggerClassifier.Source.*;

public class TriggerClassifierTest {
    @Test public void diagnosticDecisionsDoNotMoveDebounceWindow() {
        TriggerClassifier c = new TriggerClassifier();
        assertEquals(POWER, c.accept(KEY, 0));
        assertEquals(TriggerClassifier.Decision.ACCEPTED, c.decision());
        assertEquals(NONE, c.accept(MONITOR, 2499));
        assertEquals(TriggerClassifier.Decision.DEBOUNCED, c.decision());
        assertEquals(POWER, c.observedSource());
        assertEquals(POWER, c.accept(KEY, 2500));
        assertEquals(TriggerClassifier.Decision.ACCEPTED, c.decision());
    }
    private static final String KEY = "KEYLOG_SingleKeyGesture: Detect long press KEYCODE_POWER";
    private static final String MONITOR = "KEYLOG_SinglePowerKeyMonitor: PowerKey:onLongPress, underWaterCameraStatus = false";
    private static final String SERVICE = "ActivityManager: Unable to start service Intent { act=heytap.intent.action.ACTIVATE_SPEECH_ASSIST cmp=com.heytap.speechassist/.core.SpeechService }: not found";
    private static final String VOICE = "wakeUpSpeechAssist caller_package: com.oplus.ovoicemanager.wakeup";
    @Test public void screenOffPowerNeedsNoSpeechService() {
        assertEquals(POWER,new TriggerClassifier().accept(KEY,0));
        assertEquals(POWER,new TriggerClassifier().accept(MONITOR,100));
    }
    @Test public void oemPairAndSpeechAttemptOnlyActivateOnce() {
        TriggerClassifier c=new TriggerClassifier();
        assertEquals(POWER,c.accept(KEY,10000));
        assertEquals(NONE,c.accept(MONITOR,10002));
        assertEquals(NONE,c.accept(SERVICE,10003));
        assertEquals(NONE,c.accept(SERVICE,12000));
    }
    @Test public void reverseOrderAlsoDeduplicates() {
        TriggerClassifier c=new TriggerClassifier();
        assertEquals(POWER,c.accept(MONITOR,10000));
        assertEquals(NONE,c.accept(KEY,10001));
        assertEquals(NONE,c.accept(SERVICE,10002));
    }
    @Test public void duplicateDoesNotExtendCooldown() {
        TriggerClassifier c=new TriggerClassifier();
        assertEquals(POWER,c.accept(KEY,0));
        assertEquals(NONE,c.accept(MONITOR,2499));
        assertEquals(POWER,c.accept(KEY,2500));
    }
    @Test public void suppressedPowerTailDoesNotBecomeHomeAfterCooldown() {
        TriggerClassifier c=new TriggerClassifier();
        assertEquals(POWER,c.accept(KEY,0));
        assertEquals(NONE,c.accept(KEY,2400));
        assertEquals(NONE,c.accept(SERVICE,2600));
        assertEquals(HOME_GESTURE,c.accept(SERVICE,3000));
    }
    @Test public void gestureAndVoiceRetainTheirRoutes() {
        TriggerClassifier c=new TriggerClassifier();
        assertEquals(HOME_GESTURE,c.accept(SERVICE,0));
        assertEquals(NONE,c.accept(VOICE,3000));
        assertEquals(VOICE_WAKE,c.accept(SERVICE,3001));
        assertEquals(HOME_GESTURE,c.accept(SERVICE,6000));
    }
    @Test public void powerTakesPriorityOverStaleVoiceMarker() {
        TriggerClassifier c=new TriggerClassifier();
        c.accept(VOICE,100);
        assertEquals(POWER,c.accept(KEY,101));
        assertEquals(NONE,c.accept(SERVICE,102));
    }
    @Test public void unrelatedAndIncompleteLogsDoNothing() {
        TriggerClassifier c=new TriggerClassifier();
        assertEquals(NONE,c.accept(null,0));
        assertEquals(NONE,c.accept("",0));
        assertEquals(NONE,c.accept("act=heytap.intent.action.ACTIVATE_SPEECH_ASSIST",0));
        assertEquals(NONE,c.accept("PowerKey:onShortPress",0));
    }
    @Test public void breenoTouchRouteIsDiagnosticOnly() {
        String line = "ActivityTaskManager: START u0 {cmp=com.coloros.colordirectservice/com.coloros.directui.ui.CollectInfoActivity}";
        assertTrue(TriggerClassifier.isBreenoTouchRoute(line));
        assertTrue(TriggerClassifier.isCandidateLine(line));
        assertEquals(NONE, new TriggerClassifier().accept(line, 1000));
        assertFalse(TriggerClassifier.isBreenoTouchRoute("NavigationBarHandle: send down event"));
    }
    private static final String HOME_HANDLE =
            "SystemUi--NavBar: NoBackGesture-->gestureBar animation: gestureBarNotHide = true, isCorrectHomeHandle = true";
    private static final String HOME_DOWN =
            "SystemUi--NavBar: NoBackGesture-->send down event to NavigationBarHandle";
    private static final String OCR_ROUTE =
            "SystemUi--NavBar: OcrScreenService-->getServiceIntent bundle: Bundle[{StartUpType=0}]";

    @Test public void colorOs17HomeHoldActivatesWithoutSpeechService() {
        TriggerClassifier c = new TriggerClassifier();
        assertTrue(TriggerClassifier.isCandidateLine(HOME_HANDLE));
        assertTrue(TriggerClassifier.isCandidateLine(HOME_DOWN));
        assertTrue(TriggerClassifier.isCandidateLine(OCR_ROUTE));
        assertEquals(NONE, c.accept(HOME_HANDLE, 1000));
        assertEquals(NONE, c.accept(HOME_DOWN, 1002));
        assertEquals(HOME_GESTURE, c.accept(OCR_ROUTE, 1300));
        assertEquals(NONE, c.accept(OCR_ROUTE, 1305));
    }

    @Test public void ordinaryHomeSwipeNeverTriggersCts() {
        TriggerClassifier c = new TriggerClassifier();
        assertEquals(NONE, c.accept(HOME_HANDLE, 1000));
        assertEquals(NONE, c.accept(HOME_DOWN, 1001));
        assertEquals(NONE, c.accept("SystemUi--NavBar: SideGestureDetector--> ev:MotionEvent { action=ACTION_UP }", 1400));
        assertEquals(NONE, c.accept("VibratorManagerServiceExtImpl: opPkg=com.android.launcher mEffectId=367", 1401));
    }

    @Test public void ocrRouteAloneOrAfterExpiredHandleDoesNotTrigger() {
        TriggerClassifier c = new TriggerClassifier();
        assertEquals(NONE, c.accept(OCR_ROUTE, 100));
        assertEquals(NONE, c.accept(HOME_DOWN, 200));
        assertEquals(NONE, c.accept(OCR_ROUTE, 300));
        assertEquals(NONE, c.accept(HOME_HANDLE, 1000));
        assertEquals(NONE, c.accept(HOME_DOWN, 1001));
        assertEquals(NONE, c.accept(OCR_ROUTE, 2300));
        assertEquals(NONE, c.accept(OCR_ROUTE, 2301));
    }

    @Test public void colorOs17HomeHoldRespectsDebounceAndSwap() {
        TriggerClassifier c = new TriggerClassifier();
        c.accept(HOME_HANDLE, 1000);
        c.accept(HOME_DOWN, 1001);
        assertEquals(HOME_GESTURE, c.accept(OCR_ROUTE, 1100));
        c.accept(HOME_HANDLE, 1800);
        c.accept(HOME_DOWN, 1801);
        assertEquals(NONE, c.accept(OCR_ROUTE, 1850));
        assertEquals(TriggerClassifier.Decision.DEBOUNCED, c.decision());
        assertFalse(TriggerClassifier.targetsAssistant(HOME_GESTURE, true, false, false));
        assertTrue(TriggerClassifier.targetsAssistant(HOME_GESTURE, true, false, true));
    }

    @Test public void preferencesAndSwapPreserved() {
        assertTrue(TriggerClassifier.targetsAssistant(POWER,true,false,false));
        assertFalse(TriggerClassifier.targetsAssistant(HOME_GESTURE,true,false,false));
        assertFalse(TriggerClassifier.targetsAssistant(POWER,false,false,false));
        assertFalse(TriggerClassifier.targetsAssistant(POWER,true,false,true));
        assertTrue(TriggerClassifier.targetsAssistant(HOME_GESTURE,true,false,true));
        assertTrue(TriggerClassifier.targetsAssistant(VOICE_WAKE,false,true,true));
        assertFalse(TriggerClassifier.targetsAssistant(VOICE_WAKE,true,false,false));
    }
}
