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

package io.github.aroum.kazuar.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardLayoutSet
import io.github.aroum.kazuar.inputmethod.latin.R
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
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
        CustomLayoutStore.execute {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    CustomLayoutStore.readBounded(input)
                } ?: throw IllegalArgumentException(context.getString(R.string.error_cannot_open_file_stream))
                require(content.toByteArray(Charsets.UTF_8).size <= CustomLayoutStore.MAX_BYTES)
                io.github.aroum.kazuar.inputmethod.keyboard.internal.CustomLayoutLoader.validateStructure(content.trim().removePrefix("\uFEFF").trim())
                parseLoadedLayout(context, content, getFileName(context, uri), callback)
            } catch (failure: Exception) {
                showFailure(context, failure.message)
            }
        }
    }

    private fun showFailure(context: Context, message: String?) {
        CustomLayoutStore.post {
            Toast.makeText(context, context.getString(R.string.toast_file_load_failed, message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun parseLoadedLayout(context: Context, content: String, filename: String?, callback: Callback?) {
        val trimmed = content.trim().removePrefix("\uFEFF").trim()
        if (!trimmed.startsWith("<")) {
            showFailure(context, context.getString(R.string.error_file_not_xml_layout))
            return
        }

        var target: String? = null
        val parsedReplaceRules = mutableListOf<Pair<String, String>>()
        var hasCyrillicKeys = false
        var hasLatinKeys = false
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
                                    attrVal in listOf("en", "eng", "english", "latin", "lat", "qwerty") -> "en"
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
                                    attrVal.contains("en") || attrVal.contains("eng") || attrVal.contains("qwerty") || attrVal.contains("latin") || attrVal.contains("lat") -> "en"
                                    else -> null
                                }
                                if (target != null) break
                            }
                        }
                    }
                    if ("Key".equals(tagName, ignoreCase = true)) {
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i).substringAfter(':').lowercase(Locale.ROOT)
                            if (attrName == "keyspec" || attrName == "label") {
                                val attrVal = parser.getAttributeValue(i) ?: ""
                                for (ch in attrVal) {
                                    if (ch in '\u0400'..'\u04FF') {
                                        hasCyrillicKeys = true
                                    } else if ((ch in 'a'..'z') || (ch in 'A'..'Z')) {
                                        hasLatinKeys = true
                                    }
                                }
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
            showFailure(context, context.getString(R.string.error_invalid_layout_xml, e.message ?: ""))
            return
        }

        if (target == null) {
            val fileName = filename?.lowercase(Locale.ROOT) ?: ""
            target = when {
                fileName.contains("editing") || fileName.contains("edit") || fileName.contains("arrow")
                        || fileName.contains("стрелк") || fileName.contains("редактир") -> "editing"
                fileName.contains("num") || fileName.contains("pin") || fileName.contains("цифр") -> "num"
                fileName.contains("usr0") || fileName.contains("user0") || fileName.contains("user_0") || fileName.contains("user-0") -> "usr0"
                fileName.contains("usr1") || fileName.contains("user1") || fileName.contains("user_1") || fileName.contains("user-1") -> "usr1"
                fileName.contains("usr2") || fileName.contains("user2") || fileName.contains("user_2") || fileName.contains("user-2") -> "usr2"
                fileName.contains("ru") || fileName.contains("rus") || fileName.contains("slavic") || fileName.contains("рус") -> "ru"
                fileName.contains("en") || fileName.contains("eng") || fileName.contains("qwerty") || fileName.contains("latin") || fileName.contains("lat") || fileName.contains("англ") || fileName.contains("латин") -> "en"
                hasCyrillicKeys -> "ru"
                hasLatinKeys -> "en"
                trimmed.any { it in '\u0400'..'\u04FF' } -> "ru"
                else -> null
            }
        }

        if (target != null) {
            try {
                applyCustomLayout(context, trimmed, target, parsedReplaceRules, callback)
            } catch (e: Exception) {
                showFailure(context, e.message)
            }
        } else {
            val targets = arrayOf("ru", "en", "editing", "num", "usr0", "usr1", "usr2")
            val options = targets.map { getTargetDisplayName(context, it) }.toTypedArray()
            CustomLayoutStore.post {
              if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) return@post
              AlertDialog.Builder(context)
                .setTitle(R.string.dialog_select_layout_target)
                .setItems(options) { _, which ->
                    val selectedTarget = targets[which]
                    try {
                        applyCustomLayout(context, trimmed, selectedTarget, parsedReplaceRules, callback)
                    } catch (e: Exception) {
                        showFailure(context, e.message)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            }
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
        CustomLayoutStore.execute {
            var pinned: String? = null
            try {
                val canonical = CustomLayoutStore.target(target)
                val revision = CustomLayoutStore.prepare(context, xmlContent, canonical, pinRevision = true)
                pinned = revision
                val rules = replaceRules ?: parseReplaceRules(xmlContent)
                val editor = DeviceProtectedUtils.getSharedPreferences(context).edit()
                    .putString("pref_custom_layout_file_$canonical", revision)
                    .remove("pref_custom_layout_$canonical")
                    .putString("pref_keyboard_layout_$canonical", "custom")
                if (canonical == "en") {
                    editor.remove("pref_custom_layout_zz").remove("pref_custom_layout_file_zz")
                    editor.putString("pref_keyboard_layout_zz", "custom")
                }
                saveReplaceRules(editor, canonical, rules)
                check(editor.commit()) { "Cannot save custom layout selection" }
                CustomLayoutStore.post {
                    KeyboardLayoutSet.clearKeyboardCache()
                    if (context !is android.app.Activity || (!context.isFinishing && !context.isDestroyed)) {
                        val name = getTargetDisplayName(context, canonical)
                        Toast.makeText(context, context.getString(R.string.toast_custom_layout_loaded, name), Toast.LENGTH_SHORT).show()
                        callback?.onLayoutLoaded(canonical)
                    }
                }
            } catch (failure: Exception) {
                showFailure(context, failure.message)
            } finally {
                pinned?.let { CustomLayoutStore.release(it) }
            }
        }
    }

    private fun parseReplaceRules(xml: String): List<Pair<String, String>> {
        val rules = mutableListOf<Pair<String, String>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || !"Replace".equals(parser.name, ignoreCase = true)) continue
            var from: String? = null
            var to: String? = null
            for (i in 0 until parser.attributeCount) {
                when (parser.getAttributeName(i).substringAfter(':').lowercase(Locale.ROOT)) {
                    "from" -> from = parser.getAttributeValue(i)
                    "to" -> to = parser.getAttributeValue(i)
                }
            }
            if (from != null && to != null) rules.add(Pair(from, to))
        }
        return rules
    }

    private fun saveReplaceRules(
        editor: android.content.SharedPreferences.Editor,
        target: String,
        rules: List<Pair<String, String>>
    ) {
        if ((target != "ru" && target != "en") || rules.isEmpty()) return
        val array = JSONArray()
        val existing = mutableMapOf<String, JSONObject>()
        for ((from, to) in rules) {
            if (from.isEmpty()) continue
            val key = from.substring(0, 1)
            val entry = existing[key] ?: JSONObject().put("key", key).also {
                existing[key] = it
                array.put(it)
            }
            entry.put("replacement", to).put("enabled", true)
        }
        editor.putString("pref_custom_double_tap_rules_${target}_custom", array.toString())
        if (target == "en") editor.putString("pref_custom_double_tap_rules_zz_custom", array.toString())
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
