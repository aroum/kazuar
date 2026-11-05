// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.util.Log
import android.util.LruCache
import io.github.aroum.kazuar.inputmethod.keyboard.CustomLayoutValidator
import io.github.aroum.kazuar.inputmethod.keyboard.KeyboardLayoutSet
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Immutable, versioned layout files. Preferences contain only small revision references. */
object CustomLayoutStore {
    const val MAX_BYTES = 512 * 1024
    private const val MAX_STORAGE_BYTES = 16 * 1024 * 1024L
    private val targets = setOf("ru", "en", "editing", "num", "usr0", "usr1", "usr2")
    private val referencePattern = Regex("v1-[a-f0-9]{64}\\.xml")
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "custom-layout-io").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, String>(1024 * 1024) {
        override fun sizeOf(key: String, value: String) = value.length * 2
    }
    private val pending = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    @Volatile private var onReady: Runnable? = null
    private val notifyReady = Runnable {
        KeyboardLayoutSet.clearKeyboardCache()
        onReady?.run()
    }

    @JvmStatic fun setOnLayoutsReadyListener(listener: Runnable?) { onReady = listener }
    internal fun execute(task: () -> Unit) { worker.execute(task) }
    internal fun post(task: () -> Unit) { main.post(task) }
    internal fun target(target: String): String {
        val canonical = if (target == "zz") "en" else target
        require(canonical in targets) { "Unsupported layout target: $target" }
        return canonical
    }
    internal fun isLocalReferenceKey(key: String): Boolean =
            key.removePrefix("pref_custom_layout_file_").let { key.startsWith("pref_custom_layout_file_") && it in targets }

    internal fun readBounded(input: InputStream): String {
        val bytes = BackupArchive.readBounded(input, MAX_BYTES)
        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun directory(context: Context): File {
        // Layouts are needed by the keyboard before first unlock; they contain configuration, not typing history.
        val storage = if (android.os.Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext() else context
        return File(storage.filesDir, "custom-layouts")
    }
    private fun reference(xml: String): String = "v1-" + MessageDigest.getInstance("SHA-256")
            .digest(xml.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) } + ".xml"

    @Synchronized internal fun prepare(context: Context, xml: String, layer: String): String {
        val canonical = target(layer)
        val bytes = xml.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Layout exceeds 512 KiB" }
        CustomLayoutValidator.validate(context, canonical, xml)
        val ref = reference(xml)
        val directory = directory(context)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create layout directory" }
        val file = AtomicFile(File(directory, ref))
        if (!file.baseFile.exists() || runCatching { readReference(context, ref) }.getOrNull() != xml) {
            reclaim(context, directory, bytes.size)
            var stream: FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (failure: Exception) {
                file.failWrite(stream)
                throw failure
            }
        }
        cache.put(ref, xml)
        return ref
    }

    private fun reclaim(context: Context, directory: File, incoming: Int) {
        val files = directory.listFiles()?.filter { it.isFile && it.name.matches(referencePattern) } ?: emptyList()
        val prefs = DeviceProtectedUtils.getSharedPreferences(context)
        val active = targets.mapNotNull { prefs.getString("pref_custom_layout_file_$it", null) }.toSet()
        // Retain recent revisions as well as all live pointers, including staged multi-layout restores.
        val protectedRevisions = active + files.sortedByDescending { it.lastModified() }.take(7).map { it.name }
        var total = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.filter { it.name !in protectedRevisions }.forEach { file ->
            if (total + incoming > MAX_STORAGE_BYTES) {
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
        // Recompute after deletions because a deleted file no longer reports its old size.
        total = directory.listFiles()?.sumOf { it.length() } ?: 0L
        require(total + incoming <= MAX_STORAGE_BYTES) { "Custom layout storage is full" }
    }

    @JvmStatic fun read(context: Context, layer: String): String? {
        val canonical = if (layer == "zz") "en" else layer
        if (canonical !in targets) return null
        val prefs = DeviceProtectedUtils.getSharedPreferences(context)
        val ref = prefs.getString("pref_custom_layout_file_$canonical", null)
        if (ref != null) {
            cache.get(ref)?.let { return it }
        }
        val pendingKey = canonical + ":" + (ref ?: "legacy")
        if (pending.add(pendingKey)) worker.execute {
            var available = false
            try {
                if (ref != null) {
                    cache.put(ref, readReference(context, ref))
                    available = true
                } else {
                    val legacy = prefs.getString("pref_custom_layout_$canonical", null)
                            ?: if (canonical == "en") prefs.getString("pref_custom_layout_zz", null) else null
                    if (legacy != null) {
                        val next = prepare(context, legacy, canonical)
                        val editor = prefs.edit().putString("pref_custom_layout_file_$canonical", next)
                                .remove("pref_custom_layout_$canonical")
                        if (canonical == "en") editor.remove("pref_custom_layout_zz").remove("pref_custom_layout_file_zz")
                        check(editor.commit()) { "Cannot migrate custom layout" }
                        available = true
                    }
                }
            } catch (failure: Exception) {
                Log.e("CustomLayoutStore", "Cannot load layout for $canonical; retaining previous data", failure)
            } finally {
                pending.remove(pendingKey)
            }
            if (available) {
                main.removeCallbacks(notifyReady)
                main.post(notifyReady)
            }
        }
        return null
    }

    private fun readReference(context: Context, ref: String): String {
        require(ref.matches(referencePattern)) { "Invalid layout revision" }
        val xml = AtomicFile(File(directory(context), ref)).openRead().use { readBounded(it) }
        require(reference(xml) == ref) { "Custom layout integrity check failed" }
        return xml
    }

    @JvmStatic fun prefetch(context: Context) { read(context, "en"); read(context, "ru") }

    /** Used by the backup worker; portable backups contain XML, never local file paths. */
    internal fun exportSettings(context: Context, values: Map<String, *>): Map<String, Any?> {
        val result = values.toMutableMap()
        targets.forEach { layer ->
            val ref = values["pref_custom_layout_file_$layer"] as? String
            if (ref != null) result["pref_custom_layout_$layer"] = readReference(context, ref)
            result.remove("pref_custom_layout_file_$layer")
        }
        result.remove("pref_custom_layout_zz")
        return result
    }

    /** Publishes immutable files first; callers commit all resulting pointers in their transaction. */
    internal fun prepareSettings(context: Context, values: Map<String, Any>): Map<String, Any> {
        val result = values.toMutableMap()
        val imports = values.filterKeys { it.startsWith("pref_custom_layout_") && !it.startsWith("pref_custom_layout_file_") }
        imports.forEach { (key, value) ->
            val layer = target(key.removePrefix("pref_custom_layout_"))
            if (key.endsWith("_zz") && imports.containsKey("pref_custom_layout_en")) {
                result.remove(key)
                return@forEach
            }
            require(value is String)
            result["pref_custom_layout_file_$layer"] = prepare(context, value, layer)
            result.remove(key)
        }
        return result
    }
}
