package io.github.aroum.kazuar.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Xml;
import io.github.aroum.kazuar.inputmethod.keyboard.Key;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** Parses uncompiled layout XML; geometry and key construction are separate. */
final class CustomLayoutParser {
    static final class Layout {
        final ArrayList<ArrayList<CustomKeySpec>> rows;
        final ArrayList<Float> widths;
        Layout(ArrayList<ArrayList<CustomKeySpec>> rows, ArrayList<Float> widths) {
            this.rows = rows;
            this.widths = widths;
        }
    }
    static Layout parse(Context context, KeyboardParams params, XmlPullParser parser) throws Exception {
        int eventType = parser.getEventType();
        int currentRow = -1;

        float defaultKeyWidthPercent = 0.10f;

        ArrayList<ArrayList<CustomKeySpec>> rows = new ArrayList<>();
        ArrayList<Float> rowDefaultWidths = new ArrayList<>();
        ArrayDeque<SwitchState> switchStack = new ArrayDeque<>();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                if ("switch".equalsIgnoreCase(tagName)) {
                    SwitchState newState = new SwitchState();
                    newState.isBranchActive = false;
                    switchStack.push(newState);
                } else if ("case".equalsIgnoreCase(tagName)) {
                    SwitchState state = switchStack.peek();
                    if (state != null) {
                        boolean parentActive = isParentActive(switchStack);
                        if (parentActive && !state.conditionMatched && KeyboardCaseConditions.matchesRaw(params, parser) && matchesOrientation(getAttributeValue(parser, "orientation"), context)) {
                            state.conditionMatched = true;
                            state.isBranchActive = true;
                        } else {
                            state.isBranchActive = false;
                        }
                    }
                } else if ("default".equalsIgnoreCase(tagName)) {
                    SwitchState state = switchStack.peek();
                    if (state != null) {
                        boolean parentActive = isParentActive(switchStack);
                        if (parentActive && !state.conditionMatched) {
                            state.conditionMatched = true;
                            state.isBranchActive = true;
                        } else {
                            state.isBranchActive = false;
                        }
                    }
                } else if (isCurrentActive(switchStack)) {
                    if ("Keyboard".equalsIgnoreCase(tagName) || "merge".equalsIgnoreCase(tagName)) {
                        String keyWidthAttr = getAttributeValue(parser, "keyWidth");
                        if (keyWidthAttr != null) {
                            defaultKeyWidthPercent = parsePercent(keyWidthAttr, 0.10f);
                        }
                    } else if ("Row".equalsIgnoreCase(tagName)) {
                        currentRow = rows.size();
                        rows.add(new ArrayList<CustomKeySpec>());
                        float rowWidth = defaultKeyWidthPercent;
                        String rowKeyWidthAttr = getAttributeValue(parser, "keyWidth");
                        if (rowKeyWidthAttr != null) {
                            rowWidth = parsePercent(rowKeyWidthAttr, defaultKeyWidthPercent);
                        }
                        rowDefaultWidths.add(rowWidth);
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
                            spec.hint = getAttributeValue(parser, "keyHintLabel", "hintLabel", "hint");
                            spec.keyStyle = getAttributeValue(parser, "keyStyle", "style");
                            spec.keyLabelFlags = getAttributeValue(parser, "keyLabelFlags", "labelFlags");
                            spec.actionKey = getAttributeValue(parser, "actionKey", "dynamicAction");
                            if (KeyboardCaseConditions.matchesRaw(params, parser)) {
                                rows.get(currentRow).add(spec);
                            }
                        }
                    } else if ("Spacer".equalsIgnoreCase(tagName)) {
                        if (currentRow >= 0 && currentRow < rows.size()) {
                            CustomKeySpec spec = new CustomKeySpec();
                            spec.isSpacer = true;
                            spec.keyWidth = getAttributeValue(parser, "keyWidth", "width");
                            if (KeyboardCaseConditions.matchesRaw(params, parser)) {
                                rows.get(currentRow).add(spec);
                            }
                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String tagName = parser.getName();
                if ("Row".equalsIgnoreCase(tagName)) {
                    currentRow = -1;
                } else if ("switch".equalsIgnoreCase(tagName)) {
                    if (!switchStack.isEmpty()) {
                        switchStack.pop();
                    }
                } else if ("case".equalsIgnoreCase(tagName) || "default".equalsIgnoreCase(tagName)) {
                    SwitchState state = switchStack.peek();
                    if (state != null) {
                        state.isBranchActive = false;
                    }
                }
            }
            eventType = parser.next();
        }

        return new Layout(rows, rowDefaultWidths);
    }

    static String getAttributeValue(XmlPullParser parser, String... attrNames) {
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

    static float parsePercent(String val, float defaultVal) {
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

    private static class SwitchState {
        boolean conditionMatched = false;
        boolean isBranchActive = false;
    }

    private static boolean isParentActive(ArrayDeque<SwitchState> switchStack) {
        int idx = 0;
        for (SwitchState s : switchStack) {
            if (idx > 0) {
                if (!s.isBranchActive) return false;
            }
            idx++;
        }
        return true;
    }

    private static boolean isCurrentActive(ArrayDeque<SwitchState> switchStack) {
        for (SwitchState s : switchStack) {
            if (!s.isBranchActive) return false;
        }
        return true;
    }

    private static boolean matchesOrientation(String orientSpec, Context context) {
        if (context == null || orientSpec == null) return true;
        int orientation = context.getResources().getConfiguration().orientation;
        boolean isLandscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        if ("landscape".equalsIgnoreCase(orientSpec) || "land".equalsIgnoreCase(orientSpec)) return isLandscape;
        if ("portrait".equalsIgnoreCase(orientSpec) || "port".equalsIgnoreCase(orientSpec)) return !isLandscape;
        return true;
    }

    static class CustomKeySpec {
        String label;
        String codes;
        String keyWidth;
        String keyIcon;
        String longCode;
        String moreKeys;
        String backgroundType;
        String hint;
        String keyStyle;
        String keyLabelFlags;
        String actionKey;
        boolean isSpacer;
    }
}
