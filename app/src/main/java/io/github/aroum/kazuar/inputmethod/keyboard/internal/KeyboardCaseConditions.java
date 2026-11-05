// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.keyboard.internal;

import android.content.res.TypedArray;
import android.util.TypedValue;
import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardId;
import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardTheme;
import io.github.aroum.kazuar.inputmethod.latin.R;
import io.github.aroum.kazuar.inputmethod.latin.common.StringUtils;
import io.github.aroum.kazuar.inputmethod.latin.utils.ResourceUtils;
import org.xmlpull.v1.XmlPullParser;
import java.util.Locale;

/** One set of case conditions for compiled resources and imported XML. */
final class KeyboardCaseConditions {
    private KeyboardCaseConditions() {}
    private interface Values {
        boolean matchString(int index, String value);
        boolean matchTypedValue(int index, int number, String name);
        boolean matchBoolean(int index, boolean value);
        boolean matchInteger(int index, int value);
        boolean isIconDefined(int index, KeyboardIconsSet icons);
    }

    static boolean matchesCompiled(KeyboardParams params, final TypedArray attrs) {
        return matches(params, new Values() {
            public boolean matchString(int i, String v) { return KeyboardCaseConditions.matchString(attrs, i, v); }
            public boolean matchTypedValue(int i, int v, String s) { return KeyboardCaseConditions.matchTypedValue(attrs, i, v, s); }
            public boolean matchBoolean(int i, boolean v) { return KeyboardCaseConditions.matchBoolean(attrs, i, v); }
            public boolean matchInteger(int i, int v) { return KeyboardCaseConditions.matchInteger(attrs, i, v); }
            public boolean isIconDefined(int i, KeyboardIconsSet icons) { return KeyboardCaseConditions.isIconDefined(attrs, i, icons); }
        });
    }

    static boolean matchesRaw(KeyboardParams params, final XmlPullParser parser) {
        return matches(params, new Values() {
            private String value(int index) {
                return CustomLayoutParser.getAttributeValue(parser, attributeName(index));
            }
            public boolean matchString(int i, String v) { return matchTokens(value(i), v, null); }
            public boolean matchTypedValue(int i, int v, String s) { return matchTokens(value(i), s, v); }
            public boolean matchBoolean(int i, boolean v) {
                String raw = value(i);
                return raw == null || Boolean.toString(v).equalsIgnoreCase(raw)
                        || (v ? "1" : "0").equals(raw);
            }
            public boolean matchInteger(int i, int v) {
                return matchTokens(value(i), i == R.styleable.Keyboard_Case_imeAction ? actionName(v) : null, v);
            }
            public boolean isIconDefined(int i, KeyboardIconsSet icons) {
                String raw = value(i);
                return raw == null || icons.getIconDrawable(KeyboardIconsSet.getIconId(raw)) != null;
            }
        });
    }

