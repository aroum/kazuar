// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeMaterialYouTest {
    @Test
    fun materialYouFamilyAndVariants() {
        assertEquals(KeyboardTheme.THEME_FAMILY_MATERIAL_YOU,
                KeyboardTheme.getThemeFamily(KeyboardTheme.THEME_ID_MATERIAL_YOU_LIGHT))
        assertEquals(KeyboardTheme.THEME_FAMILY_MATERIAL_YOU,
                KeyboardTheme.getThemeFamily(KeyboardTheme.THEME_ID_MATERIAL_YOU_AUTO_BORDER))
        assertEquals(KeyboardTheme.THEME_VARIANT_LIGHT,
                KeyboardTheme.getThemeVariant(KeyboardTheme.THEME_ID_MATERIAL_YOU_LIGHT_BORDER))
        assertEquals(KeyboardTheme.THEME_VARIANT_DARK,
                KeyboardTheme.getThemeVariant(KeyboardTheme.THEME_ID_MATERIAL_YOU_DARK_BORDER))
        assertNull(KeyboardTheme.getThemeVariant(KeyboardTheme.THEME_ID_MATERIAL_YOU_AUTO))
        assertTrue(KeyboardTheme.isMaterialYou(KeyboardTheme.THEME_ID_MATERIAL_YOU_DARK))
        assertTrue(KeyboardTheme.isDynamicTheme(KeyboardTheme.THEME_ID_MATERIAL_YOU_AUTO))
        assertFalse(KeyboardTheme.isDynamicTheme(KeyboardTheme.THEME_ID_LXX_LIGHT))
    }

    @Test
    fun materialYouKeyBorderAndDayNightFlags() {
        assertFalse(KeyboardTheme.getHasKeyBorders(KeyboardTheme.THEME_ID_MATERIAL_YOU_LIGHT))
        assertTrue(KeyboardTheme.getHasKeyBorders(
                KeyboardTheme.THEME_ID_MATERIAL_YOU_LIGHT_BORDER))
        assertFalse(KeyboardTheme.getIsDayNight(KeyboardTheme.THEME_ID_MATERIAL_YOU_DARK))
        assertTrue(KeyboardTheme.getIsDayNight(KeyboardTheme.THEME_ID_MATERIAL_YOU_AUTO))
        assertTrue(KeyboardTheme.getIsDayNight(
                KeyboardTheme.THEME_ID_MATERIAL_YOU_AUTO_BORDER))
    }

    @Test
    fun materialYouThemeParametersMapToThemeIds() {
        assertEquals(KeyboardTheme.THEME_ID_MATERIAL_YOU_LIGHT,
                KeyboardTheme.getThemeForParameters(
                        KeyboardTheme.THEME_FAMILY_MATERIAL_YOU,
                        KeyboardTheme.THEME_VARIANT_LIGHT,
                        false, false, false))
        assertEquals(KeyboardTheme.THEME_ID_MATERIAL_YOU_DARK_BORDER,
                KeyboardTheme.getThemeForParameters(
                        KeyboardTheme.THEME_FAMILY_MATERIAL_YOU,
                        KeyboardTheme.THEME_VARIANT_DARK,
                        true, false, false))
        assertEquals(KeyboardTheme.THEME_ID_MATERIAL_YOU_AUTO,
                KeyboardTheme.getThemeForParameters(
                        KeyboardTheme.THEME_FAMILY_MATERIAL_YOU,
                        KeyboardTheme.THEME_VARIANT_LIGHT,
                        false, true, false))
        assertEquals(KeyboardTheme.THEME_ID_MATERIAL_YOU_AUTO_BORDER,
                KeyboardTheme.getThemeForParameters(
                        KeyboardTheme.THEME_FAMILY_MATERIAL_YOU,
                        KeyboardTheme.THEME_VARIANT_DARK,
                        true, true, false))
    }
}
