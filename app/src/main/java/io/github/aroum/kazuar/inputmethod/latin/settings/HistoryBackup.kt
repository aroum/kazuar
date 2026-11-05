// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.content.Context
import io.github.aroum.kazuar.inputmethod.latin.BinaryDictionary
import io.github.aroum.kazuar.inputmethod.latin.Dictionary
import io.github.aroum.kazuar.inputmethod.latin.NgramContext
import io.github.aroum.kazuar.inputmethod.latin.makedict.DictionaryHeader
import io.github.aroum.kazuar.inputmethod.latin.makedict.FormatSpec
import io.github.aroum.kazuar.inputmethod.latin.makedict.ProbabilityInfo
import io.github.aroum.kazuar.inputmethod.latin.personalization.UserHistoryDictionary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Imported history is validated text, never an externally supplied native dictionary file. */
internal object HistoryBackup {
    data class Probability(val probability: Int, val timestamp: Int, val level: Int, val count: Int)
    data class Word(val text: String, val probability: Probability, val bos: Boolean,
                    val notWord: Boolean, val offensive: Boolean, val shortcuts: List<Pair<String, Int>>)
    data class Ngram(val text: String, val probability: Probability, val context: List<Pair<String, Boolean>>)
    data class Snapshot(val locale: String, val words: List<Word>, val ngrams: List<Ngram>)

    private fun probability(info: ProbabilityInfo) = JSONObject()
            .put("probability", info.mProbability).put("timestamp", info.mTimestamp)
            .put("level", info.mLevel).put("count", info.mCount)

    fun export(context: Context): JSONObject {
        val dictionaries = JSONArray()
        val files = context.filesDir.listFiles { _, name ->
            name.startsWith(UserHistoryDictionary.NAME + ".") && name.endsWith(".dict")
        } ?: emptyArray()
        require(files.size <= 32)
        for (file in files) {
            val dictionary = BinaryDictionary(file.absolutePath, 0, file.length(), true,
                    Locale.ROOT, Dictionary.TYPE_USER_HISTORY, false)
            try {
                check(dictionary.isValidDictionary) { "Cannot read typing history" }
                val locale = dictionary.header.mLocaleString
                val words = JSONArray()
                val ngrams = JSONArray()
                val seenNgrams = hashSetOf<String>()
                var token = 0
                do {
                    check(words.length() < 50000) { "Typing history is too large" }
                    val next = dictionary.getNextWordProperty(token)
                    val word = next.mWordProperty
                    token = next.mNextToken
                    if (word.mWord.isNullOrEmpty() && !word.mIsBeginningOfSentence) continue
                    val shortcuts = JSONArray()
                    word.mShortcutTargets?.forEach { shortcut ->
                        shortcuts.put(JSONObject().put("word", shortcut.mWord)
                                .put("probability", shortcut.probability))
                    }
                    words.put(probability(word.mProbabilityInfo).put("word", word.mWord)
                            .put("bos", word.mIsBeginningOfSentence).put("notWord", word.mIsNotAWord)
                            .put("offensive", word.mIsPossiblyOffensive).put("shortcuts", shortcuts))
                    word.mNgrams?.forEach { ngram ->
                        val previous = JSONArray()
                        for (n in 1..ngram.mNgramContext.prevWordCount) {
                            previous.put(JSONObject().put("word",
                                    ngram.mNgramContext.getNthPrevWord(n)?.toString() ?: "")
                                    .put("bos", ngram.mNgramContext.isNthPrevWordBeginningOfSentence(n)))
                        }
                        val item = probability(ngram.mTargetWord.mProbabilityInfo)
                                .put("word", ngram.mTargetWord.mWord).put("context", previous)
                        if (seenNgrams.add(item.toString())) ngrams.put(item)
                    }
                } while (token != 0)
                dictionaries.put(JSONObject().put("locale", locale).put("words", words).put("ngrams", ngrams))
            } finally {
                dictionary.close()
            }
        }
        return JSONObject().put("format", "kazuar-history").put("version", 1)
                .put("dictionaries", dictionaries)
    }

