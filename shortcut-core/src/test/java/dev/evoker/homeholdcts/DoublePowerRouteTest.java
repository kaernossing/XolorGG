// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import org.junit.Test;
import static org.junit.Assert.*;
public class DoublePowerRouteTest {
    private boolean match(int uid, String pkg, String cp, String c, String action) {
        return DoublePowerRoute.matches(uid, pkg, cp, c, action);
    }
    @Test public void exactSystemShortcutOnly() {
        String p = DoublePowerRoute.OEM_PACKAGE, c = DoublePowerRoute.OEM_COMPONENT, a = DoublePowerRoute.OEM_ACTION;
        assertTrue(match(1000,p,p,c,a));
        assertFalse(match(101000,p,p,c,a));
        assertFalse(match(2000,p,p,c,a));
        assertFalse(match(1000,"com.finshell.wallet",p,c,a));
        assertFalse(match(1000,p,"other",c,a));
        assertFalse(match(1000,p,p,"OtherActivity",a));
        assertFalse(match(1000,p,p,c,"android.intent.action.MAIN"));
        assertFalse(match(1000,p,p,c,null));
    }
    @Test public void disabledByDefaultAndIndependentControllerMatrix() {
        DoublePowerRoute route = new DoublePowerRoute();
        assertFalse(route.enabled()); assertEquals("wallet",route.target()); assertEquals(-1,route.accept(0));
        assertFalse(DoublePowerRoute.needsController(false,false));
        assertTrue(DoublePowerRoute.needsController(true,false));
        assertTrue(DoublePowerRoute.needsController(false,true));
        assertTrue(DoublePowerRoute.needsController(true,true));
    }
    @Test public void duplicateDoesNotExtendInterval() {
        DoublePowerRoute r = new DoublePowerRoute(); r.configure(true,"wallet");
        assertTrue(r.accept(1000)>=0); assertEquals(-1,r.accept(1500)); assertTrue(r.accept(1800)>=0);
    }
    @Test public void oldWorkCannotSurviveSwitchDisableOrReconnect() {
        DoublePowerRoute r = new DoublePowerRoute(); r.configure(true,"wallet"); int first=r.accept(1000);
        r.configure(true,"camera"); assertFalse(r.current(first)); int next=r.accept(1800);
        r.configure(false,"camera"); assertFalse(r.current(next));
        r.configure(true,"camera"); assertFalse(r.current(next)); assertTrue(r.current(r.accept(2600)));
    }
    @Test public void idempotentConfigurationKeepsCurrentWork() {
        DoublePowerRoute r = new DoublePowerRoute(); r.configure(true,"wallet"); int token=r.accept(1000);
        r.configure(true,"wallet"); assertTrue(r.current(token));
    }
    @Test(expected=IllegalArgumentException.class) public void unknownTargetRejected() { new DoublePowerRoute().configure(true,"arbitrary"); }
}
