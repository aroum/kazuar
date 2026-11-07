// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;
import androidx.preference.ListPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/** Common AndroidX preference lifecycle and the existing settings storage location. */
public class CompatPreferenceFragment extends PreferenceFragmentCompat {
    private static final String DIALOG_TAG = "kazuar.preference.dialog";

    @Override
    public void onCreatePreferences(Bundle state, String rootKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getPreferenceManager().setStorageDeviceProtected();
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // Activity chrome and the existing custom dialogs use platform themes. AndroidX's
        // standard preference dialogs need their own AppCompat theme, including on restore.
        DialogFragment dialog;
        if (preference instanceof ListPreference) dialog = new ThemedListDialog();
        else if (preference instanceof MultiSelectListPreference) dialog = new ThemedMultiSelectDialog();
        else if (preference instanceof EditTextPreference) dialog = new ThemedEditTextDialog();
        else { super.onDisplayPreferenceDialog(preference); return; }
        if (getParentFragmentManager().isStateSaved()
                || getParentFragmentManager().findFragmentByTag(DIALOG_TAG) != null) return;
        Bundle arguments = new Bundle();
        arguments.putString("key", preference.getKey());
        dialog.setArguments(arguments);
        dialog.setTargetFragment(this, 0);
        dialog.show(getParentFragmentManager(), DIALOG_TAG);
    }

    private static Context dialogContext(Context context) {
        if (context == null) return null;
        TypedValue light = new TypedValue();
        boolean isLight = context.getTheme().resolveAttribute(android.R.attr.isLightTheme, light, true)
                && light.data != 0;
        return new ContextThemeWrapper(context, isLight
                ? androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert
                : androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert);
    }

    // Public no-argument constructors let FragmentManager restore the themed dialogs.
    public static class ThemedListDialog extends ListPreferenceDialogFragmentCompat {
        @Override public Context getContext() { return dialogContext(super.getContext()); }
    }
    public static class ThemedMultiSelectDialog extends MultiSelectListPreferenceDialogFragmentCompat {
        @Override public Context getContext() { return dialogContext(super.getContext()); }
    }
    public static class ThemedEditTextDialog extends EditTextPreferenceDialogFragmentCompat {
        @Override public Context getContext() { return dialogContext(super.getContext()); }
    }
}
