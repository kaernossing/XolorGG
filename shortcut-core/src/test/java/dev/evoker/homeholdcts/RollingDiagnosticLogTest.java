// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import static org.junit.Assert.*;

public class RollingDiagnosticLogTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void rotatesBeforeExceedingLimitAndKeepsOnlyTwoFiles() throws Exception {
        File file = new File(temp.getRoot(), "survival.log");
        for (int i = 0; i < 100; i++) RollingDiagnosticLog.append(file, "event=" + i, 32);
        assertEquals(2, temp.getRoot().listFiles().length);
        assertTrue(file.length() <= 32);
        assertTrue(new File(file + ".1").length() <= 32);
        assertTrue(new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8).contains("event=99"));
    }
    @Test public void rejectedOversizeRecordDoesNotErasePriorEvidence() throws Exception {
        File file = new File(temp.getRoot(), "survival.log");
        RollingDiagnosticLog.append(file, "safe", 8);
        try { RollingDiagnosticLog.append(file, "123456789", 8); fail(); }
        catch (IOException expected) { }
        assertEquals("safe\n", new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void multilineRecordCannotForgeAnotherLogLine() throws Exception {
        File file = new File(temp.getRoot(), "survival.log");
        RollingDiagnosticLog.append(file, "one\ntwo\rthree", 64);
        assertEquals("one two three\n", new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
    }
}
