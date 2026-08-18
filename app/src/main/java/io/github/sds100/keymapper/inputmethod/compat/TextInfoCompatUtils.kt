package io.github.sds100.keymapper.inputmethod.compat

import android.os.Build
import android.view.textservice.TextInfo
import io.github.sds100.keymapper.inputmethod.annotations.UsedForTesting

object TextInfoCompatUtils {
    @get:UsedForTesting
    val isCharSequenceSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    @kotlin.jvm.JvmStatic
    @UsedForTesting
    fun newInstance(charSequence: CharSequence, start: Int, end: Int, cookie: Int,
                    sequenceNumber: Int): TextInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TextInfo(charSequence, start, end, cookie, sequenceNumber)
        } else {
            TextInfo(charSequence.subSequence(start, end).toString(), cookie, sequenceNumber)
        }
    }

    @kotlin.jvm.JvmStatic
    @UsedForTesting
    fun getCharSequenceOrString(textInfo: TextInfo?): CharSequence? {
        if (textInfo == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textInfo.charSequence ?: textInfo.text
        } else {
            textInfo.text
        }
    }
}