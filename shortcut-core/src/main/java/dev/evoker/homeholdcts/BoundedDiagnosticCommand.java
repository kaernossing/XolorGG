// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Drains the pipe while waiting, with limits on runtime and retained output. */
final class BoundedDiagnosticCommand {
    static String run(long timeoutMs, int maxBytes, String... argv) throws Exception {
        Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
        FutureTask<String> read = new FutureTask<>(() -> {
            try (InputStream input = process.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (out.size() + count > maxBytes) throw new IOException("OUTPUT_LIMIT");
                    out.write(buffer, 0, count);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        });
        Thread reader = new Thread(read, "assistant-dump-reader");
        reader.setDaemon(true);
        reader.start();
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            String output = read.get(timeoutMs, TimeUnit.MILLISECONDS);
            long remaining = end - System.nanoTime();
            if (remaining <= 0 || !process.waitFor(remaining, TimeUnit.NANOSECONDS))
                throw new TimeoutException("COMMAND_TIMEOUT");
            if (process.exitValue() != 0) throw new IOException("COMMAND_EXIT_" + process.exitValue());
            return output;
        } finally {
            process.destroyForcibly();
            read.cancel(true);
            process.getInputStream().close();
        }
    }
    private BoundedDiagnosticCommand() {}
}
