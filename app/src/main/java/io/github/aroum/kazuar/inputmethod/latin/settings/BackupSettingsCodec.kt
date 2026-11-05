// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import org.json.JSONArray
import org.json.JSONObject

/** JSON uses explicit types so an integral float survives a round trip. */
internal object BackupSettingsCodec {
    fun encode(values: Map<String, *>): JSONObject {
        val entries = JSONObject()
        values.forEach { (key, value) ->
            val type = BackupPreferenceSchema.typeOf(key) ?: return@forEach
            val encoded = if (value is Set<*>) JSONArray(value.toList()) else value
            entries.put(key, JSONObject().put("type", type).put("value", encoded))
        }
        return JSONObject().put("format", "kazuar-settings").put("version", 2).put("entries", entries)
    }

    fun decode(json: JSONObject): Map<String, Any> {
        val typed = json.optString("format") == "kazuar-settings"
        if (typed) require(json.getInt("version") == 2) { "Unsupported settings version" }
        val entries = if (typed) json.getJSONObject("entries") else json
        require(entries.length() <= 2048) { "Too many settings" }
        val result = linkedMapOf<String, Any>()
        entries.keys().forEach { key ->
            val type = BackupPreferenceSchema.typeOf(key) ?: return@forEach
            val raw = if (typed) {
                val entry = entries.getJSONObject(key)
                require(entry.getString("type") == type) { "Wrong setting type: $key" }
                entry.get("value")
            } else entries.get(key)
            result[key] = when (type) {
                "boolean" -> { require(raw is Boolean); raw }
                "string" -> { require(raw is String && raw.length <= 1024 * 1024); raw }
                "float" -> {
                    require(raw is Number)
                    raw.toFloat().also { require(it.isFinite()) }
                }
                "int" -> {
                    require(raw is Number && raw.toDouble().isFinite() &&
                            raw.toDouble() == raw.toLong().toDouble() &&
                            raw.toLong() in Int.MIN_VALUE..Int.MAX_VALUE)
                    raw.toInt()
                }
                "long" -> { require(raw is Number && raw.toDouble() == raw.toLong().toDouble()); raw.toLong() }
                "set" -> {
                    require(raw is JSONArray && raw.length() <= 2048)
                    (0 until raw.length()).map { raw.get(it).also { v -> require(v is String) } as String }.toSet()
                }
                else -> error("Unsupported preference type")
            }
        }
        return result
    }
}
