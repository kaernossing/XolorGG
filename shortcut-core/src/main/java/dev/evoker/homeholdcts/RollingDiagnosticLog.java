// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class RollingDiagnosticLog {
    static synchronized void append(File file, String line, int limit) throws IOException {
        byte[] bytes = (line.replace('\n', ' ').replace('\r', ' ') + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) throw new IOException("Diagnostic record exceeds limit");
        if (file.length() + bytes.length > limit) {
            File previous = new File(file + ".1");
            if (previous.exists() && !previous.delete()) throw new IOException("rotate delete");
            if (!file.renameTo(previous)) throw new IOException("rotate rename");
        }
        try (FileOutputStream out = new FileOutputStream(file, true)) { out.write(bytes); }
    }
    private RollingDiagnosticLog() {}
}
