// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.os.Build;
import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;

/** Common AndroidX preference lifecycle and the existing settings storage location. */
public class CompatPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle state, String rootKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getPreferenceManager().setStorageDeviceProtected();
        }
    }
}
