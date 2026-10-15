// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/** Connection epochs invalidate callbacks and queued actions before cleanup starts. */
final class AgentLifecycle {
    enum State { CONNECTING, RUNNING, WAITING, STOPPING }
    private State state = State.WAITING;
    private int generation;
    private int failures;
    private long retryAt;
    synchronized int begin(long now) {
        if (state != State.WAITING || now < retryAt) return -1;
        state = State.CONNECTING;
        return ++generation;
    }
    synchronized boolean connected(int token) {
        if (!current(token) || state != State.CONNECTING) return false;
        state = State.RUNNING; failures = 0; return true;
    }
    synchronized boolean lost(int token, long now) {
        if (!current(token)) return false;
        state = State.WAITING;
        retryAt = now + Math.min(30000L, 1000L << Math.min(failures++, 5));
        return true;
    }
    synchronized boolean current(int token) {
        return token == generation && (state == State.CONNECTING || state == State.RUNNING);
    }
    synchronized boolean dispatch(int token) { return token == generation && state == State.RUNNING; }
    synchronized boolean acceptListener(int token, boolean removing) {
        if (current(token)) return true;
        if (removing) return false; // Old cleanup must never clear a newer listener.
        throw new IllegalStateException("Stale agent connection");
    }
    synchronized void stop() { state = State.STOPPING; }
    static boolean ownsController(String owned, String current) {
        return owned != null && !owned.isEmpty() && owned.equals(current);
    }
    synchronized State state() { return state; }
    synchronized int generation() { return generation; }
}
