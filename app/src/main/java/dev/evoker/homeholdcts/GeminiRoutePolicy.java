// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/**
 * Pure Gemini routing policy. HOME_GESTURE and CTS are never routed to Live.
 * An Android keyguard always requires normal authentication before Live.
 */
final class GeminiRoutePolicy {
    enum Action { ASSISTANT, LIVE_NOW, ASSISTANT_THEN_LIVE }

    static Action choose(TriggerClassifier.Source source, boolean targetsAssistant,
            boolean locked, boolean xiaoBuLiveUnlocked, boolean xiaoBuAfterUnlock,
            boolean powerLiveUnlocked) {
        if (!targetsAssistant) return Action.ASSISTANT;
        if (source == TriggerClassifier.Source.POWER) {
            return !locked && powerLiveUnlocked ? Action.LIVE_NOW : Action.ASSISTANT;
        }
        if (source == TriggerClassifier.Source.VOICE_WAKE) {
            if (locked) {
                return xiaoBuAfterUnlock ? Action.ASSISTANT_THEN_LIVE : Action.ASSISTANT;
            }
            return xiaoBuLiveUnlocked ? Action.LIVE_NOW : Action.ASSISTANT;
        }
        return Action.ASSISTANT;
    }

    private GeminiRoutePolicy() {}
}
