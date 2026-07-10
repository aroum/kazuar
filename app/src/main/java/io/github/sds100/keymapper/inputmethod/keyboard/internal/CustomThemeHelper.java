package io.github.sds100.keymapper.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import org.json.JSONObject;

public class CustomThemeHelper {
    private static final String TAG = "CustomThemeHelper";

    public static class ThemeColors {
        public int keyboardBackground;
        public int keyBackground;
        public int keyBackgroundPressed;
        public int keyTextColor;
        public int functionalKeyBackground;
        public int functionalKeyBackgroundPressed;
        public int functionalKeyTextColor;
        public int keyHintColor;
        public int keyBorderColor;
    }

    public static ThemeColors getCustomThemeColors(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        String themeJson = prefs.getString("pref_custom_theme_json", null);
        if (themeJson == null) return null;
        try {
            JSONObject obj = new JSONObject(themeJson);
            int currentNightMode = context.getResources().getConfiguration().uiMode 
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            boolean isDark = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
            String modeKey = isDark ? "dark" : "light";
            if (!obj.has(modeKey)) return null;
            JSONObject modeObj = obj.getJSONObject(modeKey);
            ThemeColors colors = new ThemeColors();
            colors.keyboardBackground = Color.parseColor(modeObj.getString("keyboard_background"));
            colors.keyBackground = Color.parseColor(modeObj.getString("key_background"));
            colors.keyBackgroundPressed = Color.parseColor(modeObj.getString("key_background_pressed"));
            colors.keyTextColor = Color.parseColor(modeObj.getString("key_text_color"));
            colors.functionalKeyBackground = Color.parseColor(modeObj.getString("functional_key_background"));
            colors.functionalKeyBackgroundPressed = Color.parseColor(modeObj.getString("functional_key_background_pressed"));
            colors.functionalKeyTextColor = Color.parseColor(modeObj.getString("functional_key_text_color"));
            colors.keyHintColor = Color.parseColor(modeObj.getString("key_hint_color"));
            colors.keyBorderColor = Color.parseColor(modeObj.getString("key_border_color"));
            return colors;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse custom theme JSON", e);
            return null;
        }
    }
}
