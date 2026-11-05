// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupSettingsCodecTest {
    @Test fun integralFloatSurvivesJsonRoundTrip() {
        val json = BackupSettingsCodec.encode(mapOf("pref_keypress_sound_volume" to 1.0f))
        val restored = BackupSettingsCodec.decode(JSONObject(json.toString()))
        assertEquals(1.0f, restored["pref_keypress_sound_volume"])
    }

    @Test fun legacyIntegerEncodedFloatRestoresAsFloat() {
        val restored = BackupSettingsCodec.decode(JSONObject("""{"pref_keyboard_height_scale":1}"""))
        assertTrue(restored["pref_keyboard_height_scale"] is Float)
        assertEquals(1.0f, restored["pref_keyboard_height_scale"])
    }

    @Test fun fractionalLegacyFloatRestores() {
        val restored = BackupSettingsCodec.decode(JSONObject("""{"pref_keypress_sound_volume":0.375}"""))
        assertEquals(0.375f, restored["pref_keypress_sound_volume"])
    }

    @Test fun unknownKeysAreNotRestored() {
        val restored = BackupSettingsCodec.decode(JSONObject("""{"unexpected_native_path":"/tmp/evil","sound_on":false}"""))
        assertEquals(mapOf("sound_on" to false), restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedKnownTypeRejectsWholeImport() {
        BackupSettingsCodec.decode(JSONObject("""{"sound_on":true,"pref_keypress_sound_volume":"1.0"}"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun fractionalIntegerDoesNotTruncateSilently() {
        BackupSettingsCodec.decode(JSONObject("""{"pref_key_longpress_timeout":1.25}"""))
    }

    @Test fun perAppRulesAndCustomLayoutsSurvive() {
        val source = mapOf("incognito_ignore_org.example.browser" to true,
                "pref_custom_layout_ru" to "<Keyboard/>")
        assertEquals(source, BackupSettingsCodec.decode(BackupSettingsCodec.encode(source)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun typedBackupCannotRedefineKnownType() {
        val json = BackupSettingsCodec.encode(mapOf("pref_keypress_sound_volume" to 1f))
        json.getJSONObject("entries").getJSONObject("pref_keypress_sound_volume").put("type", "int")
        BackupSettingsCodec.decode(json)
    }
}
