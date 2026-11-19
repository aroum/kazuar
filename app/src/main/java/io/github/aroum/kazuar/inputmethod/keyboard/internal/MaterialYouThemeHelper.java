/*
 * Copyright (C) 2026 The kazuar Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.aroum.kazuar.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Configuration;

import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardTheme;

/**
 * Resolves the Material You dynamic color palette into the colors used by the keyboard renderer.
 *
 * <p>Android exposes the wallpaper-derived Material You palettes as
 * {@code android.R.color.system_*} tonal resources. Lower tonal values are lighter and higher
 * tonal values are darker, so the light and dark variants pick opposite ends of each palette.</p>
 */
public final class MaterialYouThemeHelper {
    private MaterialYouThemeHelper() {
    }

    /**
     * Returns the Material You colors for the given theme id, or {@code null} when the theme is
     * not a Material You theme.
     */
    public static CustomThemeHelper.ThemeColors getThemeColors(final Context context,
            final int themeId) {
        if (!KeyboardTheme.isMaterialYou(themeId)) {
            return null;
        }
        return isDark(context, themeId) ? getDarkColors(context) : getLightColors(context);
    }

    public static boolean isDark(final Context context, final int themeId) {
        if (KeyboardTheme.getIsDayNight(themeId)) {
            final int nightMode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES;
        }
        return KeyboardTheme.THEME_VARIANT_DARK.equals(KeyboardTheme.getThemeVariant(themeId));
    }

    private static CustomThemeHelper.ThemeColors getLightColors(final Context context) {
        final CustomThemeHelper.ThemeColors colors = new CustomThemeHelper.ThemeColors();
        colors.keyboardBackground = context.getColor(android.R.color.system_neutral1_10);
        colors.keyBackground = context.getColor(android.R.color.system_neutral1_100);
        colors.keyBackgroundPressed = context.getColor(android.R.color.system_neutral1_200);
        colors.keyTextColor = context.getColor(android.R.color.system_neutral1_900);
        colors.functionalKeyBackground = context.getColor(android.R.color.system_accent1_200);
        colors.functionalKeyBackgroundPressed = context.getColor(android.R.color.system_accent1_300);
        colors.functionalKeyTextColor = context.getColor(android.R.color.system_accent1_900);
        colors.keyHintColor = context.getColor(android.R.color.system_neutral2_600);
        colors.keyDoubleTapHintColor = context.getColor(android.R.color.system_accent1_700);
        colors.keyBorderColor = context.getColor(android.R.color.system_neutral2_300);
        return colors;
    }

    private static CustomThemeHelper.ThemeColors getDarkColors(final Context context) {
        final CustomThemeHelper.ThemeColors colors = new CustomThemeHelper.ThemeColors();
        colors.keyboardBackground = context.getColor(android.R.color.system_neutral1_900);
        colors.keyBackground = context.getColor(android.R.color.system_neutral1_800);
        colors.keyBackgroundPressed = context.getColor(android.R.color.system_neutral1_700);
        colors.keyTextColor = context.getColor(android.R.color.system_neutral1_10);
        colors.functionalKeyBackground = context.getColor(android.R.color.system_accent1_700);
        colors.functionalKeyBackgroundPressed = context.getColor(android.R.color.system_accent1_600);
        colors.functionalKeyTextColor = context.getColor(android.R.color.system_accent1_100);
        colors.keyHintColor = context.getColor(android.R.color.system_neutral2_300);
        colors.keyDoubleTapHintColor = context.getColor(android.R.color.system_accent1_300);
        colors.keyBorderColor = context.getColor(android.R.color.system_neutral2_700);
        return colors;
    }
}
