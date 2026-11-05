// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    private fun archive(name: String, content: ByteArray = "{}".toByteArray()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
        }
        return output.toByteArray()
    }

    @Test fun portableEntriesAreRead() {
        val restored = BackupArchive.read(ByteArrayInputStream(archive("settings.json")))
        assertArrayEquals("{}".toByteArray(), restored["settings.json"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun parentTraversalIsRejected() { BackupArchive.read(ByteArrayInputStream(archive("../outside/settings.json"))) }

    @Test(expected = IllegalArgumentException::class)
    fun absolutePathsAreRejected() { BackupArchive.read(ByteArrayInputStream(archive("/settings.json"))) }

    @Test(expected = IllegalArgumentException::class)
    fun siblingPrefixTraversalIsRejected() { BackupArchive.read(ByteArrayInputStream(archive("../restore-evil/settings.json"))) }

    @Test(expected = IllegalArgumentException::class)
    fun binaryHistoryNeverReachesNativeParser() {
        BackupArchive.read(ByteArrayInputStream(archive("history/UserHistoryDictionary.ru.dict")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun compressedOversizedEntryIsRejectedWhileStreaming() {
        val bytes = archive("history.json", ByteArray(BackupArchive.MAX_ENTRY_BYTES + 1))
        BackupArchive.read(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundedJsonReadRejectsOversizedInput() {
        BackupArchive.readBounded(ByteArrayInputStream(ByteArray(33)), 32)
    }
}
