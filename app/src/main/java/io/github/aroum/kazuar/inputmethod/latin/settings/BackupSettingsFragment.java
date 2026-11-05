package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.preference.Preference;
import io.github.aroum.kazuar.inputmethod.latin.R;

public final class BackupSettingsFragment extends SubScreenFragment {
    private static final int REQ_EXPORT_SETTINGS = 1001;
    private static final int REQ_IMPORT_SETTINGS = 1002;
    private static final int REQ_EXPORT_DICT = 1003;
    private static final int REQ_IMPORT_DICT = 1004;
    private static final int REQ_EXPORT_HISTORY = 1005;
    private static final int REQ_IMPORT_HISTORY = 1006;
    private static final int REQ_EXPORT_ALL = 1007;
    private static final int REQ_IMPORT_ALL = 1008;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_backup);

        findPreference("pref_backup_all_export").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                exportAll();
                return true;
            }
        });

        findPreference("pref_backup_all_import").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                importAll();
                return true;
            }
        });

        findPreference("pref_backup_settings_export").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                exportSettings();
                return true;
            }
        });

        findPreference("pref_backup_settings_import").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                importSettings();
                return true;
            }
        });

        findPreference("pref_backup_dict_export").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                exportDictionary();
                return true;
            }
        });

        findPreference("pref_backup_dict_import").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                importDictionary();
                return true;
            }
        });

        findPreference("pref_backup_history_export").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                exportHistory();
                return true;
            }
        });

        findPreference("pref_backup_history_import").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                importHistory();
                return true;
            }
        });
    }

    private void exportSettings() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "kazuar_settings_backup.json");
        startActivityForResult(intent, REQ_EXPORT_SETTINGS);
    }

    private void importSettings() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT_SETTINGS);
    }

    private void exportDictionary() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "kazuar_dictionary_backup.json");
        startActivityForResult(intent, REQ_EXPORT_DICT);
    }

    private void importDictionary() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT_DICT);
    }

    private void exportHistory() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "kazuar_history_backup.zip");
        startActivityForResult(intent, REQ_EXPORT_HISTORY);
    }

    private void importHistory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_HISTORY);
    }

    private void exportAll() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "kazuar_full_backup.zip");
        startActivityForResult(intent, REQ_EXPORT_ALL);
    }

    private void importAll() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT_ALL);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Context context = getActivity();
        if (context != null && requestCode >= REQ_EXPORT_SETTINGS && requestCode <= REQ_IMPORT_ALL) {
            BackupRepository.run(context, getSharedPreferences(), data.getData(), requestCode);
        }
    }
}
