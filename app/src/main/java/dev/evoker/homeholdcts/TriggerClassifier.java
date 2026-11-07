// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

/** Classifies live ColorOS log events. Called under the watcher's trigger lock. */
final class TriggerClassifier {
    enum Source { NONE, POWER, HOME_GESTURE, VOICE_WAKE }
    enum Decision { IGNORED, VOICE_MARKER, DEBOUNCED, POWER_FOLLOW_UP, ACCEPTED }
    private Decision decision = Decision.IGNORED;
    private Source observed = Source.NONE;
    Decision decision() { return decision; }
    Source observedSource() { return observed; }
    static final long DEBOUNCE_MS = 2500L;
    private static final long POWER_MARKER_WINDOW_MS = 500L;
    private static final long VOICE_WAKE_MARKER_WINDOW_MS = 1000L;
    private static final long HOME_HANDLE_WINDOW_MS = 1200L;
    private static final long HOME_HANDLE_DOWN_WINDOW_MS = 450L;
    private long lastTrigger = -1L;
    private long lastPowerMarker = -1L;
    private long lastVoiceMarker = -1L;
    private long lastHomeHandleMarker = -1L;
    private long lastHomeHandleDown = -1L;

    // Diagnostic only: a Breeno Touch activity launch is not proof of a long press.
    static boolean isBreenoTouchRoute(String line) {
        return line != null && line.contains("com.coloros.colordirectservice")
                && line.contains("CollectInfoActivity");
    }

    // These events are emitted by SystemUI when the real bottom gesture bar
    // receives a touch. Ordinary home swipes also produce them, so they must
    // NEVER activate CTS by themselves.
    private static boolean isHomeHandleMarker(String line) {
        return line.contains("SystemUi--NavBar:")
                && line.contains("isCorrectHomeHandle = true");
    }

    private static boolean isHomeHandleDown(String line) {
        return line.contains("SystemUi--NavBar:")
                && line.contains("send down event to NavigationBarHandle");
    }

    // Correlated with the long hold on ColorOS 17 with Breeno Touch disabled.
    private static boolean isColorOs17OcrRoute(String line) {
        return line.contains("SystemUi--NavBar:")
                && line.contains("OcrScreenService-->getServiceIntent")
                && line.contains("StartUpType=0");
    }

    static boolean isCandidateLine(String line) {
        return line != null && (isBreenoTouchRoute(line)
                || isHomeHandleMarker(line)
                || isHomeHandleDown(line)
                || isColorOs17OcrRoute(line)
                || line.contains("Detect long press KEYCODE_POWER")
                || line.contains("PowerKey:onLongPress")
                || line.contains("wakeUpSpeechAssist caller_package: com.oplus.ovoicemanager.wakeup")
                || (line.contains("act=heytap.intent.action.ACTIVATE_SPEECH_ASSIST")
                    && line.contains("cmp=com.heytap.speechassist/.core.SpeechService")));
    }

    Source accept(String line, long now) {
        decision = Decision.IGNORED;
        observed = Source.NONE;
        if (line == null || line.isEmpty()) return Source.NONE;
        if (line.contains("wakeUpSpeechAssist caller_package: com.oplus.ovoicemanager.wakeup")) {
            lastVoiceMarker = now;
            decision = Decision.VOICE_MARKER;
            observed = Source.VOICE_WAKE;
            return Source.NONE;
        }
        if (isHomeHandleMarker(line)) {
            lastHomeHandleMarker = now;
            return Source.NONE;
        }
        if (isHomeHandleDown(line)) {
            if (recent(lastHomeHandleMarker, now, HOME_HANDLE_DOWN_WINDOW_MS)) {
                lastHomeHandleDown = now;
            }
            return Source.NONE;
        }
        boolean colorOs17Home = isColorOs17OcrRoute(line)
                && recent(lastHomeHandleDown, now, HOME_HANDLE_WINDOW_MS);
        if (isColorOs17OcrRoute(line)) {
            // Consume the gesture evidence, including if this OCR request was
            // too late. Never trigger on an OCR request alone.
            lastHomeHandleMarker = -1L;
            lastHomeHandleDown = -1L;
        }
        boolean power = line.contains("Detect long press KEYCODE_POWER")
                || line.contains("PowerKey:onLongPress");
        if (power) lastPowerMarker = now;
        else if (!colorOs17Home
                && (!line.contains("act=heytap.intent.action.ACTIVATE_SPEECH_ASSIST")
                || !line.contains("cmp=com.heytap.speechassist/.core.SpeechService"))) {
            return Source.NONE;
        }

        boolean voice = !power && !colorOs17Home
                && recent(lastVoiceMarker, now, VOICE_WAKE_MARKER_WINDOW_MS);
        if (voice) lastVoiceMarker = -1L;
        observed = power ? Source.POWER : (voice ? Source.VOICE_WAKE : Source.HOME_GESTURE);
        // Only an accepted activation moves the cooldown. Duplicate OEM markers
        // and the following speech-service attempt must never schedule another.
        if (recent(lastTrigger, now, DEBOUNCE_MS - 1L)) {
            decision = Decision.DEBOUNCED;
            return Source.NONE;
        }
        if (!power && !voice && recent(lastPowerMarker, now, POWER_MARKER_WINDOW_MS)) {
            decision = Decision.POWER_FOLLOW_UP;
            return Source.NONE;
        }
        lastTrigger = now;
        decision = Decision.ACCEPTED;
        return power ? Source.POWER : (voice ? Source.VOICE_WAKE : Source.HOME_GESTURE);
    }

    static boolean targetsAssistant(Source source, boolean powerGemini,
            boolean voiceAssistant, boolean swap) {
        if (source == Source.NONE) return false;
        if (source == Source.VOICE_WAKE) return voiceAssistant;
        return swap ? source == Source.HOME_GESTURE : source == Source.POWER && powerGemini;
    }

    private static boolean recent(long marker, long now, long window) {
        return marker >= 0L && now >= marker && now - marker <= window;
    }
}
