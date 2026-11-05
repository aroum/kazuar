// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;
import io.github.aroum.kazuar.inputmethod.keyboard.internal.CustomLayoutLoader;
import io.github.aroum.kazuar.inputmethod.keyboard.internal.KeyboardBuilder;
import io.github.aroum.kazuar.inputmethod.keyboard.internal.KeyboardParams;
import io.github.aroum.kazuar.inputmethod.latin.R;
import io.github.aroum.kazuar.inputmethod.latin.RichInputMethodSubtype;
import io.github.aroum.kazuar.inputmethod.latin.utils.SubtypeLocaleUtils;

/** Builds real keys with the same headers as runtime, without constructing native proximity data. */
public final class CustomLayoutValidator {
    private CustomLayoutValidator() {}

    public static void validate(Context context, String target, String xml) throws Exception {
        CustomLayoutLoader.validateStructure(xml);
        SubtypeLocaleUtils.init(context);
        int element = KeyboardId.ELEMENT_ALPHABET;
        int header = "ru".equals(target) ? R.xml.kbd_east_slavic : R.xml.kbd_qwerty;
        if ("editing".equals(target)) { element = KeyboardId.ELEMENT_EDITING; header = R.xml.kbd_editing; }
        else if ("num".equals(target)) { element = KeyboardId.ELEMENT_NUMBER; header = R.xml.kbd_number; }
        else if (target.startsWith("usr")) {
            element = KeyboardId.ELEMENT_USER_0 + Integer.parseInt(target.substring(3));
            header = R.xml.kbd_user_empty;
        }
        for (int orientation : new int[]{Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE}) {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.orientation = orientation;
            Context configured = context.createConfigurationContext(configuration);
            Context themed = new ContextThemeWrapper(configured, KeyboardTheme.getKeyboardTheme(context).mStyleId);
            for (int mode = KeyboardId.MODE_TEXT; mode <= KeyboardId.MODE_DATETIME; mode++) {
                KeyboardLayoutSet.Params set = new KeyboardLayoutSet.Params();
                set.mMode = mode;
                set.mKeyboardLayoutSetName = "ru".equals(target) ? "east_slavic" : "qwerty";
                set.mKeyboardWidth = orientation == Configuration.ORIENTATION_PORTRAIT ? 1080 : 1920;
                set.mKeyboardHeight = 480;
                set.mSubtype = new RichInputMethodSubtype(new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeLocale("ru".equals(target) ? "ru" : "en_US")
                        .setSubtypeMode("keyboard")
                        .setSubtypeExtraValue("KeyboardLayoutSet=" + set.mKeyboardLayoutSetName).build());
                set.mEditorInfo = new EditorInfo();
                set.mEditorInfo.imeOptions = EditorInfo.IME_ACTION_DONE;
                KeyboardParams params = new KeyboardParams();
                new KeyboardBuilder<>(themed, params).loadHeaderOnly(header, new KeyboardId(element, set));
                CustomLayoutLoader.loadCustomLayoutOrThrow(themed, params, xml);
            }
        }
    }
}
