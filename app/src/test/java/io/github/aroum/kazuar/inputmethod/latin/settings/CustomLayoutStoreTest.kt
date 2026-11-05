// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.Context
import io.github.aroum.kazuar.inputmethod.keyboard.CustomLayoutValidator
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomLayoutStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val xml = """<Keyboard target="en"><Row><Key keySpec="a" keyWidth="100%p"/></Row></Keyboard>"""

    @Test fun validLayoutBuildsInBothOrientations() {
        CustomLayoutValidator.validate(context, "en", xml)
    }

    @Test(expected = Exception::class)
    fun wellFormedXmlWithNoRowsDoesNotPassConstruction() {
        CustomLayoutStore.prepare(context, "<Keyboard/>", "en")
    }

    @Test(expected = Exception::class)
    fun allSpacerLayoutIsRejected() {
        CustomLayoutStore.prepare(context, "<Keyboard><Row><Spacer keyWidth=\"100%p\"/></Row></Keyboard>", "en")
    }

    @Test(expected = Exception::class)
    fun malformedXmlIsRejectedWithPosition() {
        try {
            CustomLayoutValidator.validate(context, "en", "<Keyboard>\n<Row><Key></Row></Keyboard>")
        } catch (failure: Exception) {
            assertTrue(failure.message.orEmpty().contains("position") || failure.message.orEmpty().contains("line"))
            throw failure
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun inputLimitAppliesBeforeParsing() {
        CustomLayoutStore.readBounded(ByteArrayInputStream(ByteArray(CustomLayoutStore.MAX_BYTES + 1)))
    }

    @Test fun failedReplacementKeepsWorkingRevisionAndSelection() {
        val prefs = DeviceProtectedUtils.getSharedPreferences(context)
        val revision = CustomLayoutStore.prepare(context, xml, "en")
        prefs.edit().putString("pref_custom_layout_file_en", revision)
                .putString("pref_keyboard_layout_en", "custom").commit()
        try {
            CustomLayoutStore.prepare(context, "<Keyboard/>", "en")
            fail("Invalid replacement must fail")
        } catch (_: Exception) { }
        assertEquals(revision, prefs.getString("pref_custom_layout_file_en", null))
        assertEquals("custom", prefs.getString("pref_keyboard_layout_en", null))
    }

    @Test fun englishAliasDoesNotDuplicateXmlInPreferencesOrOnDisk() {
        val canonical = CustomLayoutStore.prepare(context, xml, "en")
        val alias = CustomLayoutStore.prepare(context, xml, "zz")
        assertEquals(canonical, alias)
        val prefs = DeviceProtectedUtils.getSharedPreferences(context)
        prefs.edit().putString("pref_custom_layout_en", xml).putString("pref_custom_layout_zz", xml).commit()
        CustomLayoutStore.read(context, "zz")
        val done = CountDownLatch(1)
        CustomLayoutStore.execute { done.countDown() }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        assertFalse(prefs.contains("pref_custom_layout_en"))
        assertFalse(prefs.contains("pref_custom_layout_zz"))
        assertEquals(canonical, prefs.getString("pref_custom_layout_file_en", null))
    }

    @Test fun portableBackupExportsXmlAndRejectsExternalLocalPaths() {
        val revision = CustomLayoutStore.prepare(context, xml, "en")
        val portable = CustomLayoutStore.exportSettings(context, mapOf("pref_custom_layout_file_en" to revision))
        assertEquals(xml, portable["pref_custom_layout_en"])
        assertFalse(portable.containsKey("pref_custom_layout_file_en"))
        val incoming = org.json.JSONObject("""{"pref_custom_layout_file_en":"../../outside.xml"}""")
        assertTrue(BackupSettingsCodec.decode(incoming).isEmpty())
    }

    @Test(expected = Exception::class)
    fun invalidLayoutInBackupFailsBeforeSettingsCommit() {
        CustomLayoutStore.prepareSettings(context, mapOf("sound_on" to false, "pref_custom_layout_en" to "<Keyboard/>"))
    }

    @Test fun storageCleanupDoesNotDeleteStagedBackupRevisions() {
        val prepared = CustomLayoutStore.prepareSettings(context, mapOf("pref_custom_layout_en" to xml))
        val ref = prepared["pref_custom_layout_file_en"] as String
        val directory = java.io.File(context.createDeviceProtectedStorageContext().filesDir, "custom-layouts")
        val staged = java.io.File(directory, ref)
        assertTrue(staged.setLastModified(1))
        val fillers = (0..32).map { index ->
            java.io.File(directory, "v1-" + "%064x".format(index) + ".xml").apply {
                writeBytes(ByteArray(CustomLayoutStore.MAX_BYTES))
            }
        }
        try {
            CustomLayoutStore.prepare(context, xml.replace("keySpec=\"a\"", "keySpec=\"b\""), "ru")
            assertTrue("A staged restore must retain its file until preference commit", staged.exists())
            assertEquals(xml, CustomLayoutStore.exportSettings(context, prepared)["pref_custom_layout_en"])
        } finally {
            CustomLayoutStore.releasePrepared(prepared)
            fillers.forEach { it.delete() }
        }
    }

}
