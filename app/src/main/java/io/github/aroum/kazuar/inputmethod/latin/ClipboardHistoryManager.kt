package io.github.aroum.kazuar.inputmethod.latin

import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.util.AtomicFile
import android.os.Build
import androidx.core.os.UserManagerCompat
import io.github.aroum.kazuar.inputmethod.latin.settings.Settings
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import io.github.aroum.kazuar.inputmethod.latin.utils.ClipboardCipher
import android.util.Log
import io.github.aroum.kazuar.inputmethod.compat.ClipboardManagerCompat
import io.github.aroum.kazuar.inputmethod.latin.utils.JsonUtils
import java.io.File
import java.lang.Exception
import java.util.*
import java.util.concurrent.Executors

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var pinnedHistoryClipsFile: File
    private lateinit var clipboardManager: ClipboardManager
    private val historyEntries: MutableList<ClipboardHistoryEntry>
    private var onHistoryChangeListener: OnHistoryChangeListener? = null
    private val diskExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "$TAG-disk-io").apply { isDaemon = true }
    }

    private var lastSourceTimestamp: Long? = null
    private var lastSourceContent: String? = null

    fun onCreate() {
        val storage = latinIME.applicationContext
        if (UserManagerCompat.isUserUnlocked(latinIME)) {
            pinnedHistoryClipsFile = File(storage.filesDir, PINNED_CLIPS_DATA_FILE_NAME)
        }
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fetchPrimaryClip()
        clipboardManager.addPrimaryClipChangedListener(this)
        if (UserManagerCompat.isUserUnlocked(latinIME)) startLoadPinnedClipsFromDisk()
    }

    fun onPinnedClipsAvailable(pinnedClips: List<ClipboardHistoryEntry>) {
        pinnedClips.forEach { clip ->
            if (historyEntries.any { it.timeStamp == clip.timeStamp }) {
                clip.timeStamp = nextEntryTimestamp()
            }
            historyEntries.add(clip)
        }
        sortHistoryEntries()
        if (onHistoryChangeListener != null) {
            pinnedClips.forEach {
                onHistoryChangeListener?.onClipboardHistoryEntryAdded(historyEntries.indexOf(it))
            }
        }
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
        diskExecutor.shutdown()
    }

    override fun onPrimaryClipChanged() {
        // Make sure we read clipboard content only if history settings is set
        fetchPrimaryClip()
    }

    private fun fetchPrimaryClip() {
        val prefs = DeviceProtectedUtils.getSharedPreferences(latinIME)
        if (!UserManagerCompat.isUserUnlocked(latinIME) ||
                !Settings.readClipboardHistoryEnabled(prefs)) return
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0) return
        clipData.getItemAt(0)?.let { clipItem ->
            val sensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                clipData.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            } else false
            if (sensitive && !prefs.getBoolean(Settings.PREF_SAVE_SENSITIVE_CLIPS, false)) return
            val sourceTimestamp = ClipboardManagerCompat.getClipTimestamp(clipData)

            val content = clipItem.coerceToText(latinIME)
            if (TextUtils.isEmpty(content)) return
            if (sourceTimestamp != null && sourceTimestamp == lastSourceTimestamp &&
                    content.toString() == lastSourceContent) return

            val entry = ClipboardHistoryEntry(nextEntryTimestamp(), content)
            lastSourceTimestamp = sourceTimestamp
            lastSourceContent = content.toString()
            historyEntries.add(entry)
            sortHistoryEntries()

            // Enforce maximum entries for unpinned items to avoid unbounded RAM consumption
            val unpinnedEntries = historyEntries.filter { !it.isPinned }
            if (unpinnedEntries.size > MAX_UNPINNED_HISTORY_ENTRIES) {
                val oldestUnpinned = unpinnedEntries.minByOrNull { it.timeStamp }
                if (oldestUnpinned != null) {
                    val removeIndex = historyEntries.indexOf(oldestUnpinned)
                    if (removeIndex != -1) {
                        historyEntries.removeAt(removeIndex)
                        onHistoryChangeListener?.onClipboardHistoryEntriesRemoved(removeIndex, 1)
                    }
                }
            }

            val at = historyEntries.indexOf(entry)
            if (at != -1) {
                onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
            }
        }
    }

    fun toggleClipPinned(ts: Long) {
        val from = historyEntries.indexOfFirst { it.timeStamp == ts }
        if (from < 0) return
        val historyEntry = historyEntries[from].apply {
            isPinned = !isPinned
        }
        sortHistoryEntries()
        val to = historyEntries.indexOf(historyEntry)
        onHistoryChangeListener?.onClipboardHistoryEntryMoved(from, to)
        startSavePinnedClipsToDisk()
    }

    fun clearHistory() {
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        val pos = historyEntries.indexOfFirst { !it.isPinned }
        val count = historyEntries.count { !it.isPinned }
        historyEntries.removeAll { !it.isPinned }
        if (pos >= 0 && count > 0) {
            onHistoryChangeListener?.onClipboardHistoryEntriesRemoved(pos, count)
        }
    }

    private fun sortHistoryEntries() {
        historyEntries.sort()
    }

    private fun checkClipRetentionElapsed() {
        val mins = latinIME.mSettings.current.mClipboardHistoryRetentionTime
        if (mins <= 0) return // No retention limit
        val maxClipRetentionTime = mins * 60 * 1000L
        val now = System.currentTimeMillis()
        historyEntries.removeAll { !it.isPinned && (now - it.timeStamp) > maxClipRetentionTime }
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() = checkClipRetentionElapsed()

    fun getHistorySize() = historyEntries.size

    fun getHistoryEntry(position: Int) = historyEntries.getOrNull(position)

    fun getHistoryEntryContent(timeStamp: Long) = historyEntries.firstOrNull { it.timeStamp == timeStamp }

    fun setHistoryChangeListener(l: OnHistoryChangeListener?) {
        onHistoryChangeListener = l
    }

    fun retrieveClipboardContent(): CharSequence {
        val clipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0) return ""
        return clipData.getItemAt(0)?.coerceToText(latinIME) ?: ""
    }

    private fun startLoadPinnedClipsFromDisk() {
        diskExecutor.execute {
            loadFromDisk()
        }
    }

    private fun loadFromDisk() {
        // Debugging
        if (pinnedHistoryClipsFile.exists() && !pinnedHistoryClipsFile.canRead()) {
            Log.w(TAG, "Attempt to read pinned clips file $pinnedHistoryClipsFile without permission")
        }
        var list = emptyList<ClipboardHistoryEntry>()
        try {
            if (pinnedHistoryClipsFile.exists()) {
                val bytes = AtomicFile(pinnedHistoryClipsFile).readFully()
                list = JsonUtils.jsonBytesToHistoryEntryList(ClipboardCipher.decrypt(bytes))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't retrieve $pinnedHistoryClipsFile content", e)
        }
        latinIME.mHandler.postUpdateClipboardPinnedClips(list)
    }

    private fun startSavePinnedClipsToDisk() {
        val localCopy = historyEntries.filter { it.isPinned }.map { it.copy() }
        diskExecutor.execute {
            saveToDisk(localCopy)
        }
    }

    private fun saveToDisk(list: List<ClipboardHistoryEntry>) {
        // Debugging
        if (pinnedHistoryClipsFile.exists() && !pinnedHistoryClipsFile.canWrite()) {
            Log.w(TAG, "Attempt to write pinned clips file $pinnedHistoryClipsFile without permission")
        }
        val file = AtomicFile(pinnedHistoryClipsFile)
        var stream: java.io.FileOutputStream? = null
        try {
            val json = JsonUtils.historyEntryListToJsonStr(list).ifEmpty { "[]" }
            val bytes = ClipboardCipher.encrypt(json.toByteArray(Charsets.UTF_8))
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            Log.w(TAG, "Couldn't save pinned clips", e)
        }
    }

    private fun nextEntryTimestamp(): Long = maxOf(System.currentTimeMillis(),
            (historyEntries.maxOfOrNull { it.timeStamp } ?: 0L) + 1L)

    interface OnHistoryChangeListener {
        fun onClipboardHistoryEntryAdded(at: Int)
        fun onClipboardHistoryEntriesRemoved(pos: Int, count: Int)
        fun onClipboardHistoryEntryMoved(from: Int, to: Int)
    }

    companion object {
        const val MAX_UNPINNED_HISTORY_ENTRIES = 30
        const val PINNED_CLIPS_DATA_FILE_NAME = "pinned_clips.data"
        const val TAG = "ClipboardHistoryManager"
    }

    init {
        historyEntries = LinkedList()
    }
}