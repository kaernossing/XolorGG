// SPDX-License-Identifier: GPL-3.0-only
package dev.evoker.homeholdcts;
import android.os.Bundle;
interface ICameraAgent {
    Bundle getStatus();
    void shutdown();
}
