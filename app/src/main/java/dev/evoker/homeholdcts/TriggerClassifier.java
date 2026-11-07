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
    private static final long COLOROS17_WAKE_PAIR_WINDOW_MS = 1500L;
    private static final long HOME_HANDLE_WINDOW_MS = 1200L;
    private static final long HOME_HANDLE_DOWN_WINDOW_MS = 450L;
    private long lastTrigger = -1L;
    private long lastPowerMarker = -1L;
    private long lastVoiceMarker = -1L;
    private long lastColorOs17VoiceBind = -1L;
    private long lastColorOs17VoiceWakeLock = -1L;
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

    // ColorOS 17 attempts to bind the legacy speech agent after a recognized
    // XiaoBu hotword. A failed bind alone can also come from other callers,
    // so require a nearby wake lock from the actual XiaoBu wakeup process.
    private static boolean isColorOs17VoiceBind(String line) {
        return line.startsWith("ActivityManager:")
                && line.contains("act=heytap.speech.intent.action.BIND_SPEECH_ASSIST")
                && line.contains("cmp=com.heytap.speechassist/.agent.SpeechAssistAgentService");
    }

    private static boolean isColorOs17VoiceWakeLock(String line) {
        return line.startsWith("OplusAONSmartDim:")
                && line.contains("onWakeLockAcquireLocked:")
                && line.contains("packageName=com.oplus.ovoicemanager.wakeup")
                && line.contains("tag=\"WakeupService:\"");
    }

    static boolean isCandidateLine(String line) {
        return line != null && (isColorOs17VoiceBind(line)
                || isColorOs17VoiceWakeLock(line)
                || isBreenoTouchRoute(line)
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
        return accept(line, now, true);
    }

    Source accept(String line, long now, boolean voiceWakeEnabled) {
        decision = Decision.IGNORED;
        observed = Source.NONE;
        if (line == null || line.isEmpty()) return Source.NONE;
        if (line.contains("wakeUpSpeechAssist caller_package: com.oplus.ovoicemanager.wakeup")) {
            // Legacy verified marker: it already identifies the wakeup caller.
            // When voice routing is off, retain the marker briefly so the
            // following SpeechService attempt is ignored rather than mistaken
            // for a HOME_GESTURE / Circle to Search activation.
            lastVoiceMarker = voiceWakeEnabled ? -1L : now;
            return acceptVoiceWake(now, voiceWakeEnabled);
        }
        if (isColorOs17VoiceBind(line)) {
            lastColorOs17VoiceBind = now;
            if (recent(lastColorOs17VoiceWakeLock, now, COLOROS17_WAKE_PAIR_WINDOW_MS)) {
                lastColorOs17VoiceBind = -1L;
                lastColorOs17VoiceWakeLock = -1L;
                return acceptVoiceWake(now, voiceWakeEnabled);
            }
            return Source.NONE;
        }
        if (isColorOs17VoiceWakeLock(line)) {
            lastColorOs17VoiceWakeLock = now;
            if (recent(lastColorOs17VoiceBind, now, COLOROS17_WAKE_PAIR_WINDOW_MS)) {
                lastColorOs17VoiceBind = -1L;
                lastColorOs17VoiceWakeLock = -1L;
                return acceptVoiceWake(now, voiceWakeEnabled);
            }
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
        if (voice && !voiceWakeEnabled) {
            // A speech-service tail from a disabled XiaoBu voice route must
            // not accidentally become Circle to Search.
            decision = Decision.VOICE_MARKER;
            return Source.NONE;
        }
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

    private Source acceptVoiceWake(long now, boolean enabled) {
        observed = Source.VOICE_WAKE;
        if (!enabled) {
            decision = Decision.VOICE_MARKER;
            return Source.NONE;
        }
        if (recent(lastTrigger, now, DEBOUNCE_MS - 1L)
                || recent(lastPowerMarker, now, POWER_MARKER_WINDOW_MS)) {
            decision = Decision.DEBOUNCED;
            return Source.NONE;
        }
        lastTrigger = now;
        decision = Decision.ACCEPTED;
        return Source.VOICE_WAKE;
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
