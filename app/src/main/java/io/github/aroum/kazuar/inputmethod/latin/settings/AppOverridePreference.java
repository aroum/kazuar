// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.content.Context;
import android.widget.CheckBox;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceViewHolder;

/** The row opens settings; only its checkbox changes the override switch. */
public final class AppOverridePreference extends CheckBoxPreference {
    private final Runnable open;
    public AppOverridePreference(Context context, Runnable open) { super(context); this.open = open; }
    @Override protected void onClick() { open.run(); }
    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        CheckBox checkbox = (CheckBox) holder.findViewById(android.R.id.checkbox);
        if (checkbox == null) return;
        checkbox.setClickable(true);
        checkbox.setFocusable(true);
        checkbox.setContentDescription(getTitle() + ": " + getContext().getString(
                io.github.aroum.kazuar.inputmethod.latin.R.string.app_override_enabled));
        checkbox.setOnClickListener(view -> {
            boolean checked = checkbox.isChecked();
            if (callChangeListener(checked)) setChecked(checked);
            else checkbox.setChecked(isChecked());
        });
    }
}
