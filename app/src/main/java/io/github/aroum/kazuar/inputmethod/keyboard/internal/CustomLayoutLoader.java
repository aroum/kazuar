package io.github.aroum.kazuar.inputmethod.keyboard.internal;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import io.github.aroum.kazuar.inputmethod.keyboard.Key;
import org.xmlpull.v1.XmlPullParser;
import java.io.File;
import java.io.FileInputStream;

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

    public static boolean tryLoadCustomLayout(Context context, KeyboardParams params, File file) {
        if (file == null || !file.exists()) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");
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

    public static void validateStructure(String xml) throws Exception {
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
            throw new IllegalArgumentException("DTD and entities are not allowed in layouts");
        }
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(xml));
        int keys = 0;
        int rows = 0;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) continue;
            if (parser.getDepth() > 32) throw new org.xmlpull.v1.XmlPullParserException("Layout nesting exceeds 32", parser, null);
            if (parser.getDepth() == 1 && !"Keyboard".equalsIgnoreCase(parser.getName())
                    && !"merge".equalsIgnoreCase(parser.getName())) {
                throw new org.xmlpull.v1.XmlPullParserException("Expected Keyboard or merge root", parser, null);
            }
            if ("Row".equalsIgnoreCase(parser.getName()) && ++rows > 64)
                throw new org.xmlpull.v1.XmlPullParserException("Too many layout rows", parser, null);
            if (("Key".equalsIgnoreCase(parser.getName()) || "Spacer".equalsIgnoreCase(parser.getName())) && ++keys > 1024)
                throw new org.xmlpull.v1.XmlPullParserException("Too many layout keys", parser, null);
            String width = CustomLayoutParser.getAttributeValue(parser, "keyWidth", "width");
            if (width != null) {
                float value = CustomLayoutParser.parsePercent(width, Float.NaN);
                boolean spacer = "Spacer".equalsIgnoreCase(parser.getName());
                if (Float.isNaN(value) || Float.isInfinite(value) || value < 0 || (!spacer && value == 0) || value > 1)
                    throw new org.xmlpull.v1.XmlPullParserException("Invalid key width: " + width, parser, null);
            }
        }
    }

    public static void loadCustomLayoutOrThrow(Context context, KeyboardParams params, String xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(xml));
        if (!loadCustomLayout(context, params, parser)) {
            throw new org.xmlpull.v1.XmlPullParserException("Layout has no active rows", parser, null);
        }
        boolean hasKey = false;
        for (Key key : params.mSortedKeys) if (!key.isSpacer()) hasKey = true;
        if (!hasKey) throw new org.xmlpull.v1.XmlPullParserException("Layout has no active keys", parser, null);
    }

    private static boolean loadCustomLayout(Context context, KeyboardParams params, XmlPullParser parser) throws Exception {
        return CustomLayoutKeyBuilder.build(params, CustomLayoutParser.parse(context, params, parser));
    }
}
