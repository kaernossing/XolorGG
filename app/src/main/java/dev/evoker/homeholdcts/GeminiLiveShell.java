// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Experimental Gemini Live UI launcher. Runs ONLY inside DesktopSearchAgent
 * (ADB shell uid), never on the app's UI or watcher thread.
 *
 * Google exposes a Live widget, but no stable third-party Live intent. We
 * therefore open Gemini and find its real Live view, never blind-tapping a
 * fixed coordinate. If the view is missing, no tap occurs.
 */
final class GeminiLiveShell {
    private static final String GEMINI_ENTRY =
            "com.google.android.apps.bard/.shellapp.BardEntryPointActivity";
    private static final String GEMINI_VIEW =
            "com.google.android.googlequicksearchbox/"
            + "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity";
    private static final Pattern VIEW_LINE = Pattern.compile(
            "\\{[0-9a-fA-F]+\\s+(\\S+)\\s+\\S+\\s+"
            + "(-?\\d+),(-?\\d+)-(-?\\d+),(-?\\d+)");
    private static final int MAX_OBSERVATIONS = 24;

    private GeminiLiveShell() {}

    static boolean launch() throws Exception {
        if (ShellCamera.locked()) return false;
        String launch = run(6000, 8192, "/system/bin/am", "start",
                "-n", GEMINI_ENTRY);
        if (launch.contains("Error:") || launch.contains("Exception")) {
            throw new IllegalStateException("Gemini entry activity unavailable");
        }

        int[] previous = null;
        int stable = 0;
        for (int n = 0; n < MAX_OBSERVATIONS; n++) {
            SystemClock.sleep(250);
            if (ShellCamera.locked()) return false;
            String dump = run(2700, 160000, "/system/bin/dumpsys",
                    "activity", GEMINI_VIEW);
            int[] position = findLiveButton(dump);
            if (position == null) {
                previous = null;
                stable = 0;
                continue;
            }
            if (previous != null && previous[0] == position[0]
                    && previous[1] == position[1]) {
                stable++;
            } else {
                stable = 1;
            }
            previous = position;
            // Allow the button to settle after Gemini's entry animation.
            if (stable < 3) continue;
            if (ShellCamera.locked()) return false;
            run(2500, 1024, "/system/bin/input", "tap",
                    Integer.toString(position[0]),
                    Integer.toString(position[1]));
            // A successful tap may still be ignored by Gemini; do not repeat
            // blindly, as a second tap could hit a different Live control.
            return true;
        }
        return false;
    }

    /**
     * Find the centre of a *visible* Live button in the Gemini activity view
     * hierarchy. Android dumps coordinates relative to the containing view.
     */
    static int[] findLiveButton(String dump) {
        if (dump == null || !dump.contains("voice_chat_button_compose")) return null;
        ArrayDeque<ViewOffset> stack = new ArrayDeque<>();
        boolean decorFound = false;
        for (String line : dump.split("\\n")) {
            if (line.contains("DecorView")) {
                stack.clear();
                decorFound = true;
            }
            if (!decorFound) continue;
            Matcher m = VIEW_LINE.matcher(line);
            if (!m.find()) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            while (!stack.isEmpty() && stack.peek().indent >= indent) stack.pop();
            ViewOffset parent = stack.peek();
            int parentX = parent == null ? 0 : parent.x;
            int parentY = parent == null ? 0 : parent.y;
            boolean parentVisible = parent == null || parent.visible;
            int left = Integer.parseInt(m.group(2));
            int top = Integer.parseInt(m.group(3));
            int right = Integer.parseInt(m.group(4));
            int bottom = Integer.parseInt(m.group(5));
            boolean visible = parentVisible && m.group(1).startsWith("V");
            int x = parentX + left, y = parentY + top;
            if (visible && line.contains("voice_chat_button_compose")
                    && right > left && bottom > top) {
                return new int[] {x + (right - left) / 2, y + (bottom - top) / 2};
            }
            stack.push(new ViewOffset(indent, x, y, visible));
        }
        return null;
    }

    private static String run(int timeout, int cap, String... command) throws Exception {
        return BoundedDiagnosticCommand.run(timeout, cap, command);
    }

    private static final class ViewOffset {
        final int indent, x, y;
        final boolean visible;
        ViewOffset(int indent, int x, int y, boolean visible) {
            this.indent = indent;
            this.x = x;
            this.y = y;
            this.visible = visible;
        }
    }
}
