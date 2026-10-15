// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import java.io.Closeable;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/** Cooperative, process-lifetime exclusion across both apps; also detects older agents. */
final class SharedAgentLease implements Closeable {
    private final RandomAccessFile file;
    private final FileLock lock;
    SharedAgentLease(String otherProcess) throws Exception {
        file = new RandomAccessFile("/data/local/tmp/color-shortcut-controller.lock", "rw");
        FileLock acquired = null;
        try {
            acquired = file.getChannel().tryLock();
            if (acquired == null) throw new IllegalStateException("Another Color shortcut agent is running; stop it first");
            String ps = BoundedDiagnosticCommand.run(3000, 262144, "/system/bin/ps", "-A", "-o", "NAME");
            if (containsProcess(ps, otherProcess)) throw new IllegalStateException("Stop " + otherProcess + " before activation");
            lock = acquired;
        } catch (Exception e) {
            if (acquired != null) acquired.release();
            file.close();
            throw e;
        }
    }
    static boolean containsProcess(String output, String name) {
        String[] lines = output.split("\\n");
        if (lines.length == 0 || !lines[0].trim().equals("NAME"))
            throw new IllegalStateException("Cannot inspect other agents");
        for (int i = 1; i < lines.length; i++) if (lines[i].trim().equals(name)) return true;
        return false;
    }
    @Override public void close() throws java.io.IOException { try { lock.release(); } finally { file.close(); } }
}
