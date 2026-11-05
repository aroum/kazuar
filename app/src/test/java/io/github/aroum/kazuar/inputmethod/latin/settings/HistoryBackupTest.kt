// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HistoryBackupTest {
    private fun backup(word: String = "слово", locale: String = "ru"): JSONObject {
        val entry = JSONObject().put("word", word).put("probability", 123)
                .put("timestamp", 123456).put("level", 0).put("count", 500)
                .put("bos", false).put("notWord", false).put("offensive", false)
                .put("shortcuts", JSONArray())
        val dictionary = JSONObject().put("locale", locale)
                .put("words", JSONArray().put(entry)).put("ngrams", JSONArray())
        return JSONObject().put("format", "kazuar-history").put("version", 1)
                .put("dictionaries", JSONArray().put(dictionary))
    }

    @Test fun portableHistoryPreservesHistoricalCounters() {
        val snapshot = HistoryBackup.validate(backup()).single()
        assertEquals("слово", snapshot.words.single().text)
        assertEquals(500, snapshot.words.single().probability.count)
        assertEquals(123456, snapshot.words.single().probability.timestamp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedWordIsRejectedBeforeJni() { HistoryBackup.validate(backup("a".repeat(48))) }

    @Test(expected = IllegalArgumentException::class)
    fun localeCannotSupplyFilePath() { HistoryBackup.validate(backup(locale = "../outside")) }

    @Test(expected = IllegalArgumentException::class)
    fun unpairedSurrogateIsRejectedBeforeJni() { HistoryBackup.validate(backup("\ud800")) }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveNgramContextIsRejectedBeforeJni() {
        val json = backup()
        val previous = JSONArray()
        repeat(4) { previous.put(JSONObject().put("word", "previous").put("bos", false)) }
        val ngram = JSONObject().put("word", "word").put("probability", 123)
                .put("timestamp", 123456).put("level", 0).put("count", 1).put("context", previous)
        json.getJSONArray("dictionaries").getJSONObject(0).getJSONArray("ngrams").put(ngram)
        HistoryBackup.validate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aggregateCountersCannotOverflowNativeIntegers() {
        val json = backup()
        val words = json.getJSONArray("dictionaries").getJSONObject(0).getJSONArray("words")
        val entry = words.getJSONObject(0).put("count", 65535)
        repeat(9000) { words.put(entry) }
        HistoryBackup.validate(json)
    }

}
