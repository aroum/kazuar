// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.UserDictionary.Words
import android.util.Log
import android.widget.Toast
import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardLayoutSet
import io.github.aroum.kazuar.inputmethod.keyboard.internal.CustomThemeHelper
import io.github.aroum.kazuar.inputmethod.latin.PersonalDictionaryShortcutCache
import io.github.aroum.kazuar.inputmethod.latin.R
import io.github.aroum.kazuar.inputmethod.latin.personalization.PersonalizationHelper
import io.github.aroum.kazuar.inputmethod.latin.personalization.UserHistoryDictionary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object BackupRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean()
    private val main = Handler(Looper.getMainLooper())
    private const val TAG = "BackupRepository"

    @JvmStatic fun run(context: Context, prefs: SharedPreferences, uri: Uri, operation: Int) {
        val app = context.applicationContext
        if (!busy.compareAndSet(false, true)) {
            Toast.makeText(context, R.string.backup_in_progress, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, R.string.backup_in_progress, Toast.LENGTH_SHORT).show()
        executor.execute {
            val exporting = operation % 2 == 1
            val message = try {
                if (exporting) export(app, prefs, uri, operation) else restore(app, prefs, uri, operation)
                if (exporting) R.string.backup_success else R.string.restore_success
            } catch (e: Exception) {
                Log.e(TAG, if (exporting) "Backup export failed" else "Backup restore failed", e)
                if (exporting) R.string.backup_fail else R.string.restore_fail
            } finally {
                busy.set(false)
            }
            main.post { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun export(context: Context, prefs: SharedPreferences, uri: Uri, operation: Int) {
        val entries = linkedMapOf<String, ByteArray>()
        if (operation == 1001 || operation == 1007) {
            entries["settings.json"] = BackupSettingsCodec.encode(prefs.all).toString(4).toByteArray(Charsets.UTF_8)
        }
        if (operation == 1003 || operation == 1007) {
            entries["dictionary.json"] = exportDictionary(context).toString(4).toByteArray(Charsets.UTF_8)
        }
        if (operation == 1005 || operation == 1007) {
            synchronized(PersonalizationHelper.DICTIONARY_IO_LOCK) {
                PersonalizationHelper.flushAllUserHistoryDictionaries()
                entries["history.json"] = HistoryBackup.export(context).toString().toByteArray(Charsets.UTF_8)
            }
        }
        require(entries.values.all { it.size <= BackupArchive.MAX_ENTRY_BYTES } &&
                entries.values.sumOf { it.size.toLong() } <= BackupArchive.MAX_BYTES)
        val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Document provider returned no output stream")
        output.use { stream ->
            if (operation == 1001 || operation == 1003) stream.write(entries.values.single())
            else ZipOutputStream(stream).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun restore(context: Context, prefs: SharedPreferences, uri: Uri, operation: Int) {
        synchronized(PersonalizationHelper.DICTIONARY_IO_LOCK) {
            BackupRestoreJournal.recover(context, prefs)
        }
        val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Document provider returned no input stream")
        val entries = input.use {
            when (operation) {
                1002 -> mapOf("settings.json" to BackupArchive.readBounded(it))
                1004 -> mapOf("dictionary.json" to BackupArchive.readBounded(it))
                1006, 1008 -> BackupArchive.read(it)
                else -> error("Unknown backup operation")
            }
        }
        if (operation == 1006) require(entries.containsKey("history.json")) { "Missing portable history" }
        if (operation == 1008) require(entries.keys.containsAll(
                listOf("settings.json", "dictionary.json", "history.json"))) { "Incomplete full backup" }
        // All JSON and native write inputs are validated before touching live state.
        val settings = entries["settings.json"]?.let { BackupSettingsCodec.decode(jsonObject(it)) }
        val dictionary = entries["dictionary.json"]?.let { validateDictionary(jsonArray(it)) }
        val history = entries["history.json"]?.let { HistoryBackup.validate(jsonObject(it)) }
        val transaction = File.createTempFile("restore-", "", context.filesDir).also {
            BackupRestoreJournal.register(it)
            check(it.delete() && it.mkdirs())
        }
        try {
            val staged = File(transaction, "new")
            if (history != null) HistoryBackup.build(history, staged)
            synchronized(PersonalizationHelper.DICTIONARY_IO_LOCK) {
                val oldSettings = prefs.all
                val oldFiles = mutableListOf<File>()
                val installed = mutableListOf<File>()
                val inserted = mutableListOf<Uri>()
                val previous = File(transaction, "previous").also { check(it.mkdirs()) }
                val originalNames = if (history == null) emptyList() else
                    context.filesDir.listFiles { _, name -> name.startsWith(UserHistoryDictionary.NAME + ".") }
                            ?.map { it.name } ?: emptyList()
                BackupRestoreJournal.begin(transaction, prefs, settings?.keys ?: emptySet(),
                        originalNames, staged.listFiles()?.map { it.name } ?: emptyList())
                try {
                    if (history != null) {
                        PersonalizationHelper.flushAllUserHistoryDictionaries()
                        PersonalizationHelper.closeAllUserHistoryDictionaries()
                        context.filesDir.listFiles { _, name -> name.startsWith(UserHistoryDictionary.NAME + ".") }
                                ?.forEach { file ->
                                    check(file.renameTo(File(previous, file.name))) { "Cannot preserve previous history" }
                                    oldFiles.add(File(previous, file.name))
                                }
                        staged.listFiles()?.forEach { file ->
                            val target = File(context.filesDir, file.name)
                            check(file.renameTo(target)) { "Cannot install restored history" }
                            installed.add(target)
                        }
                    }
                    if (settings != null) writePreferences(prefs, settings)
                    if (dictionary != null) insertDictionary(context, dictionary, inserted)
                    BackupRestoreJournal.committed(transaction)
                } catch (failure: Exception) {
                    // Keep every old file until the entire restore has succeeded.
                    inserted.forEach { item ->
                        try { context.contentResolver.delete(item, null, null) }
                        catch (rollback: Exception) { failure.addSuppressed(rollback) }
                    }
                    try { writePreferences(prefs, oldSettings, clear = true) }
                    catch (rollback: Exception) { failure.addSuppressed(rollback) }
                    installed.forEach { it.deleteRecursively() }
                    oldFiles.forEach { file ->
                        if (!file.renameTo(File(context.filesDir, file.name))) {
                            failure.addSuppressed(IOException("Previous history retained at $file"))
                        }
                    }
                    if (failure.suppressed.isNotEmpty()) {
                        // Retain recovery files on disk if rollback itself fails.
                        File(transaction, "retain").createNewFile()
                    }
                    throw failure
                } finally {
                    KeyboardLayoutSet.clearKeyboardCache()
                    CustomThemeHelper.clearCache()
                    PersonalDictionaryShortcutCache.invalidate()
                }
            }
        } finally {
            if (!File(transaction, "retain").exists()) transaction.deleteRecursively()
            BackupRestoreJournal.unregister(transaction)
        }
    }

    internal fun writePreferences(prefs: SharedPreferences, values: Map<String, *>, clear: Boolean = false) {
        val editor = prefs.edit()
        if (clear) editor.clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    require(value.all { it is String })
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
                else -> error("Unsupported setting type")
            }
        }
        check(editor.commit()) { "Cannot commit restored settings" }
    }

    private fun exportDictionary(context: Context): JSONArray {
        val result = JSONArray()
        context.contentResolver.query(Words.CONTENT_URI,
                arrayOf(Words.WORD, Words.SHORTCUT, Words.FREQUENCY, Words.LOCALE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                require(result.length() < 50000)
                result.put(JSONObject().put("word", cursor.getString(0))
                        .put("shortcut", cursor.getString(1) ?: JSONObject.NULL)
                        .put("frequency", cursor.getInt(2)).put("locale", cursor.getString(3) ?: JSONObject.NULL))
            }
        }
        return result
    }

    private fun validateDictionary(array: JSONArray): List<ContentValues> {
        require(array.length() <= 50000)
        return (0 until array.length()).map { n ->
            val item = array.getJSONObject(n)
            val word = item.getString("word")
            val shortcut = if (item.isNull("shortcut")) null else item.getString("shortcut")
            val locale = if (item.isNull("locale")) null else item.getString("locale")
            val frequency = item.getInt("frequency")
            require(word.isNotEmpty() && word.length <= 256 && (shortcut?.length ?: 0) <= 256 &&
                    (locale == null || locale.matches(Regex("[a-zA-Z0-9_-]{0,64}"))) && frequency in 0..255)
            ContentValues().apply {
                put(Words.WORD, word); put(Words.SHORTCUT, shortcut)
                put(Words.FREQUENCY, frequency); put(Words.LOCALE, locale)
            }
        }
    }

    private fun insertDictionary(context: Context, values: List<ContentValues>, inserted: MutableList<Uri>) {
        val existing = hashSetOf<Triple<String?, String?, String?>>()
        context.contentResolver.query(Words.CONTENT_URI,
                arrayOf(Words.WORD, Words.SHORTCUT, Words.LOCALE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) existing.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
        }
        // A bounded batch avoids Binder's transaction size limit. Receipt URIs permit rollback.
        val additions = values.filter {
            existing.add(Triple(it.getAsString(Words.WORD), it.getAsString(Words.SHORTCUT), it.getAsString(Words.LOCALE)))
        }
        additions.chunked(100).forEach { batch ->
            val ops = ArrayList(batch.map { ContentProviderOperation.newInsert(Words.CONTENT_URI).withValues(it).build() })
            val results = context.contentResolver.applyBatch(Words.CONTENT_URI.authority!!, ops)
            results.forEach { result -> result.uri?.let { inserted.add(it) } }
        }
    }

    private fun jsonObject(bytes: ByteArray) = JSONObject(jsonText(bytes))
    private fun jsonArray(bytes: ByteArray) = JSONArray(jsonText(bytes))

    /** Bound nesting before invoking Android's recursive JSON parser. */
    private fun jsonText(bytes: ByteArray): String {
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEach { ch ->
            if (quoted) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "Backup JSON is too deeply nested" } }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(!quoted && depth == 0)
        return text
    }
}
