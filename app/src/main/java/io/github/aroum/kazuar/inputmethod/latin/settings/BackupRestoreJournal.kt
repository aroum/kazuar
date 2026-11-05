// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import androidx.core.os.UserManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Keeps old history and settings recoverable if the process dies during replacement. */
internal object BackupRestoreJournal {
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun register(root: File) { active.add(root.absolutePath) }
    fun unregister(root: File) { active.remove(root.absolutePath) }
    fun begin(root: File, prefs: SharedPreferences, changed: Set<String>, oldFiles: List<String>, newFiles: List<String>) {
        val json = JSONObject().put("settings", BackupSettingsCodec.encode(prefs.all))
                .put("changed", JSONArray(changed.toList()))
                .put("oldFiles", JSONArray(oldFiles)).put("newFiles", JSONArray(newFiles))
        val file = AtomicFile(File(root, "journal.json"))
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    fun committed(root: File) {
        FileOutputStream(File(root, "committed")).use { it.fd.sync() }
    }

    @JvmStatic fun recover(context: Context, prefs: SharedPreferences) {
        if (!UserManagerCompat.isUserUnlocked(context)) return
        val directory = context.applicationContext.filesDir
        directory.listFiles { file -> file.isDirectory && file.name.startsWith("restore-") }
                ?.forEach { root ->
                    if (root.absolutePath in active) return@forEach
                    if (File(root, "retain").exists()) {
                        error("A previous backup requires recovery: $root")
                    }
                    if (File(root, "committed").exists()) {
                        check(root.deleteRecursively())
                        return@forEach
                    }
                    val file = AtomicFile(File(root, "journal.json"))
                    if (!file.baseFile.exists() && !File(root, "journal.json.bak").exists()) {
                        check(root.deleteRecursively())
                        return@forEach
                    }
                    val json = JSONObject(String(file.readFully(), Charsets.UTF_8))
                    val old = json.getJSONArray("oldFiles").strings().toSet()
                    val fresh = json.getJSONArray("newFiles").strings()
                    val previous = File(root, "previous")
                    fresh.forEach { name ->
                        val target = File(directory, name)
                        // A file with an old name not moved into previous is still the original.
                        if (File(previous, name).exists() || name !in old) {
                            check(!target.exists() || target.deleteRecursively())
                        }
                    }
                    previous.listFiles()?.forEach { saved ->
                        val target = File(directory, saved.name)
                        check(!target.exists() || target.deleteRecursively())
                        check(saved.renameTo(target)) { "Cannot recover previous history" }
                    }
                    val editor = prefs.edit()
                    json.getJSONArray("changed").strings().forEach { editor.remove(it) }
                    check(editor.commit())
                    BackupRepository.writePreferences(prefs, BackupSettingsCodec.decode(json.getJSONObject("settings")))
                    check(root.deleteRecursively())
                }
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
