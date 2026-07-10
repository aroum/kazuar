package io.github.sds100.keymapper.inputmethod.latin.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import io.github.sds100.keymapper.inputmethod.latin.R;
import io.github.sds100.keymapper.inputmethod.latin.settings.SettingsValues.DoubleTapRule;

import java.util.ArrayList;
import java.util.Locale;

public final class DoubleTapSettingsFragment extends SubScreenFragment {
    private static final String TAG = DoubleTapSettingsFragment.class.getSimpleName();
    private String mLang = "ru";
    private ArrayList<DoubleTapRule> mRules = new ArrayList<>();

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        final Bundle args = getArguments();
        mLang = (args != null && args.containsKey("lang")) ? args.getString("lang") : "ru";
        
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
        setPreferenceScreen(screen);
        
        String layoutVersion = getSharedPreferences().getString("pref_keyboard_layout_" + mLang, "v3");
        boolean isRu = isRussianLocale();
        screen.setTitle(mLang.equals("ru") 
                ? "Setup Double-Taps for ru (" + layoutVersion + ")"
                : "Setup Double-Taps for en (" + layoutVersion + ")");
        
        setHasOptionsMenu(true);
        loadRules();
        rebuildPreferenceScreen();
    }

    private boolean isRussianLocale() {
        return Locale.getDefault().getLanguage().equals("ru");
    }

    private void loadRules() {
        SharedPreferences prefs = getSharedPreferences();
        // Load default rules if preference not set yet
        String defaultsJson = "[]";
        if ("ru".equals(mLang)) {
            try {
                org.json.JSONArray array = new org.json.JSONArray();
                String[][] defaults = {
                    {"ы", "ю"},
                    {"ь", "ъ"},
                    {"ш", "щ"},
                    {"й", "э"},
                    {"ч", "ф"},
                    {"х", "ц"}
                };
                for (String[] pair : defaults) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("key", pair[0]);
                    obj.put("replacement", pair[1]);
                    obj.put("enabled", true);
                    array.put(obj);
                }
                defaultsJson = array.toString();
            } catch (Exception e) {
                Log.e(TAG, "Failed to build default JSON", e);
            }
        }

        String layoutVersion = prefs.getString("pref_keyboard_layout_" + mLang, "v3");
        String json = prefs.getString("pref_custom_double_tap_rules_" + mLang + "_" + layoutVersion, defaultsJson);
        mRules = parseDoubleTapRules(json);
    }

    private ArrayList<DoubleTapRule> parseDoubleTapRules(String jsonStr) {
        ArrayList<DoubleTapRule> list = new ArrayList<>();
        try {
            org.json.JSONArray array = new org.json.JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                String key = obj.getString("key");
                String replacement = obj.getString("replacement");
                boolean enabled = obj.getBoolean("enabled");
                list.add(new DoubleTapRule(key, replacement, enabled));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse double tap rules", e);
        }
        return list;
    }

    private void saveRules() {
        String json = SettingsValues.serializeDoubleTapRules(mRules);
        String layoutVersion = getSharedPreferences().getString("pref_keyboard_layout_" + mLang, "v3");
        getSharedPreferences().edit().putString("pref_custom_double_tap_rules_" + mLang + "_" + layoutVersion, json).apply();
    }

    private void rebuildPreferenceScreen() {
        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();

        boolean isRu = isRussianLocale();

        for (int i = 0; i < mRules.size(); i++) {
            final int index = i;
            final DoubleTapRule rule = mRules.get(i);

            final SwitchPreference switchPref = new SwitchPreference(getActivity());
            switchPref.setKey("double_tap_rule_" + i);
            switchPref.setTitle(rule.key + rule.key + " → " + rule.replacement);
            switchPref.setSummary("Tap to edit/delete");
            switchPref.setChecked(rule.enabled);
            switchPref.setPersistent(false);

            // To avoid opening the edit dialog on switch toggle, we can check if the preference click happened.
            // Wait, in Android, when a SwitchPreference is clicked, OnPreferenceChangeListener runs BEFORE OnPreferenceClickListener.
            // So we can set a flag when OnPreferenceChangeListener runs, and check it in OnPreferenceClickListener!
            // Let's implement this state tracking.
            // Since we need to reset the flag, we can do it with a static or instance variable, or by posting a Runnable.
            // Let's use an array/holder for a boolean flag:
            final boolean[] switchToggled = new boolean[]{false};

            switchPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean enabled = (Boolean) newValue;
                    mRules.set(index, new DoubleTapRule(rule.key, rule.replacement, enabled));
                    saveRules();
                    switchToggled[0] = true;
                    return true;
                }
            });

            switchPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (switchToggled[0]) {
                        // The click was on the switch itself (toggle), so do not open the edit dialog
                        switchToggled[0] = false;
                        return true;
                    }
                    showAddOrEditDialog(index);
                    return true;
                }
            });

            screen.addPreference(switchPref);
        }
    }

    private void showAddOrEditDialog(final int index) {
        final Context context = getActivity();
        
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(index == -1 
            ? "Add Double-Tap Rule"
            : "Edit Double-Tap Rule");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText keyInput = new EditText(context);
        keyInput.setHint("Key (e.g. ы)");
        if (index >= 0) {
            keyInput.setText(mRules.get(index).key);
        }
        layout.addView(keyInput);

        final EditText replacementInput = new EditText(context);
        replacementInput.setHint("Replacement (e.g. ю)");
        if (index >= 0) {
            replacementInput.setText(mRules.get(index).replacement);
        }
        layout.addView(replacementInput);

        builder.setView(layout);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String key = keyInput.getText().toString().trim();
                String replacement = replacementInput.getText().toString().trim();
                if (key.isEmpty() || replacement.isEmpty()) {
                    Toast.makeText(context, "Fields must not be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (index == -1) {
                    mRules.add(new DoubleTapRule(key, replacement, true));
                } else {
                    mRules.set(index, new DoubleTapRule(key, replacement, mRules.get(index).enabled));
                }
                saveRules();
                rebuildPreferenceScreen();
            }
        });
        builder.setNegativeButton("Cancel", null);

        if (index >= 0) {
            builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mRules.remove(index);
                    saveRules();
                    rebuildPreferenceScreen();
                }
            });
        }

        builder.show();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.add_style, menu);
        MenuItem addStyleItem = menu.findItem(R.id.action_add_style);
        if (addStyleItem != null) {
            addStyleItem.setTitle("Add rule");
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_add_style) {
            showAddOrEditDialog(-1);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
