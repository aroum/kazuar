package io.github.sds100.keymapper.inputmethod.latin.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import io.github.sds100.keymapper.inputmethod.latin.R;
import io.github.sds100.keymapper.inputmethod.latin.settings.SettingsValues.DoubleTapRule;

import java.util.ArrayList;
import java.util.Locale;

public final class DoubleTapSettingsFragment extends SubScreenFragment {
    private static final String TAG = DoubleTapSettingsFragment.class.getSimpleName();
    private String mLang = "ru";
    private ArrayList<DoubleTapRule> mRules = new ArrayList<>();

    public static class DoubleTapRulePreference extends Preference {
        public interface Listener {
            void onRuleClick(int index);
            void onRuleToggle(int index, boolean enabled);
        }

        private final int mIndex;
        private DoubleTapRule mRule;
        private final Listener mListener;

        public DoubleTapRulePreference(
                final Context context,
                final int index,
                final DoubleTapRule rule,
                final boolean isRuLocale,
                final Listener listener) {
            super(context);
            mIndex = index;
            mRule = rule;
            mListener = listener;
            setKey("double_tap_rule_" + index);
            setTitle(rule.key + rule.key + " → " + rule.replacement);
            setSummary(isRuLocale ? "Нажмите для редактирования/удаления" : "Tap to edit/delete");
            setPersistent(false);
            setWidgetLayoutResource(R.layout.preference_double_tap_rule_widget);
        }

        public int getIndex() {
            return mIndex;
        }

        @Override
        protected void onBindView(final View view) {
            super.onBindView(view);

            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (mListener != null) {
                        mListener.onRuleClick(mIndex);
                        return true;
                    }
                    return false;
                }
            });

            final Switch switchWidget = view.findViewById(R.id.double_tap_rule_switch);
            if (switchWidget != null) {
                switchWidget.setOnCheckedChangeListener(null);
                switchWidget.setChecked(mRule.enabled);
                switchWidget.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final boolean isChecked = ((Switch) v).isChecked();
                        mRule = new DoubleTapRule(mRule.key, mRule.replacement, isChecked);
                        if (mListener != null) {
                            mListener.onRuleToggle(mIndex, isChecked);
                        }
                    }
                });
            }
        }

        @Override
        protected void onClick() {
            super.onClick();
            if (mListener != null) {
                mListener.onRuleClick(mIndex);
            }
        }
    }

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
                ? (isRu ? "Настройка двойных тапов для ru (" + layoutVersion + ")" : "Setup Double-Taps for ru (" + layoutVersion + ")")
                : (isRu ? "Настройка двойных тапов для en (" + layoutVersion + ")" : "Setup Double-Taps for en (" + layoutVersion + ")"));
        
        setHasOptionsMenu(true);
        loadRules();
        rebuildPreferenceScreen();
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final View view = getView();
        if (view != null) {
            final ListView lv = view.findViewById(android.R.id.list);
            if (lv != null) {
                lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                        final Object item = getPreferenceScreen().getRootAdapter().getItem(position);
                        if (item instanceof DoubleTapRulePreference) {
                            showAddOrEditDialog(((DoubleTapRulePreference) item).getIndex());
                            return true;
                        }
                        return false;
                    }
                });
            }
        }
    }

    private boolean isRussianLocale() {
        return Locale.getDefault().getLanguage().equals("ru");
    }

    private void loadRules() {
        SharedPreferences prefs = getSharedPreferences();
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
        if (screen == null) return;
        screen.removeAll();

        boolean isRu = isRussianLocale();

        for (int i = 0; i < mRules.size(); i++) {
            final int index = i;
            final DoubleTapRule rule = mRules.get(i);

            final DoubleTapRulePreference rulePref = new DoubleTapRulePreference(
                    getActivity(),
                    index,
                    rule,
                    isRu,
                    new DoubleTapRulePreference.Listener() {
                        @Override
                        public void onRuleClick(int idx) {
                            showAddOrEditDialog(idx);
                        }

                        @Override
                        public void onRuleToggle(int idx, boolean enabled) {
                            if (idx >= 0 && idx < mRules.size()) {
                                DoubleTapRule currentRule = mRules.get(idx);
                                mRules.set(idx, new DoubleTapRule(currentRule.key, currentRule.replacement, enabled));
                                saveRules();
                            }
                        }
                    });

            screen.addPreference(rulePref);
        }
    }

    private void showAddOrEditDialog(final int index) {
        final Context context = getActivity();
        if (context == null) return;

        final boolean isRu = isRussianLocale();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(index == -1 
            ? (isRu ? "Добавить правило" : "Add Double-Tap Rule")
            : (isRu ? "Редактировать правило" : "Edit Double-Tap Rule"));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText keyInput = new EditText(context);
        keyInput.setHint(isRu 
            ? ("ru".equals(mLang) ? "Клавиша (напр. ы)" : "Клавиша (напр. q)")
            : ("ru".equals(mLang) ? "Key (e.g. ы)" : "Key (e.g. q)"));
        if (index >= 0 && index < mRules.size()) {
            keyInput.setText(mRules.get(index).key);
        }
        layout.addView(keyInput);

        final EditText replacementInput = new EditText(context);
        replacementInput.setHint(isRu
            ? ("ru".equals(mLang) ? "Замена (напр. ю)" : "Замена (напр. w)")
            : ("ru".equals(mLang) ? "Replacement (e.g. ю)" : "Replacement (e.g. w)"));
        if (index >= 0 && index < mRules.size()) {
            replacementInput.setText(mRules.get(index).replacement);
        }
        layout.addView(replacementInput);

        builder.setView(layout);

        builder.setPositiveButton(isRu ? "Сохранить" : "Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String key = keyInput.getText().toString().trim();
                String replacement = replacementInput.getText().toString().trim();
                if (key.isEmpty() || replacement.isEmpty()) {
                    Toast.makeText(context, isRu ? "Поля не должны быть пустыми" : "Fields must not be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (index == -1) {
                    mRules.add(new DoubleTapRule(key, replacement, true));
                } else if (index >= 0 && index < mRules.size()) {
                    mRules.set(index, new DoubleTapRule(key, replacement, mRules.get(index).enabled));
                }
                saveRules();
                rebuildPreferenceScreen();
            }
        });
        builder.setNegativeButton(isRu ? "Отмена" : "Cancel", null);

        if (index >= 0 && index < mRules.size()) {
            builder.setNeutralButton(isRu ? "Удалить" : "Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (index >= 0 && index < mRules.size()) {
                        mRules.remove(index);
                        saveRules();
                        rebuildPreferenceScreen();
                    }
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
            addStyleItem.setTitle(isRussianLocale() ? "Добавить правило" : "Add rule");
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
