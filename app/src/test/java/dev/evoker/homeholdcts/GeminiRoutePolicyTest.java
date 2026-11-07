// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static dev.evoker.homeholdcts.GeminiRoutePolicy.Action.*;
import static dev.evoker.homeholdcts.TriggerClassifier.Source.*;

public class GeminiRoutePolicyTest {
    @Test public void powerLiveOnlyWhenEnabledAndUnlocked() {
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(POWER,true,false,true,true,false));
        assertEquals(LIVE_NOW, GeminiRoutePolicy.choose(POWER,true,false,false,false,true));
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(POWER,true,true,true,true,true));
    }
    @Test public void xiaoBuUnlockedAndAfterUnlockAreIndependent() {
        assertEquals(LIVE_NOW, GeminiRoutePolicy.choose(VOICE_WAKE,true,false,true,false,false));
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(VOICE_WAKE,true,false,false,true,true));
        assertEquals(ASSISTANT_THEN_LIVE, GeminiRoutePolicy.choose(VOICE_WAKE,true,true,false,true,false));
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(VOICE_WAKE,true,true,true,false,true));
    }
    @Test public void ctsAndSwapGestureNeverUsePowerLiveSwitch() {
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(POWER,false,false,true,true,true));
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(HOME_GESTURE,false,false,true,true,true));
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(HOME_GESTURE,true,false,true,true,true));
        assertEquals(ASSISTANT, GeminiRoutePolicy.choose(NONE,true,false,true,true,true));
    }
}
