// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeminiLiveShellTest {
    @Test public void findsVisibleLiveButtonWithNestedCoordinates() {
        String dump =
                "DecorView\n"
                + "  com.android.internal.widget.DecorView{123abc V.E...... a 0,0-1080,2376}\n"
                + "    android.widget.FrameLayout{abcdef V.E...... a 0,30-1080,2300}\n"
                + "      android.view.View{a1b2c3 V.E...... a 100,100-900,1200}\n"
                + "        voice_chat_button_compose{d34db3 V.E...... a 50,40-150,140}\n";
        assertArrayEquals(new int[] {200, 220}, GeminiLiveShell.findLiveButton(dump));
    }

    @Test public void extractsUnderlyingFailureInsteadOfExecutionException() {
        java.util.concurrent.ExecutionException wrapped =
                new java.util.concurrent.ExecutionException(
                        new java.io.IOException("OUTPUT_LIMIT"));
        assertEquals("VIEW_DUMP_OUTPUT_LIMIT",
                GeminiLiveShell.failureCode("VIEW_DUMP", wrapped));
        assertEquals("LIVE_TAP_COMMAND_TIMEOUT",
                GeminiLiveShell.failureCode("LIVE_TAP",
                        new java.util.concurrent.TimeoutException("COMMAND_TIMEOUT")));
    }

    @Test public void rejectsHiddenAndMissingLiveButtons() {
        assertNull(GeminiLiveShell.findLiveButton("no Live button"));
        assertNull(GeminiLiveShell.findLiveButton(
                "DecorView\n"
                + "  android.view.View{abcdef G.E...... a 0,0-1080,2376}\n"
                + "    voice_chat_button_compose{beef11 V.E...... a 10,10-80,80}\n"));
        assertNull(GeminiLiveShell.findLiveButton(
                "DecorView\n"
                + "  voice_chat_button_compose{abcdef V.E...... a 10,10-10,10}\n"));
    }
}
