// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.diagnostics;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.aroum.kazuar.inputmethod.latin.R;

/** Local viewing and explicit copying only. No sharing or external destinations. */
public final class CrashReportDialog extends DialogFragment {
    private static final String REPORT_TAG = "kazuar.crash.report", NOTICE_TAG = "kazuar.crash.notice";

    public static void showReport(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()
                || activity.getSupportFragmentManager().isStateSaved()
                || activity.getSupportFragmentManager().findFragmentByTag(REPORT_TAG) != null) return;
        new CrashReportDialog().show(activity.getSupportFragmentManager(), REPORT_TAG);
    }

    public static void notifyIfNew(FragmentActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()
                || activity.getSupportFragmentManager().isStateSaved()
                || activity.getSupportFragmentManager().findFragmentByTag(NOTICE_TAG) != null) return;
        CrashReporter.recoverSystemExit(activity);
        if (!CrashReporter.takeNotice(activity)) return;
        CrashReportDialog dialog = new CrashReportDialog();
        Bundle arguments = new Bundle(); arguments.putBoolean("notice", true); dialog.setArguments(arguments);
        dialog.show(activity.getSupportFragmentManager(), NOTICE_TAG);
    }

    @Override public Dialog onCreateDialog(Bundle state) {
        FragmentActivity activity = requireActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(R.string.crash_report_title);
        if (getArguments() != null && getArguments().getBoolean("notice")) {
            return builder.setMessage(R.string.crash_report_saved)
                    .setPositiveButton(R.string.crash_report_open, (dialog, which) -> showReport(activity))
                    .setNegativeButton(android.R.string.cancel, null).create();
        }
        String report = CrashReporter.report(activity);
        if (report == null) return builder.setMessage(R.string.crash_report_empty)
                .setPositiveButton(android.R.string.ok, null).create();
        TextView text = new TextView(activity);
        text.setText(report); text.setTextSize(12); text.setTypeface(Typeface.MONOSPACE);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(activity); scroll.addView(text);
        return builder.setView(scroll).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.crash_report_copy, (dialog, which) -> {
                    ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                    if (clipboard == null) return;
                    ClipData clip = ClipData.newPlainText(getString(R.string.crash_report_title), report);
                    PersistableBundle flags = new PersistableBundle();
                    flags.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                    clip.getDescription().setExtras(flags);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(activity, R.string.crash_report_copied, Toast.LENGTH_SHORT).show();
                }).create();
    }
}
