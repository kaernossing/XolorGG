// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.os.Bundle;
import android.os.SystemClock;
import java.util.concurrent.*;

/** Owned by the shell agent. Sampling never runs on log delivery or Binder threads. */
final class AssistantDiagnostics implements AutoCloseable {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final DiagnosticSampleGate gate = new DiagnosticSampleGate();
    private volatile AssistantSnapshot latest = AssistantSnapshot.unknown("NOT_SAMPLED", -1);
    private long sequence;

    void request() {
        if (!gate.begin(SystemClock.elapsedRealtime())) return;
        try {
            worker.execute(() -> {
                String id = AgentDiagnostics.session() + "-s" + (++sequence);
                sample(id, "LEADING");
                try {
                    worker.schedule(() -> {
                        try { sample(id, "FOLLOW_UP"); } finally { gate.finish(); }
                    }, 1000, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException stopped) { gate.finish(); }
            });
        } catch (RejectedExecutionException stopped) { gate.finish(); }
    }

    private void sample(String id, String phase) {
        AssistantSnapshot value;
        try {
            String dump = BoundedDiagnosticCommand.run(3000, 65536,
                    "/system/bin/dumpsys", "-t", "2", "voiceinteraction");
            value = AssistantSnapshot.parse(dump, SystemClock.elapsedRealtime());
        } catch (TimeoutException e) {
            value = AssistantSnapshot.unknown("TIMEOUT", SystemClock.elapsedRealtime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            value = AssistantSnapshot.unknown("INTERRUPTED", SystemClock.elapsedRealtime());
        } catch (ExecutionException e) {
            value = AssistantSnapshot.unknown(e.getCause() != null && "OUTPUT_LIMIT".equals(e.getCause().getMessage())
                    ? "OUTPUT_LIMIT" : "PIPE_READ_FAILED", SystemClock.elapsedRealtime());
        } catch (java.io.IOException e) {
            String message = e.getMessage();
            value = AssistantSnapshot.unknown(message != null && message.matches("COMMAND_EXIT_[0-9]+")
                    ? message : "COMMAND_IO_FAILED", SystemClock.elapsedRealtime());
        } catch (Exception e) {
            value = AssistantSnapshot.unknown("READ_FAILED_" + e.getClass().getSimpleName(), SystemClock.elapsedRealtime());
        }
        latest = value;
        AgentDiagnostics.shell("ASSISTANT_SAMPLE id=" + id + " phase=" + phase + " " + value.event());
    }

    void addTo(Bundle out) {
        AssistantSnapshot value = latest;
        long now = SystemClock.elapsedRealtime();
        out.putString("assistantConnection", value.effectiveMain(now));
        out.putString("assistantSampleConnection", value.main);
        out.putString("assistantSession", value.session);
        out.putString("assistantSessionShown", value.shown);
        out.putString("assistantSampleReason", value.reason);
        out.putLong("assistantSampleElapsed", value.sampledAt);
        out.putLong("assistantSampleAgeMs", value.sampledAt < 0 ? -1 : now - value.sampledAt);
        out.putString("assistantUi", "UNCONFIRMED");
    }

    @Override public void close() { gate.close(); worker.shutdownNow(); }
}