    private static boolean matches(KeyboardParams params, Values values) {
        final KeyboardId id = params.mId;
        if (id == null) return true;
        final boolean keyboardLayoutSetMatched = values.matchString(
                R.styleable.Keyboard_Case_keyboardLayoutSet,
                id.mSubtype.getKeyboardLayoutSetName());
        final boolean keyboardLayoutSetElementMatched = values.matchTypedValue(
                R.styleable.Keyboard_Case_keyboardLayoutSetElement, id.mElementId,
                KeyboardId.elementIdToName(id.mElementId));
        final boolean keyboardThemeMacthed = values.matchTypedValue(
                R.styleable.Keyboard_Case_keyboardTheme, params.mThemeId,
                KeyboardTheme.getKeyboardThemeName(params.mThemeId));
        final boolean modeMatched = values.matchTypedValue(
                R.styleable.Keyboard_Case_mode, id.mMode, KeyboardId.modeName(id.mMode));
        final boolean navigateNextMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_navigateNext, id.navigateNext());
        final boolean navigatePreviousMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_navigatePrevious, id.navigatePrevious());
        final boolean passwordInputMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_passwordInput, id.passwordInput());
        final boolean clobberSettingsKeyMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_clobberSettingsKey, id.mClobberSettingsKey);
        final boolean hasShortcutKeyMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_hasShortcutKey, id.mHasShortcutKey);
        final boolean numberRowEnabledMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_numberRowEnabled,
                id.mNumberRowEnabled);
        final boolean languageSwitchKeyEnabledMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_languageSwitchKeyEnabled,
                id.mLanguageSwitchKeyEnabled);
        final boolean emojiKeyEnabledMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_emojiKeyEnabled,
                id.mEmojiKeyEnabled);
        final boolean isMultiLineMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_isMultiLine, id.isMultiLine());
        final boolean imeActionMatched = values.matchInteger(
                R.styleable.Keyboard_Case_imeAction, id.imeAction());
        final boolean isIconDefinedMatched = values.isIconDefined(
                R.styleable.Keyboard_Case_isIconDefined, params.mIconsSet);
        final Locale locale = id.getLocale();
        final boolean localeCodeMatched = values.matchString(R.styleable.Keyboard_Case_localeCode, locale.toString());
        final boolean languageCodeMatched = values.matchString(R.styleable.Keyboard_Case_languageCode, locale.getLanguage());
        final boolean countryCodeMatched = values.matchString(R.styleable.Keyboard_Case_countryCode, locale.getCountry());
        // Always match default non-split XML layout so our programmatic split layout
        // can split any full keyboard layout (including V3, Russian, Custom, etc.) consistently.
        final boolean splitLayoutMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_isSplitLayout, false);
        final boolean oneHandedModeEnabledMatched = values.matchBoolean(
                R.styleable.Keyboard_Case_oneHandedModeEnabled,
                id.mOneHandedModeEnabled);
        final boolean selected = keyboardLayoutSetMatched && keyboardLayoutSetElementMatched
                && keyboardThemeMacthed && modeMatched && navigateNextMatched
                && navigatePreviousMatched && passwordInputMatched && clobberSettingsKeyMatched
                && hasShortcutKeyMatched && numberRowEnabledMatched  && languageSwitchKeyEnabledMatched
                && emojiKeyEnabledMatched && isMultiLineMatched && imeActionMatched && isIconDefinedMatched
                && localeCodeMatched && languageCodeMatched && countryCodeMatched
                && splitLayoutMatched && oneHandedModeEnabledMatched;

        return selected;
    }

    private static boolean matchInteger(final TypedArray a, final int index, final int value) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for
        // the attribute.
        return !a.hasValue(index) || a.getInt(index, 0) == value;
    }

    private static boolean matchBoolean(final TypedArray a, final int index, final boolean value) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for
        // the attribute.
        return !a.hasValue(index) || a.getBoolean(index, false) == value;
    }

    private static boolean matchString(final TypedArray a, final int index, final String value) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for
        // the attribute.
        return !a.hasValue(index)
                || StringUtils.containsInArray(value, a.getString(index).split("\\|"));
    }

    private static boolean matchTypedValue(final TypedArray a, final int index, final int intValue,
            final String strValue) {
        // If <case> does not have "index" attribute, that means this <case> is wild-card for
        // the attribute.
        final TypedValue v = a.peekValue(index);
        if (v == null) {
            return true;
        }
        if (ResourceUtils.isIntegerValue(v)) {
            return intValue == a.getInt(index, 0);
        }
        if (ResourceUtils.isStringValue(v)) {
            return StringUtils.containsInArray(strValue, a.getString(index).split("\\|"));
        }
        return false;
    }

    private static boolean isIconDefined(final TypedArray a, final int index,
            final KeyboardIconsSet iconsSet) {
        if (!a.hasValue(index)) {
            return true;
        }
        final String iconName = a.getString(index);
        final int iconId = KeyboardIconsSet.getIconId(iconName);
        return iconsSet.getIconDrawable(iconId) != null;
    }


    // Imported XML additionally accepts comma lists and negation, without guessing unknown names.
    private static boolean matchTokens(String raw, String name, Integer number) {
        if (raw == null) return true;
        boolean positive = false, matched = false;
        for (String token : raw.split("[,|]")) {
            String value = token.trim();
            boolean negative = value.startsWith("!");
            if (negative) value = value.substring(1).trim();
            if (value.isEmpty()) return false;
            if (value.startsWith("action")) value = value.substring(6);
            boolean equal = name != null && name.equalsIgnoreCase(value);
            if (number != null) {
                try { equal |= number == Integer.parseInt(value); }
                catch (NumberFormatException ignored) { }
            }
            if (negative && equal) return false;
            if (!negative) { positive = true; matched |= equal; }
        }
        return !positive || matched;
    }

    private static String actionName(int value) {
        switch (value) {
            case 1: return "none";
            case 2: return "go";
            case 3: return "search";
            case 4: return "send";
            case 5: return "next";
            case 6: return "done";
            case 7: return "previous";
            default: return "unspecified";
        }
    }

    private static String attributeName(int index) {
        if (index == R.styleable.Keyboard_Case_clobberSettingsKey) return "clobberSettingsKey";
        if (index == R.styleable.Keyboard_Case_countryCode) return "countryCode";
        if (index == R.styleable.Keyboard_Case_emojiKeyEnabled) return "emojiKeyEnabled";
        if (index == R.styleable.Keyboard_Case_hasShortcutKey) return "hasShortcutKey";
        if (index == R.styleable.Keyboard_Case_imeAction) return "imeAction";
        if (index == R.styleable.Keyboard_Case_isIconDefined) return "isIconDefined";
        if (index == R.styleable.Keyboard_Case_isMultiLine) return "isMultiLine";
        if (index == R.styleable.Keyboard_Case_isSplitLayout) return "isSplitLayout";
        if (index == R.styleable.Keyboard_Case_keyboardLayoutSet) return "keyboardLayoutSet";
        if (index == R.styleable.Keyboard_Case_keyboardLayoutSetElement) return "keyboardLayoutSetElement";
        if (index == R.styleable.Keyboard_Case_keyboardTheme) return "keyboardTheme";
        if (index == R.styleable.Keyboard_Case_languageCode) return "languageCode";
        if (index == R.styleable.Keyboard_Case_languageSwitchKeyEnabled) return "languageSwitchKeyEnabled";
        if (index == R.styleable.Keyboard_Case_localeCode) return "localeCode";
        if (index == R.styleable.Keyboard_Case_mode) return "mode";
        if (index == R.styleable.Keyboard_Case_navigateNext) return "navigateNext";
        if (index == R.styleable.Keyboard_Case_navigatePrevious) return "navigatePrevious";
        if (index == R.styleable.Keyboard_Case_numberRowEnabled) return "numberRowEnabled";
        if (index == R.styleable.Keyboard_Case_oneHandedModeEnabled) return "oneHandedModeEnabled";
        if (index == R.styleable.Keyboard_Case_passwordInput) return "passwordInput";
        throw new IllegalArgumentException("Unknown case attribute " + index);
    }
}
