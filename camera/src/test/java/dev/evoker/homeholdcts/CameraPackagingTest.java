// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import io.github.joeyandu.colorcamera.BuildConfig;
public class CameraPackagingTest {
    @Test public void appAndPcUseSameStarterAndNeverKillOtherAgent() throws Exception {
        String script=new String(Files.readAllBytes(Paths.get("../tools/activate-camera.sh")), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(script,BuildConfig.AGENT_START_SCRIPT);
        assertTrue(script.contains("pidof colorgoogle-search"));
        assertFalse(script.contains("kill "));
        assertTrue(script.contains("requested=false"));
    }
    @Test public void standaloneIdentityAndNoSensitivePermissions() throws Exception {
        assertEquals("io.github.joeyandu.colorcamera",BuildConfig.APPLICATION_ID);
        String manifest=new String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")), java.nio.charset.StandardCharsets.UTF_8);
        for(String forbidden:new String[]{"READ_LOGS","SYSTEM_ALERT_WINDOW","RECORD_AUDIO","android.permission.CAMERA","INTERNET","Shizuku","WakeLogBridge"}) assertFalse(forbidden,manifest.contains(forbidden));
    }
}
