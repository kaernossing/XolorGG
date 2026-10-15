// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class AgentStarterTest {
    @Test public void embeddedStarterExactlyMatchesDistributedScript() throws Exception {
        Path source = Paths.get("../tools/activate-agent.sh");
        assertEquals(new String(Files.readAllBytes(source), java.nio.charset.StandardCharsets.UTF_8), DesktopSearchActivation.START_SCRIPT);
        assertFalse(DesktopSearchActivation.START_SCRIPT.contains("--method stop"));
        assertTrue(DesktopSearchActivation.START_SCRIPT.contains("nohup setsid app_process"));
    }
}