    private fun word(text: String, allowEmpty: Boolean = false): String {
        require((allowEmpty || text.isNotEmpty()) && text.codePointCount(0, text.length) <= 47)
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            require(cp in 1..0x10ffff && cp !in 0xd800..0xdfff) { "Invalid Unicode word" }
            index += Character.charCount(cp)
        }
        return text
    }

    private fun readProbability(item: JSONObject): Probability {
        val probability = item.getInt("probability")
        val timestamp = item.getInt("timestamp")
        val level = item.getInt("level")
        val count = item.getInt("count")
        require(probability in -1..255 && timestamp >= -1 && level in 0..3 && count in 0..65535)
        return Probability(probability, timestamp, level, count)
    }

    fun validate(json: JSONObject): List<Snapshot> {
        require(json.getString("format") == "kazuar-history" && json.getInt("version") == 1)
        val dictionaries = json.getJSONArray("dictionaries")
        require(dictionaries.length() <= 32)
        val locales = hashSetOf<String>()
        return (0 until dictionaries.length()).map { index ->
            val dictionary = dictionaries.getJSONObject(index)
            val locale = dictionary.getString("locale")
            require(locale.matches(Regex("[a-zA-Z]{2,8}(?:_[a-zA-Z0-9]{1,16}){0,3}")) && locales.add(locale))
            val words = dictionary.getJSONArray("words")
            val ngrams = dictionary.getJSONArray("ngrams")
            require(words.length() <= 50000 && ngrams.length() <= 100000)
            val parsedWords = (0 until words.length()).map { n ->
                val entry = words.getJSONObject(n)
                val bos = entry.getBoolean("bos")
                val shortcuts = entry.getJSONArray("shortcuts")
                require(shortcuts.length() <= 16)
                Word(word(entry.getString("word"), bos), readProbability(entry), bos,
                        entry.getBoolean("notWord"), entry.getBoolean("offensive"),
                        (0 until shortcuts.length()).map { k ->
                            val shortcut = shortcuts.getJSONObject(k)
                            val score = shortcut.getInt("probability")
                            require(score in 0..15)
                            word(shortcut.getString("word")) to score
                        })
            }
            val parsedNgrams = (0 until ngrams.length()).map { n ->
                val entry = ngrams.getJSONObject(n)
                val previous = entry.getJSONArray("context")
                require(previous.length() in 1..3)
                Ngram(word(entry.getString("word")), readProbability(entry),
                        (0 until previous.length()).map { k ->
                            val item = previous.getJSONObject(k)
                            val bos = item.getBoolean("bos")
                            word(item.getString("word"), bos) to bos
                        })
            }
            val totalCount = parsedWords.sumOf {
                it.probability.count.toLong() * maxOf(1, it.shortcuts.size)
            } + parsedNgrams.sumOf { it.probability.count.toLong() }
            require(totalCount <= Int.MAX_VALUE / 4L) { "History counters exceed safe native limits" }
            Snapshot(locale, parsedWords, parsedNgrams)
        }
    }

    fun build(snapshots: List<Snapshot>, destination: File) {
        check(destination.mkdirs())
        snapshots.forEach { snapshot ->
            val locale = Locale.forLanguageTag(snapshot.locale.replace('_', '-'))
            val name = UserHistoryDictionary.NAME + "." + snapshot.locale + ".dict"
            val attrs = hashMapOf(
                    DictionaryHeader.DICTIONARY_ID_KEY to name.removeSuffix(".dict"),
                    DictionaryHeader.DICTIONARY_LOCALE_KEY to snapshot.locale,
                    DictionaryHeader.DICTIONARY_VERSION_KEY to (System.currentTimeMillis() / 1000).toString(),
                    DictionaryHeader.HAS_HISTORICAL_INFO_KEY to "1",
                    DictionaryHeader.USES_FORGETTING_CURVE_KEY to "1")
            val dictionary = BinaryDictionary(File(destination, name).absolutePath, true, locale,
                    Dictionary.TYPE_USER_HISTORY, FormatSpec.VERSION4.toLong(), attrs)
            try {
                check(dictionary.isValidDictionary)
                snapshot.words.forEach { word ->
                    val p = word.probability
                    val shortcuts = word.shortcuts.ifEmpty { listOf(null) }
                    shortcuts.forEach { shortcut ->
                        check(dictionary.restoreUnigramEntry(word.text, p.probability, shortcut?.first,
                                shortcut?.second ?: 0, word.bos, word.notWord, word.offensive,
                                p.timestamp, p.level, p.count)) { "Cannot restore history word" }
                    }
                }
                snapshot.ngrams.forEach { ngram ->
                    val previous = ngram.context.map { (text, bos) ->
                        if (bos) NgramContext.WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO
                        else NgramContext.WordInfo(text)
                    }.toTypedArray()
                    val p = ngram.probability
                    check(dictionary.restoreNgramEntry(NgramContext(*previous), ngram.text,
                            p.probability, p.timestamp, p.level, p.count)) { "Cannot restore history ngram" }
                }
                check(dictionary.flush()) { "Cannot write restored history" }
            } finally {
                dictionary.close()
            }
        }
    }
}
