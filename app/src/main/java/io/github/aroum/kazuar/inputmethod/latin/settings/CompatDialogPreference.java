// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

/** Hosts the existing custom seek-bar and subtype editors as AndroidX preferences. */
public class CompatDialogPreference extends androidx.preference.DialogPreference
        implements DialogInterface.OnClickListener {
    private AlertDialog dialog;

    public CompatDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        showDialog(null);
    }

    protected void showDialog(Bundle state) {
        if (dialog != null && dialog.isShowing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getDialogTitle());
        View view = onCreateDialogView();
        if (view != null) {
            onBindDialogView(view);
            builder.setView(view);
        }
        onPrepareDialogBuilder(builder);
        dialog = builder.create();
        dialog.show();
    }

    protected View onCreateDialogView() {
        return LayoutInflater.from(getContext()).inflate(getDialogLayoutResource(), null);
    }

    protected void onBindDialogView(View view) {}
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {}
    public Dialog getDialog() { return dialog; }
    @Override public void onClick(DialogInterface dialog, int which) {}

    @Override
    public void onDetached() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
        super.onDetached();
    }
}
