package io.github.sds100.keymapper.inputmethod.latin.settings;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.provider.UserDictionary.Words;
import android.widget.Toast;

import io.github.sds100.keymapper.inputmethod.latin.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.github.sds100.keymapper.inputmethod.latin.common.FileUtils;
import io.github.sds100.keymapper.inputmethod.latin.personalization.PersonalizationHelper;
import io.github.sds100.keymapper.inputmethod.latin.personalization.UserHistoryDictionary;

public final class BackupSettingsFragment extends SubScreenFragment {
    private static final int REQ_EXPORT_SETTINGS = 1001;
    private static final int REQ_IMPORT_SETTINGS = 1002;
    private static final int REQ_EXPORT_DICT = 1003;
    private static final int REQ_IMPORT_DICT = 1004;
    private static final int REQ_EXPORT_HISTORY = 1005;
    private static final int REQ_IMPORT_HISTORY = 1006;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_backup);

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        Context context = getActivity();
        if (context == null) return;

        try {
            if (requestCode == REQ_EXPORT_SETTINGS) {
                doExportSettings(context, uri);
            } else if (requestCode == REQ_IMPORT_SETTINGS) {
                doImportSettings(context, uri);
            } else if (requestCode == REQ_EXPORT_DICT) {
                doExportDict(context, uri);
            } else if (requestCode == REQ_IMPORT_DICT) {
                doImportDict(context, uri);
            } else if (requestCode == REQ_EXPORT_HISTORY) {
                doExportHistory(context, uri);
            } else if (requestCode == REQ_IMPORT_HISTORY) {
                doImportHistory(context, uri);
            }
        } catch (Exception e) {
            Toast.makeText(context, R.string.restore_fail, Toast.LENGTH_LONG).show();
        }
    }

    private void doExportSettings(Context context, Uri uri) throws Exception {
        SharedPreferences sharedPrefs = getSharedPreferences();
        Map<String, ?> allEntries = sharedPrefs.getAll();
        JSONObject json = new JSONObject();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(json.toString(4).getBytes("UTF-8"));
                Toast.makeText(context, R.string.backup_success, Toast.LENGTH_SHORT).show();
            } else {
                throw new Exception("Output stream is null");
            }
        }
    }

    private void doImportSettings(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JSONObject json = new JSONObject(sb.toString());
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.clear();

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                HashSet<String> set = new HashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    set.add(array.getString(i));
                }
                editor.putStringSet(key, set);
            }
        }
        editor.apply();
        Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show();
    }

    private void doExportDict(Context context, Uri uri) throws Exception {
        JSONArray array = new JSONArray();
        try (Cursor cursor = context.getContentResolver().query(Words.CONTENT_URI, null, null, null, null)) {
            if (cursor != null) {
                int wordIdx = cursor.getColumnIndex(Words.WORD);
                int shortcutIdx = cursor.getColumnIndex(Words.SHORTCUT);
                int freqIdx = cursor.getColumnIndex(Words.FREQUENCY);
                int localeIdx = cursor.getColumnIndex(Words.LOCALE);

                while (cursor.moveToNext()) {
                    JSONObject obj = new JSONObject();
                    obj.put("word", cursor.getString(wordIdx));
                    if (shortcutIdx >= 0) obj.put("shortcut", cursor.getString(shortcutIdx));
                    if (freqIdx >= 0) obj.put("frequency", cursor.getInt(freqIdx));
                    if (localeIdx >= 0) obj.put("locale", cursor.getString(localeIdx));
                    array.put(obj);
                }
            }
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(array.toString(4).getBytes("UTF-8"));
                Toast.makeText(context, R.string.backup_success, Toast.LENGTH_SHORT).show();
            } else {
                throw new Exception("Output stream is null");
            }
        }
    }

    private void doImportDict(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JSONArray array = new JSONArray(sb.toString());

        HashSet<String> existing = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(Words.CONTENT_URI, new String[]{Words.WORD, Words.SHORTCUT, Words.LOCALE}, null, null, null)) {
            if (cursor != null) {
                int wordIdx = cursor.getColumnIndex(Words.WORD);
                int shortcutIdx = cursor.getColumnIndex(Words.SHORTCUT);
                int localeIdx = cursor.getColumnIndex(Words.LOCALE);
                while (cursor.moveToNext()) {
                    String w = cursor.getString(wordIdx);
                    String s = shortcutIdx >= 0 ? cursor.getString(shortcutIdx) : null;
                    String l = localeIdx >= 0 ? cursor.getString(localeIdx) : null;
                    existing.add(w + "|" + (s != null ? s : "") + "|" + (l != null ? l : ""));
                }
            }
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String word = obj.getString("word");
            String shortcut = obj.optString("shortcut", null);
            int freq = obj.optInt("frequency", 250);
            String locale = obj.optString("locale", null);

            String key = word + "|" + (shortcut != null ? shortcut : "") + "|" + (locale != null ? locale : "");
            if (!existing.contains(key)) {
                ContentValues values = new ContentValues();
                values.put(Words.WORD, word);
                values.put(Words.SHORTCUT, shortcut);
                values.put(Words.FREQUENCY, freq);
                values.put(Words.LOCALE, locale);
                context.getContentResolver().insert(Words.CONTENT_URI, values);
            }
        }

        Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show();
    }

    private void doExportHistory(Context context, Uri uri) throws Exception {
        PersonalizationHelper.flushAllUserHistoryDictionaries();
        File filesDir = context.getFilesDir();
        if (filesDir == null) {
            throw new Exception("filesDir is null");
        }
        File[] historyFiles = filesDir.listFiles(new PersonalizationHelper.DictFilter(UserHistoryDictionary.NAME));
        ArrayList<File> validFiles = new ArrayList<>();
        if (historyFiles != null) {
            for (File file : historyFiles) {
                if (!file.getName().endsWith(".tmp")) {
                    validFiles.add(file);
                }
            }
        }
        if (validFiles.isEmpty()) {
            Toast.makeText(context, R.string.history_backup_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            if (outputStream == null) {
                throw new Exception("Output stream is null");
            }
            ZipEntry metaEntry = new ZipEntry("metadata.json");
            zos.putNextEntry(metaEntry);
            JSONObject meta = new JSONObject();
            meta.put("version", 1);
            meta.put("type", "kazuar_user_history");
            meta.put("timestamp", System.currentTimeMillis());
            zos.write(meta.toString(4).getBytes("UTF-8"));
            zos.closeEntry();

            for (File file : validFiles) {
                addFileToZip("", file, zos);
            }
            Toast.makeText(context, R.string.backup_success, Toast.LENGTH_SHORT).show();
        }
    }

    private void addFileToZip(String basePath, File file, ZipOutputStream zos) throws IOException {
        String entryName = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
        if (file.isDirectory()) {
            if (!entryName.endsWith("/")) {
                entryName += "/";
            }
            zos.putNextEntry(new ZipEntry(entryName));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!child.getName().endsWith(".tmp")) {
                        addFileToZip(entryName.substring(0, entryName.length() - 1), child, zos);
                    }
                }
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    private void doImportHistory(Context context, Uri uri) throws Exception {
        PersonalizationHelper.closeAllUserHistoryDictionaries();
        File cacheDir = context.getCacheDir();
        File tempDir = new File(cacheDir, "history_import_tmp_" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new Exception("Cannot create temp directory: " + tempDir);
        }

        try {
            boolean hasHistoryFiles = false;
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 ZipInputStream zis = new ZipInputStream(inputStream)) {
                if (inputStream == null) {
                    throw new Exception("Input stream is null");
                }
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                String canonicalDest = tempDir.getCanonicalPath();
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    File destFile = new File(tempDir, name);
                    String canonicalFile = destFile.getCanonicalPath();
                    if (!canonicalFile.startsWith(canonicalDest)) {
                        throw new SecurityException("Zip traversal detected: " + name);
                    }
                    if (entry.isDirectory()) {
                        destFile.mkdirs();
                    } else {
                        File parent = destFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        try (FileOutputStream fos = new FileOutputStream(destFile)) {
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    if (name.startsWith(UserHistoryDictionary.NAME)) {
                        hasHistoryFiles = true;
                    }
                    zis.closeEntry();
                }
            }

            if (!hasHistoryFiles) {
                throw new Exception("Archive does not contain user history files");
            }

            File filesDir = context.getFilesDir();
            if (filesDir != null) {
                FileUtils.deleteFilteredFiles(filesDir, new PersonalizationHelper.DictFilter(UserHistoryDictionary.NAME));
                File[] extracted = tempDir.listFiles();
                if (extracted != null) {
                    for (File file : extracted) {
                        if (file.getName().startsWith(UserHistoryDictionary.NAME)) {
                            copyRecursively(file, new File(filesDir, file.getName()));
                        }
                    }
                }
            }
            PersonalizationHelper.closeAllUserHistoryDictionaries();
            Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show();
        } finally {
            FileUtils.deleteRecursively(tempDir);
        }
    }

    private void copyRecursively(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs()) {
                throw new IOException("Cannot create directory: " + dest);
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursively(child, new File(dest, child.getName()));
                }
            }
        } else {
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        }
    }
}
