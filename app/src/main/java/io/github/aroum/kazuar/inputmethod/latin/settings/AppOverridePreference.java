// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.content.Context;
import android.widget.Switch;
import androidx.preference.SwitchPreference;
import androidx.preference.PreferenceViewHolder;

/** The row opens settings; only its switch changes the override switch. */
public final class AppOverridePreference extends SwitchPreference {
    private final Runnable open;
    public AppOverridePreference(Context context, Runnable open) {
        super(context);
        this.open = open;
        setSwitchTextOn("");
        setSwitchTextOff("");
    }
    @Override protected void onClick() { open.run(); }
    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // Same framework switch ID used internally by AndroidX SwitchPreference.
        Switch toggle = (Switch) holder.findViewById(0x01020040);
        if (toggle == null) return;
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setContentDescription(getTitle() + ": " + getContext().getString(
                io.github.aroum.kazuar.inputmethod.latin.R.string.app_override_enabled));
        // SwitchPreference already binds checked state, persistence and change validation.
    }
}
