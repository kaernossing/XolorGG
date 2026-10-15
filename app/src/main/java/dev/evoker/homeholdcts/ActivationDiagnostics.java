// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Only loaded in :watcher. No raw OEM lines, exception messages or user content. */
final class ActivationDiagnostics {
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256));
    private static final AtomicInteger DROPPED = new AtomicInteger();
    private static volatile Bundle latest = new Bundle();
    private static long sequence;
    private static String lastId = "NONE";

    static String trigger(Context c, TriggerClassifier.Source source, String target,
                          TriggerClassifier.Decision decision) {
        if (decision == TriggerClassifier.Decision.ACCEPTED) {
            lastId = AgentDiagnostics.session() + "-r" + (++sequence);
            Bundle next = new Bundle();
            next.putString("requestId", lastId);
            next.putString("requestSource", source.name());
            next.putString("requestTarget", target);
            next.putString("requestResult", "SCHEDULED");
            next.putLong("requestElapsed", SystemClock.elapsedRealtime());
            latest = next;
        }
        event(c, decision == TriggerClassifier.Decision.VOICE_MARKER ? "NONE" : lastId,
                "TRIGGER source=" + source + " target=" + target + " decision=" + decision);
        return lastId;
    }

    static void event(Context c, String id, String fields) {
        Context app = c.getApplicationContext();
        long occurred = SystemClock.elapsedRealtime();
        try {
            WRITER.execute(() -> {
                int dropped = DROPPED.getAndSet(0);
                if (dropped != 0) AgentDiagnostics.app(app, "DIAGNOSTIC_QUEUE_DROPPED count=" + dropped);
                AgentDiagnostics.app(app, "REQUEST id=" + id + " occurredElapsed=" + occurred + " " + fields);
            });
        } catch (RejectedExecutionException full) {
            DROPPED.incrementAndGet();
            android.util.Log.w("ColorGoogleDiagnostics", "Diagnostic queue full; record dropped");
        }
    }

    static void result(Context c, String id, String outcome, int attempt, long duration, String error) {
        Bundle next = new Bundle(latest);
        if (id.equals(next.getString("requestId"))) {
            next.putString("requestResult", outcome);
            next.putInt("requestAttempt", attempt);
            next.putLong("requestDurationMs", duration);
            next.putString("requestUi", "UNCONFIRMED");
            latest = next;
        }
        event(c, id, "RESULT outcome=" + outcome + " attempt=" + attempt
                + " durationMs=" + duration + " error=" + error + " ui=UNCONFIRMED");
    }

    static void addTo(Bundle out) {
        out.putAll(new Bundle(latest));
        out.putInt("diagnosticRecordsDropped", DROPPED.get());
    }
    private ActivationDiagnostics() {}
}
