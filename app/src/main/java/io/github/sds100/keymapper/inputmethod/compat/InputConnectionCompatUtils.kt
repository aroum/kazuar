package io.github.sds100.keymapper.inputmethod.compat

import android.os.Build
import android.view.inputmethod.InputConnection

object InputConnectionCompatUtils {
    private const val CURSOR_UPDATE_IMMEDIATE = 1 shl 0
    private const val CURSOR_UPDATE_MONITOR = 1 shl 1

    @kotlin.jvm.JvmStatic
    fun requestCursorUpdates(inputConnection: InputConnection,
                             enableMonitor: Boolean, requestImmediateCallback: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        val cursorUpdateMode = ((if (enableMonitor) CURSOR_UPDATE_MONITOR else 0)
                or if (requestImmediateCallback) CURSOR_UPDATE_IMMEDIATE else 0)
        return inputConnection.requestCursorUpdates(cursorUpdateMode)
    }
}