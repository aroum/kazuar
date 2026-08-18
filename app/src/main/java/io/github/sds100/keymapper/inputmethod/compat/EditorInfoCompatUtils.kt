package io.github.sds100.keymapper.inputmethod.compat

import android.os.Build
import android.view.inputmethod.EditorInfo
import java.util.*

object EditorInfoCompatUtils {
    @kotlin.jvm.JvmStatic
    fun hasFlagForceAscii(imeOptions: Int): Boolean {
        return imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0
    }

    @kotlin.jvm.JvmStatic
    fun imeActionName(imeOptions: Int): String {
        val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
        return when (actionId) {
            EditorInfo.IME_ACTION_UNSPECIFIED -> "actionUnspecified"
            EditorInfo.IME_ACTION_NONE -> "actionNone"
            EditorInfo.IME_ACTION_GO -> "actionGo"
            EditorInfo.IME_ACTION_SEARCH -> "actionSearch"
            EditorInfo.IME_ACTION_SEND -> "actionSend"
            EditorInfo.IME_ACTION_NEXT -> "actionNext"
            EditorInfo.IME_ACTION_DONE -> "actionDone"
            EditorInfo.IME_ACTION_PREVIOUS -> "actionPrevious"
            else -> "actionUnknown($actionId)"
        }
    }

    fun imeOptionsName(imeOptions: Int): String {
        val action = imeActionName(imeOptions)
        val flags = StringBuilder()
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            flags.append("flagNoEnterAction|")
        }
        if (imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT != 0) {
            flags.append("flagNavigateNext|")
        }
        if (imeOptions and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS != 0) {
            flags.append("flagNavigatePrevious|")
        }
        if (hasFlagForceAscii(imeOptions)) {
            flags.append("flagForceAscii|")
        }
        return flags.toString() + action
    }

    @kotlin.jvm.JvmStatic
    fun getPrimaryHintLocale(editorInfo: EditorInfo?): Locale? {
        if (editorInfo == null) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val hintLocales = editorInfo.hintLocales
            if (hintLocales != null && !hintLocales.isEmpty) {
                return hintLocales.get(0)
            }
        }
        return null
    }
}