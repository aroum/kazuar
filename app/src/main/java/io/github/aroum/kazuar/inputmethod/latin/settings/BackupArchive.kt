// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads bounded portable entries; archive bytes are never extracted as native dictionaries. */
internal object BackupArchive {
    const val MAX_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
    private val allowedNames = setOf("settings.json", "dictionary.json", "history.json", "metadata.json")

    fun read(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 32) { "Too many archive entries" }
                require(!entry.isDirectory && entry.name in allowedNames) {
                    "Unsupported archive entry: ${entry.name}. Binary history backups are not accepted."
                }
                require(!entries.containsKey(entry.name)) { "Duplicate archive entry" }
                val bytes = readBounded(zip, MAX_ENTRY_BYTES)
                total += bytes.size
                require(total <= MAX_BYTES) { "Backup is too large" }
                entries[entry.name] = bytes
                zip.closeEntry()
            }
        }
        require(entries.isNotEmpty()) { "Empty backup" }
        return entries
    }

    fun readBounded(input: InputStream, limit: Int = MAX_ENTRY_BYTES): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            require(read <= limit - output.size()) { "Backup entry is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
