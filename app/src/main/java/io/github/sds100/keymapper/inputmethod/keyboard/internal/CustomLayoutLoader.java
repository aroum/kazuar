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
                            spec.label = getAttributeValue(parser, "keyLabel");
                            spec.codes = getAttributeValue(parser, "codes");
                            spec.keyWidth = getAttributeValue(parser, "keyWidth");
                            spec.keyIcon = getAttributeValue(parser, "keyIcon");
                            spec.longCode = getAttributeValue(parser, "longCode");
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
                    int code = Constants.CODE_UNSPECIFIED;

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

                    if (spec.codes != null) {
                        try {
                            code = Integer.parseInt(spec.codes);
                        } catch (NumberFormatException e) {
                            code = Constants.CODE_UNSPECIFIED;
                        }
                    } else if (label != null && !label.isEmpty()) {
                        if ("⌫".equals(label)) {
                            code = Constants.CODE_DELETE;
                        } else {
                            code = label.codePointAt(0);
                        }
                    }

                    int iconId = KeyboardIconsSet.ICON_UNDEFINED;
                    if (spec.keyIcon != null) {
                        String iconName = spec.keyIcon.toLowerCase(java.util.Locale.ROOT);
                        if (iconName.contains("space")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SPACE_KEY);
                        } else if (iconName.contains("return") || iconName.contains("enter")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_ENTER_KEY);
                        } else if (iconName.equalsIgnoreCase("shift")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SHIFT_KEY);
                            code = Constants.CODE_SHIFT;
                        } else if (iconName.contains("delete") || iconName.contains("backspace")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_DELETE_KEY);
                            code = Constants.CODE_DELETE;
                        } else if (iconName.contains("settings")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_SETTINGS_KEY);
                            code = Constants.CODE_SETTINGS;
                        } else if (iconName.contains("globe") || iconName.contains("language")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_LANGUAGE_SWITCH_KEY);
                            code = Constants.CODE_LANGUAGE_SWITCH;
                        } else if (iconName.contains("emoji")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY);
                            code = Constants.CODE_EMOJI;
                        } else if (iconName.contains("clipboard")) {
                            iconId = KeyboardIconsSet.getIconId(KeyboardIconsSet.NAME_CLIPBOARD_NORMAL_KEY);
                            code = Constants.CODE_CLIPBOARD;
                        }
                    }

                    int labelFlags = 0;
                    int backgroundType = Key.BACKGROUND_TYPE_NORMAL;
                    if (code == Constants.CODE_SPACE) {
                        backgroundType = Key.BACKGROUND_TYPE_SPACEBAR;
                    } else if (code == Constants.CODE_DELETE || code == Constants.CODE_SHIFT || code == 10) {
                        backgroundType = Key.BACKGROUND_TYPE_FUNCTIONAL;
                    }

                    int hGap = params.mHorizontalGap;
                    int vGap = params.mVerticalGap;

                    Key key = new Key(label, iconId, code, null, hint, labelFlags, backgroundType, x, y, w, rowHeight, hGap, vGap);
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

    private static String getAttributeValue(XmlPullParser parser, String attrName) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (attrName.equalsIgnoreCase(parser.getAttributeName(i))) {
                return parser.getAttributeValue(i);
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
    }
}
