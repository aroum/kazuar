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
        fun onLayoutLoaded(target: String)
    }

    @JvmStatic
    fun handleLoadedLayout(context: Context, uri: Uri, callback: Callback? = null) {
        val content = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw Exception(context.getString(R.string.error_cannot_open_file_stream))
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_file_load_failed, e.message), Toast.LENGTH_LONG).show()
            return
        }

        val trimmed = content.trim().removePrefix("\uFEFF").trim()
        if (!trimmed.startsWith("<")) {
            Toast.makeText(context, context.getString(R.string.toast_file_load_failed, context.getString(R.string.error_file_not_xml_layout)), Toast.LENGTH_LONG).show()
            return
        }

        var target: String? = null
        val parsedReplaceRules = mutableListOf<Pair<String, String>>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(trimmed))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    if (target == null) {
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i).substringAfter(':').lowercase(Locale.ROOT)
                            val attrVal = parser.getAttributeValue(i)?.lowercase(Locale.ROOT) ?: ""
                            if (attrName == "layer" || attrName == "target") {
                                target = when {
                                    attrVal in listOf("editing", "edit", "arrows", "arrow") -> "editing"
                                    attrVal in listOf("num", "numpad", "number", "numbers", "pin", "numeric") -> "num"
                                    attrVal in listOf("usr0", "user0", "user_0", "user-0") -> "usr0"
                                    attrVal in listOf("usr1", "user1", "user_1", "user-1") -> "usr1"
                                    attrVal in listOf("usr2", "user2", "user_2", "user-2") -> "usr2"
                                    attrVal in listOf("ru", "rus", "russian") -> "ru"
                                    attrVal in listOf("en", "eng", "english") -> "en"
                                    else -> null
                                }
                                if (target != null) break
                            } else if (attrName == "language" || attrName == "locale") {
                                target = when {
                                    attrVal in listOf("editing", "edit") -> "editing"
                                    attrVal in listOf("num", "numpad", "number", "pin") -> "num"
                                    attrVal in listOf("usr0", "user0", "user_0", "user-0") -> "usr0"
                                    attrVal in listOf("usr1", "user1", "user_1", "user-1") -> "usr1"
                                    attrVal in listOf("usr2", "user2", "user_2", "user-2") -> "usr2"
                                    attrVal.contains("ru") || attrVal.contains("rus") || attrVal.contains("slavic") -> "ru"
                                    attrVal.contains("en") || attrVal.contains("eng") || attrVal.contains("qwerty") -> "en"
                                    else -> null
                                }
                                if (target != null) break
                            }
                        }
                    }
                    if ("Replace".equals(tagName, ignoreCase = true)) {
                        var from: String? = null
                        var to: String? = null
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i).substringAfter(':').lowercase(Locale.ROOT)
                            if (attrName == "from") from = parser.getAttributeValue(i)
                            else if (attrName == "to") to = parser.getAttributeValue(i)
                        }
                        if (from != null && to != null) {
                            parsedReplaceRules.add(Pair(from, to))
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_file_load_failed, context.getString(R.string.error_invalid_layout_xml, e.message ?: "")), Toast.LENGTH_LONG).show()
            return
        }

        if (target == null) {
            val fileName = getFileName(context, uri)?.lowercase(Locale.ROOT) ?: ""
            target = when {
                fileName.contains("editing") || fileName.contains("edit") || fileName.contains("arrow")
                        || fileName.contains("стрелк") || fileName.contains("редактир") -> "editing"
                fileName.contains("num") || fileName.contains("pin") || fileName.contains("цифр") -> "num"
                fileName.contains("usr0") || fileName.contains("user0") || fileName.contains("user_0") || fileName.contains("user-0") -> "usr0"
                fileName.contains("usr1") || fileName.contains("user1") || fileName.contains("user_1") || fileName.contains("user-1") -> "usr1"
                fileName.contains("usr2") || fileName.contains("user2") || fileName.contains("user_2") || fileName.contains("user-2") -> "usr2"
                fileName.contains("ru") || fileName.contains("rus") || fileName.contains("slavic") -> "ru"
                fileName.contains("en") || fileName.contains("eng") || fileName.contains("qwerty") -> "en"
                trimmed.any { it in '\u0400'..'\u04FF' } -> "ru"
                else -> null
            }
        }

        if (target != null) {
            try {
                applyCustomLayout(context, trimmed, target, parsedReplaceRules, callback)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_file_load_failed, e.message), Toast.LENGTH_LONG).show()
            }
        } else {
            val targets = arrayOf("ru", "en", "editing", "num", "usr0", "usr1", "usr2")
            val options = targets.map { getTargetDisplayName(context, it) }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle(R.string.dialog_select_layout_target)
                .setItems(options) { _, which ->
                    val selectedTarget = targets[which]
                    try {
                        applyCustomLayout(context, trimmed, selectedTarget, parsedReplaceRules, callback)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_file_load_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun applyCustomLayout(
        context: Context,
        xmlContent: String,
        target: String,
        replaceRules: List<Pair<String, String>>? = null,
        callback: Callback? = null
    ) {
        val rulesToApply = replaceRules ?: run {
            val rules = mutableListOf<Pair<String, String>>()
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
                            rules.add(Pair(from, to))
                        }
                    }
                }
                eventType = parser.next()
            }
            rules
        }

        val prefs = DeviceProtectedUtils.getSharedPreferences(context)
        val editor = prefs.edit()
            .putString("pref_custom_layout_$target", xmlContent)
            .putString("pref_keyboard_layout_$target", "custom")

        if ((target == "ru" || target == "en") && rulesToApply.isNotEmpty()) {
            val rulesKey = "pref_custom_double_tap_rules_${target}_custom"
            val rulesArray = JSONArray()
            val existingKeys = mutableMapOf<String, JSONObject>()

            for (rule in rulesToApply) {
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
            editor.putString(rulesKey, rulesArray.toString())
        }

        editor.apply()
        KeyboardLayoutSet.clearKeyboardCache()

        val displayName = getTargetDisplayName(context, target)
        Toast.makeText(context, context.getString(R.string.toast_custom_layout_loaded, displayName), Toast.LENGTH_SHORT).show()

        callback?.onLayoutLoaded(target)
    }

    fun getTargetDisplayName(context: Context, target: String): String {
        return when (target) {
            "ru" -> context.getString(R.string.language_russian)
            "en" -> context.getString(R.string.language_english)
            "editing" -> context.getString(R.string.pref_keyboard_layout_editing_title)
            "num" -> context.getString(R.string.pref_keyboard_layout_num_title)
            "usr0" -> context.getString(R.string.pref_keyboard_layout_usr0_title)
            "usr1" -> context.getString(R.string.pref_keyboard_layout_usr1_title)
            "usr2" -> context.getString(R.string.pref_keyboard_layout_usr2_title)
            else -> target
        }
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
