/*
 * Copyright (C) 2026 Kazuar Keyboard
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

package io.github.sds100.keymapper.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import io.github.sds100.keymapper.inputmethod.keyboard.KeyboardLayoutSet
import io.github.sds100.keymapper.inputmethod.latin.R
import io.github.sds100.keymapper.inputmethod.latin.utils.DeviceProtectedUtils
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.Locale

object CustomLayoutHelper {

    fun interface Callback {
        fun onLayoutLoaded(language: String)
    }

    @JvmStatic
    fun handleLoadedLayout(context: Context, uri: Uri, callback: Callback? = null) {
        val content = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw Exception("Cannot open file stream")
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_file_load_failed, e.message), Toast.LENGTH_LONG).show()
            return
        }

        val trimmed = content.trim().removePrefix("\uFEFF").trim()
        if (!trimmed.startsWith("<")) {
            Toast.makeText(context, context.getString(R.string.toast_file_load_failed, "File is not an XML layout"), Toast.LENGTH_LONG).show()
            return
        }

        var language: String? = null
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(trimmed))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    for (i in 0 until parser.attributeCount) {
                        val attrName = parser.getAttributeName(i).substringAfter(':').lowercase(Locale.ROOT)
                        if (attrName == "language" || attrName == "locale") {
                            val attrVal = parser.getAttributeValue(i)?.lowercase(Locale.ROOT) ?: ""
                            if (attrVal.contains("ru") || attrVal.contains("rus") || attrVal.contains("slavic")) {
                                language = "ru"
                            } else if (attrVal.contains("en") || attrVal.contains("eng") || attrVal.contains("qwerty")) {
                                language = "en"
                            }
                            break
                        }
                    }
                    if (language != null) break
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_file_load_failed, "Invalid layout XML format: ${e.message}"), Toast.LENGTH_LONG).show()
            return
        }

        if (language == null) {
            val fileName = getFileName(context, uri)?.lowercase(Locale.ROOT) ?: ""
            language = when {
                fileName.contains("ru") || fileName.contains("rus") || fileName.contains("slavic") -> "ru"
                fileName.contains("en") || fileName.contains("eng") || fileName.contains("qwerty") -> "en"
                trimmed.any { it in '\u0400'..'\u04FF' } -> "ru"
                else -> null
            }
        }

        if (language == "ru" || language == "en") {
            try {
                applyCustomLayout(context, trimmed, language, callback)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_file_load_failed, e.message), Toast.LENGTH_LONG).show()
            }
        } else {
            val options = arrayOf(
                context.getString(R.string.language_russian),
                context.getString(R.string.language_english)
            )
            AlertDialog.Builder(context)
                .setTitle(R.string.dialog_select_layout_language)
                .setItems(options) { _, which ->
                    val selectedLang = if (which == 0) "ru" else "en"
                    try {
                        applyCustomLayout(context, trimmed, selectedLang, callback)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_file_load_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    @JvmStatic
    fun applyCustomLayout(context: Context, xmlContent: String, language: String, callback: Callback? = null) {
        val replaceRules = mutableListOf<Pair<String, String>>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlContent))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name
                if ("Replace".equals(tagName, ignoreCase = true)) {
                    var from: String? = null
                    var to: String? = null
                    for (i in 0 until parser.attributeCount) {
                        val attrName = parser.getAttributeName(i).substringAfter(':').lowercase(Locale.ROOT)
                        if (attrName == "from") from = parser.getAttributeValue(i)
                        else if (attrName == "to") to = parser.getAttributeValue(i)
                    }
                    if (from != null && to != null) {
                        replaceRules.add(Pair(from, to))
                    }
                }
            }
            eventType = parser.next()
        }

        val prefs = DeviceProtectedUtils.getSharedPreferences(context)
        val rulesKey = "pref_custom_double_tap_rules_${language}_custom"
        val existingRulesJson = prefs.getString(rulesKey, "[]") ?: "[]"
        val rulesArray = JSONArray(existingRulesJson)
        val existingKeys = mutableMapOf<String, JSONObject>()
        for (i in 0 until rulesArray.length()) {
            val obj = rulesArray.getJSONObject(i)
            val key = obj.optString("key")
            if (key.isNotEmpty()) {
                existingKeys[key] = obj
            }
        }

        for (rule in replaceRules) {
            val fromStr = rule.first
            val toStr = rule.second
            if (fromStr.isNotEmpty()) {
                val keyChar = fromStr.substring(0, 1)
                if (existingKeys.containsKey(keyChar)) {
                    val obj = existingKeys[keyChar]!!
                    obj.put("replacement", toStr)
                    obj.put("enabled", true)
                } else {
                    val obj = JSONObject()
                    obj.put("key", keyChar)
                    obj.put("replacement", toStr)
                    obj.put("enabled", true)
                    rulesArray.put(obj)
                    existingKeys[keyChar] = obj
                }
            }
        }

        prefs.edit()
            .putString("pref_custom_layout_$language", xmlContent)
            .putString("pref_keyboard_layout_$language", "custom")
            .putString(rulesKey, rulesArray.toString())
            .apply()

        KeyboardLayoutSet.clearKeyboardCache()

        Toast.makeText(context, context.getString(R.string.toast_custom_layout_loaded, language), Toast.LENGTH_SHORT).show()

        callback?.onLayoutLoaded(language)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        return cursor.getString(index)
                    }
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
