// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings;

import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.system.Os;
import android.system.OsConstants;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import androidx.fragment.app.DialogFragment;
import androidx.preference.ListPreference;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.aroum.kazuar.inputmethod.keyboard.Keyboard;
import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardLayoutSet;
import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardTheme;
import io.github.aroum.kazuar.inputmethod.latin.InputAttributes;
import io.github.aroum.kazuar.inputmethod.latin.RichInputMethodManager;
import io.github.aroum.kazuar.inputmethod.latin.RichInputMethodSubtype;
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static org.junit.Assert.*;

/** Runs against the signed, minified and resource-shrunk CI APK, not debug resources. */
@RunWith(AndroidJUnit4.class)
public class ShrunkApkSmokeTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void dynamicResourcesHaveRealPayloadsOn16KiBAndroid() throws Exception {
        assertEquals(16384L, Os.sysconf(OsConstants._SC_PAGESIZE));
        System.loadLibrary("jni_latinime");
        JSONArray entries;
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("resources.json")) {
            entries = new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertTrue(entries.length() > 100);
        Context context = context();
        for (int i = 0; i < entries.length(); i++) {
            JSONArray entry = entries.getJSONArray(i);
            String kind = entry.getString(0), name = entry.getString(1);
            int id = context.getResources().getIdentifier(name, kind, context.getPackageName());
            assertTrue(kind + "/" + name + " missing", id != 0);
            if (kind.equals("xml")) {
                try (XmlResourceParser xml = context.getResources().getXml(id)) {
                    boolean hasRoot = false;
                    while (xml.next() != XmlPullParser.END_DOCUMENT) {
                        if (!hasRoot && xml.getEventType() == XmlPullParser.START_TAG) {
                            boolean expectedRoot = false;
                            JSONArray roots = entry.getJSONArray(2);
                            for (int r = 0; r < roots.length(); r++) {
                                expectedRoot |= roots.getString(r).equals(xml.getName());
                            }
                            assertTrue(name + " has unexpected/placeholder root " + xml.getName(), expectedRoot);
                            hasRoot = true;
                        }
                    }
                    assertTrue(name + " is a shrinker placeholder", hasRoot);
                }
            } else if (kind.equals("raw")) {
                try (InputStream input = context.getResources().openRawResource(id)) {
                    assertTrue(name + " is empty", input.read() >= 0);
                }
            } else {
                assertNotNull(context.getResources().getString(id));
            }
        }
    }

    @Test public void buildRuEnVariantsAndInputModesInLightAndDarkThemes() {
        Context context = context();
        Settings.init(context);
        RichInputMethodManager.init(context);
        android.content.SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        try {
            for (int theme : new int[]{3, 7}) {
                KeyboardTheme.saveKeyboardThemeId(theme, prefs);
                Context themed = new ContextThemeWrapper(context, KeyboardTheme.getKeyboardTheme(context).mStyleId);
                for (String language : new String[]{"ru", "en"}) {
                    RichInputMethodSubtype subtype = new RichInputMethodSubtype(
                            new InputMethodSubtype.InputMethodSubtypeBuilder()
                                    .setSubtypeLocale(language.equals("en") ? "en_US" : "ru")
                                    .setSubtypeMode("keyboard").setIsAsciiCapable(true)
                                    .setSubtypeExtraValue("KeyboardLayoutSet=" +
                                            (language.equals("en") ? "qwerty" : "east_slavic"))
                                    .build());
                    for (String variant : new String[]{"v1", "v2", "v3", "v4"}) {
                        prefs.edit().putString("pref_keyboard_layout_" + language, variant).commit();
                        for (int mode : new int[]{InputType.TYPE_CLASS_TEXT, InputType.TYPE_CLASS_NUMBER,
                                InputType.TYPE_CLASS_PHONE}) {
                            EditorInfo editor = new EditorInfo();
                            editor.packageName = context.getPackageName(); editor.inputType = mode;
                            Settings.getInstance().loadSettings(context, new Locale(language),
                                    new InputAttributes(editor, false, context.getPackageName()));
                            KeyboardLayoutSet.clearKeyboardCache();
                            KeyboardLayoutSet set = new KeyboardLayoutSet.Builder(themed, editor)
                                    .setSubtype(subtype).setKeyboardGeometry(1080, 480).build();
                            for (int element : mode == InputType.TYPE_CLASS_TEXT ? new int[]{0, 1, 5, 6, 9}
                                    : new int[]{0}) {
                                Keyboard keyboard = set.getKeyboard(element);
                                assertFalse(language + "/" + variant + "/" + mode + "/" + element,
                                        keyboard.getSortedKeys().isEmpty());
                            }
                        }
                    }
                }
            }
        } finally {
            prefs.edit().clear().commit();
            KeyboardLayoutSet.clearKeyboardCache();
            Settings.getInstance().onDestroy();
        }
    }

    @Test public void minifiedSettingsOpenBothLayoutPickers() {
        Context context = context();
        Intent intent = new Intent(context, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT, LayoutSettingsFragment.class.getName());
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                LayoutSettingsFragment screen = (LayoutSettingsFragment)
                        activity.getSupportFragmentManager().getFragments().get(0);
                for (String key : new String[]{"pref_keyboard_layout_ru", "pref_keyboard_layout_en"}) {
                    ListPreference pref = screen.findPreference(key);
                    assertNotNull(pref); pref.performClick();
                    activity.getSupportFragmentManager().executePendingTransactions();
                    DialogFragment dialog = (DialogFragment) activity.getSupportFragmentManager()
                            .findFragmentByTag("kazuar.preference.dialog");
                    assertNotNull(dialog); assertTrue(dialog.requireDialog().isShowing());
                    dialog.dismiss(); activity.getSupportFragmentManager().executePendingTransactions();
                }
            });
        }
    }
}
