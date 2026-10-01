package io.github.sds100.keymapper.inputmethod.keyboard.internal;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import io.github.sds100.keymapper.inputmethod.keyboard.Key;
import io.github.sds100.keymapper.inputmethod.keyboard.KeyboardId;
import io.github.sds100.keymapper.inputmethod.latin.common.Constants;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public class CustomLayoutLoader {
    private static final String TAG = "CustomLayoutLoader";

    public static class CustomReplaceRule {
        public final String from;
        public final String to;
        public CustomReplaceRule(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    private static ArrayList<CustomReplaceRule> sReplaceRules = new ArrayList<>();

    public static synchronized void setReplaceRules(ArrayList<CustomReplaceRule> rules) {
        sReplaceRules = rules;
    }

    public static synchronized ArrayList<CustomReplaceRule> getReplaceRules() {
        return sReplaceRules;
    }

    public static boolean tryLoadCustomLayout(Context context, KeyboardParams params, File file) {
        try {
            if (!file.exists()) return false;

            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new FileInputStream(file), "UTF-8");
            return loadCustomLayout(context, params, parser);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom layout from file", e);
            return false;
        }
    }

    public static boolean tryLoadCustomLayoutFromString(Context context, KeyboardParams params, String xmlContent) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new java.io.StringReader(xmlContent));
            return loadCustomLayout(context, params, parser);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom layout from string", e);
            return false;
        }
    }

    private static boolean loadCustomLayout(Context context, KeyboardParams params, XmlPullParser parser) {
        try {
            params.clearKeys();

            int eventType = parser.getEventType();
            int currentRow = -1;

            int baseWidth = params.mBaseWidth;
            int baseHeight = params.mBaseHeight;

            int totalRows = 4;
            int rowHeight = baseHeight / totalRows;

            float defaultKeyWidthPercent = 0.10f;

            ArrayList<ArrayList<CustomKeySpec>> rows = new ArrayList<>();
            ArrayList<CustomReplaceRule> replaceRules = new ArrayList<>();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("Keyboard".equalsIgnoreCase(tagName)) {
                        String keyWidthAttr = getAttributeValue(parser, "keyWidth");
                        if (keyWidthAttr != null) {
                            defaultKeyWidthPercent = parsePercent(keyWidthAttr, 0.10f);
                        }
                    } else if ("Row".equalsIgnoreCase(tagName)) {
                        currentRow++;
                        rows.add(new ArrayList<CustomKeySpec>());
                    } else if ("Key".equalsIgnoreCase(tagName)) {
                        if (currentRow >= 0 && currentRow < rows.size()) {
                            CustomKeySpec spec = new CustomKeySpec();
                            spec.label = getAttributeValue(parser, "keyLabel", "label", "keySpec");
                            spec.codes = getAttributeValue(parser, "codes", "code");
                            spec.keyWidth = getAttributeValue(parser, "keyWidth", "width");
                            spec.keyIcon = getAttributeValue(parser, "keyIcon", "icon");
                            spec.longCode = getAttributeValue(parser, "longCode");
                            spec.moreKeys = getAttributeValue(parser, "moreKeys");
                            spec.backgroundType = getAttributeValue(parser, "backgroundType");
                            rows.get(currentRow).add(spec);
                        }
                    } else if ("Replace".equalsIgnoreCase(tagName)) {
                        String from = getAttributeValue(parser, "from");
                        String to = getAttributeValue(parser, "to");
                        if (from != null && to != null) {
                            replaceRules.add(new CustomReplaceRule(from, to));
                        }
                    }
                }
                eventType = parser.next();
            }

            if (rows.isEmpty()) return false;

            totalRows = rows.size();
            rowHeight = baseHeight / totalRows;

            for (int r = 0; r < totalRows; r++) {
                ArrayList<CustomKeySpec> rowKeys = rows.get(r);
                int y = params.mTopPadding + r * rowHeight;
                int x = params.mLeftPadding;

                for (int i = 0; i < rowKeys.size(); i++) {
                    CustomKeySpec spec = rowKeys.get(i);
                    float wPercent = defaultKeyWidthPercent;
                    if (spec.keyWidth != null) {
                        wPercent = parsePercent(spec.keyWidth, defaultKeyWidthPercent);
                    }
                    int w = Math.round(wPercent * baseWidth);

                    String label = spec.label;
                    String hint = null;
                    String specCodeStr = spec.codes;
                    String specIconStr = spec.keyIcon;

                    // Support composite specs like "!icon/language_switch_key|!code/key_language_switch"
                    if (label != null && label.contains("|")) {
                        String[] parts = label.split("\\|");
                        for (String part : parts) {
                            String p = part.trim();
                            if (p.startsWith(KeyboardIconsSet.PREFIX_ICON) || p.contains("icon")) {
                                if (specIconStr == null) specIconStr = p;
                            } else if (p.startsWith(KeyboardCodesSet.PREFIX_CODE) || p.contains("code")) {
                                if (specCodeStr == null) specCodeStr = p;
                            } else if (label.equals(spec.label)) {
                                label = p;
                            }
                        }
                    }

                    if (label != null) {
                        int nlIndex = label.indexOf("\\n");
                        if (nlIndex >= 0) {
                            hint = label.substring(0, nlIndex);
                            label = label.substring(nlIndex + 2);
                        } else {
                            nlIndex = label.indexOf("\n");
                            if (nlIndex >= 0) {
                                hint = label.substring(0, nlIndex);
                                label = label.substring(nlIndex + 1);
                            }
                        }
                    }

                    int code = resolveCode(specCodeStr);
                    int iconId = resolveIcon(specIconStr);

                    // Derive code from icon if not explicitly set
                    if (code == Constants.CODE_UNSPECIFIED && iconId != KeyboardIconsSet.ICON_UNDEFINED) {
                        if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY)) {
                            code = Constants.CODE_LANGUAGE_SWITCH;
                        } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY)
                                || iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_ACTION_KEY)) {
                            code = Constants.CODE_EMOJI;
                        } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY)
                                || iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_ACTION_KEY)) {
                            code = Constants.CODE_CLIPBOARD;
                        } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY)) {
                            code = Constants.CODE_SETTINGS;
                        } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY)) {
                            code = Constants.CODE_DELETE;
                        } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY)) {
                            code = Constants.CODE_SHIFT;
                        } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY)) {
                            code = Constants.CODE_SPACE;
                        } else if (iconId == KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY)) {
                            code = Constants.CODE_ENTER;
                        }
                    }

                    // Derive code from label if still unspecified
                    if (code == Constants.CODE_UNSPECIFIED && label != null && !label.isEmpty()) {
                        if ("⌫".equals(label)) {
                            code = Constants.CODE_DELETE;
                        } else {
                            code = label.codePointAt(0);
                        }
                    }

                    // Assign standard icon if code is known and key has no text label or custom icon
                    if (iconId == KeyboardIconsSet.ICON_UNDEFINED && (label == null || label.isEmpty())) {
                        if (code == Constants.CODE_LANGUAGE_SWITCH) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY);
                        } else if (code == Constants.CODE_EMOJI) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY);
                        } else if (code == Constants.CODE_CLIPBOARD) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY);
                        } else if (code == Constants.CODE_SETTINGS) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY);
                        } else if (code == Constants.CODE_DELETE) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY);
                        } else if (code == Constants.CODE_SHIFT) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY);
                        } else if (code == Constants.CODE_SPACE) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY);
                        } else if (code == Constants.CODE_ENTER) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
                        }
                    }

                    int labelFlags = 0;
                    int backgroundType = Key.BACKGROUND_TYPE_NORMAL;
                    if (spec.backgroundType != null) {
                        String bg = spec.backgroundType.trim().toLowerCase(java.util.Locale.ROOT);
                        if ("functional".equals(bg)) {
                            backgroundType = Key.BACKGROUND_TYPE_FUNCTIONAL;
                        } else if ("spacebar".equals(bg)) {
                            backgroundType = Key.BACKGROUND_TYPE_SPACEBAR;
                        } else if ("normal".equals(bg)) {
                            backgroundType = Key.BACKGROUND_TYPE_NORMAL;
                        }
                    } else {
                        if (code == Constants.CODE_SPACE) {
                            backgroundType = Key.BACKGROUND_TYPE_SPACEBAR;
                        } else if (code == Constants.CODE_DELETE || code == Constants.CODE_SHIFT
                                || code == Constants.CODE_ENTER || code == Constants.CODE_LANGUAGE_SWITCH
                                || code == Constants.CODE_EMOJI || code == Constants.CODE_CLIPBOARD
                                || code == Constants.CODE_SETTINGS || code == Constants.CODE_SWITCH_ALPHA_SYMBOL
                                || code == Constants.CODE_CAPSLOCK || iconId != KeyboardIconsSet.ICON_UNDEFINED) {
                            backgroundType = Key.BACKGROUND_TYPE_FUNCTIONAL;
                        }
                    }

                    String moreKeySpecs = null;
                    if (spec.moreKeys != null && !spec.moreKeys.trim().isEmpty()) {
                        moreKeySpecs = spec.moreKeys.trim();
                    } else if (spec.longCode != null && !spec.longCode.trim().isEmpty()) {
                        String lc = spec.longCode.trim();
                        String prefix = (hint != null && !hint.trim().isEmpty()) ? hint.trim() : (label != null ? label : " ");
                        if (lc.contains("|")) {
                            moreKeySpecs = "!noPanelAutoMoreKey!," + lc;
                        } else {
                            moreKeySpecs = "!noPanelAutoMoreKey!," + prefix + "|" + lc;
                        }
                    } else if (hint != null && !hint.trim().isEmpty()) {
                        moreKeySpecs = "!noPanelAutoMoreKey!," + hint.trim();
                    }

                    Key key = new Key(label, iconId, code, null, hint, moreKeySpecs, labelFlags, backgroundType, x, y, w, rowHeight, params);
                    params.onAddKey(key);

                    x += w;
                }
            }

            setReplaceRules(replaceRules);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse layout XML", e);
            return false;
        }
    }

    private static int resolveCode(String specCode) {
        if (specCode == null) return Constants.CODE_UNSPECIFIED;
        String trimmed = specCode.trim();
        if (trimmed.isEmpty()) return Constants.CODE_UNSPECIFIED;

        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {}

        if (trimmed.startsWith(KeyboardCodesSet.PREFIX_CODE)) {
            trimmed = trimmed.substring(KeyboardCodesSet.PREFIX_CODE.length());
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        switch (lower) {
            case "language_switch":
            case "language":
            case "lang":
            case "globe":
            case "key_language_switch":
                return Constants.CODE_LANGUAGE_SWITCH;
            case "emoji":
            case "key_emoji":
            case "emoji_normal":
            case "emoji_action":
                return Constants.CODE_EMOJI;
            case "clipboard":
            case "key_clipboard":
            case "clipboard_normal":
            case "clipboard_action":
                return Constants.CODE_CLIPBOARD;
            case "shift":
            case "key_shift":
                return Constants.CODE_SHIFT;
            case "capslock":
            case "key_capslock":
                return Constants.CODE_CAPSLOCK;
            case "switch_alpha_symbol":
            case "key_switch_alpha_symbol":
            case "symbols":
            case "symbol":
                return Constants.CODE_SWITCH_ALPHA_SYMBOL;
            case "delete":
            case "backspace":
            case "key_delete":
                return Constants.CODE_DELETE;
            case "settings":
            case "key_settings":
                return Constants.CODE_SETTINGS;
            case "shortcut":
            case "key_shortcut":
                return Constants.CODE_SHORTCUT;
            case "action_next":
            case "key_action_next":
                return Constants.CODE_ACTION_NEXT;
            case "action_previous":
            case "key_action_previous":
                return Constants.CODE_ACTION_PREVIOUS;
            case "shift_enter":
            case "key_shift_enter":
                return Constants.CODE_SHIFT_ENTER;
            case "alpha_from_emoji":
            case "key_alpha_from_emoji":
                return Constants.CODE_ALPHA_FROM_EMOJI;
            case "alpha_from_clipboard":
            case "key_alpha_from_clipboard":
                return Constants.CODE_ALPHA_FROM_CLIPBOARD;
            case "enter":
            case "return":
            case "key_enter":
                return Constants.CODE_ENTER;
            case "space":
            case "spc":
            case "key_space":
                return Constants.CODE_SPACE;
            case "tab":
            case "key_tab":
                return Constants.CODE_TAB;
            case "copy":
            case "key_copy":
                return Constants.CODE_COPY;
            case "paste":
            case "key_paste":
                return Constants.CODE_PASTE;
            case "cut":
            case "key_cut":
                return Constants.CODE_CUT;
            case "undo":
            case "key_undo":
                return Constants.CODE_UNDO;
            case "redo":
            case "key_redo":
                return Constants.CODE_REDO;
            case "select_all":
            case "key_select_all":
                return Constants.CODE_SELECT_ALL;
            case "select_toggle":
            case "key_select_toggle":
                return Constants.CODE_SELECT_TOGGLE;
            case "delete_word":
            case "key_delete_word":
                return Constants.CODE_DELETE_WORD;
            case "forward_delete":
            case "key_forward_delete":
                return Constants.CODE_FORWARD_DELETE;
            case "switch_to_editing":
            case "key_switch_to_editing":
                return Constants.CODE_SWITCH_TO_EDITING;
            case "arrow_left":
            case "key_arrow_left":
                return Constants.CODE_ARROW_LEFT;
            case "arrow_right":
            case "key_arrow_right":
                return Constants.CODE_ARROW_RIGHT;
            case "arrow_up":
            case "key_arrow_up":
                return Constants.CODE_ARROW_UP;
            case "arrow_down":
            case "key_arrow_down":
                return Constants.CODE_ARROW_DOWN;
            case "move_home":
            case "key_move_home":
                return Constants.CODE_MOVE_HOME;
            case "move_end":
            case "key_move_end":
                return Constants.CODE_MOVE_END;
            case "page_up":
            case "key_page_up":
                return Constants.CODE_PAGE_UP;
            case "page_down":
            case "key_page_down":
                return Constants.CODE_PAGE_DOWN;
            default:
                try {
                    return KeyboardCodesSet.getCode(lower);
                } catch (Exception ignored) {
                    return Constants.CODE_UNSPECIFIED;
                }
        }
    }

    private static int resolveIcon(String specIcon) {
        if (specIcon == null) return KeyboardIconsSet.ICON_UNDEFINED;
        String trimmed = specIcon.trim();
        if (trimmed.isEmpty()) return KeyboardIconsSet.ICON_UNDEFINED;
        if (trimmed.startsWith(KeyboardIconsSet.PREFIX_ICON)) {
            trimmed = trimmed.substring(KeyboardIconsSet.PREFIX_ICON.length());
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("space")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY);
        } else if (lower.contains("return") || lower.contains("enter")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
        } else if (lower.contains("shift")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY);
        } else if (lower.contains("delete") || lower.contains("backspace")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY);
        } else if (lower.contains("settings")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY);
        } else if (lower.contains("globe") || lower.contains("language")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY);
        } else if (lower.contains("emoji")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY);
        } else if (lower.contains("clipboard")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY);
        } else if (lower.contains("tab")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_TAB_KEY);
        } else if (lower.contains("shortcut")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHORTCUT_KEY);
        } else if (lower.contains("incognito")) {
            return KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_INCOGNITO_KEY);
        }
        try {
            return KeyboardIconsSet.getIconId(lower);
        } catch (Exception ignored) {
            return KeyboardIconsSet.ICON_UNDEFINED;
        }
    }

    private static String getAttributeValue(XmlPullParser parser, String... attrNames) {
        final int count = parser.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String rawName = parser.getAttributeName(i);
            final String name = rawName.contains(":") ? rawName.substring(rawName.indexOf(':') + 1) : rawName;
            for (String attr : attrNames) {
                if (attr.equalsIgnoreCase(name) || attr.equalsIgnoreCase(rawName)) {
                    return parser.getAttributeValue(i);
                }
            }
        }
        return null;
    }

    private static float parsePercent(String val, float defaultVal) {
        try {
            if (val.endsWith("%p")) {
                return Float.parseFloat(val.substring(0, val.length() - 2)) / 100.0f;
            } else if (val.endsWith("%")) {
                return Float.parseFloat(val.substring(0, val.length() - 1)) / 100.0f;
            }
            return Float.parseFloat(val) / 100.0f;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static class CustomKeySpec {
        String label;
        String codes;
        String keyWidth;
        String keyIcon;
        String longCode;
        String moreKeys;
        String backgroundType;
    }
}
