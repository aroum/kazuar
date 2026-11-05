// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.Context
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomLayoutImportTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = DeviceProtectedUtils.getSharedPreferences(context)
    private fun import(target: String, rules: String = "", xml: String = """<Keyboard><Row><Key keySpec="a"/></Row>$rules</Keyboard>""") {
        CustomLayoutHelper.applyCustomLayout(context, xml, target)
        val done = CountDownLatch(1)
        CustomLayoutStore.execute { done.countDown() }
        assertTrue(done.await(30, TimeUnit.SECONDS))
    }
    private fun rules(target: String) = JSONArray(prefs.getString("pref_custom_double_tap_rules_${target}_custom", null))

    @Test fun replacingLayoutWithoutRulesClearsBothEnglishAliases() {
        import("en", """<Replace from="a" to="b"/>""")
        assertEquals(1, rules("en").length())
        import("zz")
        assertEquals(0, rules("en").length())
        assertEquals(0, rules("zz").length())
    }

    @Test fun replacingRussianRulesDoesNotAlterEnglishRules() {
        import("en", """<Replace from="a" to="b"/>""")
        import("ru", """<Replace from="я" to="ю"/>""")
        import("ru")
        assertEquals(0, rules("ru").length())
        assertEquals("b", rules("en").getJSONObject(0).getString("replacement"))
    }

    @Test fun supplementaryKeysRemainWholeAndDuplicateKeysUseLastReplacement() {
        import("en", """<Replace from="😀x" to="first"/><Replace from="😀" to="🚀"/>""")
        val entry = rules("en").getJSONObject(0)
        assertEquals(1, rules("en").length())
        assertEquals("😀", entry.getString("key"))
        assertEquals(1, entry.getString("key").codePointCount(0, entry.getString("key").length))
        assertEquals("🚀", entry.getString("replacement"))
        assertEquals(rules("en").toString(), rules("zz").toString())
    }

    @Test fun invalidReplacementRetainsPreviousLayoutAndRules() {
        import("en", """<Replace from="a" to="b"/>""")
        val before = prefs.getString("pref_custom_layout_file_en", null)
        val oldRules = rules("en").toString()
        import("en", xml = "<Keyboard/>")
        assertEquals(before, prefs.getString("pref_custom_layout_file_en", null))
        assertEquals(oldRules, rules("en").toString())
    }
}
