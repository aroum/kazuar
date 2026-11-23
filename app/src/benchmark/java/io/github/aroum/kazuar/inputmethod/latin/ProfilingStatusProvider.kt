package io.github.aroum.kazuar.inputmethod.latin

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.aroum.kazuar.inputmethod.latin.settings.Settings
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Shell-only readiness probe, absent from distribution APKs. Never returns editor text. */
class ProfilingStatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val snapshot = FutureTask {
            val ime = ProfilingIme.current
            val settings = Settings.getInstance().current
            MatrixCursor(arrayOf("locale", "main_dictionary", "slow_connection",
                "suggestions", "learning")).apply {
                addRow(arrayOf<Any>(
                    if (ime == null) "" else RichInputMethodManager.getInstance()
                        .currentSubtypeLocale.toString(),
                    if (ime?.mainDictionaryReady == true) 1 else 0,
                    if (ime == null || ime.hasSlowConnection()) 1 else 0,
                    if (settings?.isSuggestionsEnabledPerUserSettings() == true) 1 else 0,
                    if (settings != null && !settings.mIncognitoModeEnabled
                        && settings.mUsePersonalizedDicts) 1 else 0
                ))
            }
        }
        Handler(Looper.getMainLooper()).post(snapshot)
        return snapshot.get(5, TimeUnit.SECONDS)
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.kazuar.performance"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}
