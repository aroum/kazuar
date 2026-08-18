package io.github.sds100.keymapper.inputmethod.compat

import android.os.Build
import android.os.Build.VERSION_CODES
import android.text.TextUtils
import android.util.Log
import android.view.inputmethod.InputMethodSubtype
import io.github.sds100.keymapper.inputmethod.annotations.UsedForTesting
import io.github.sds100.keymapper.inputmethod.latin.RichInputMethodSubtype
import io.github.sds100.keymapper.inputmethod.latin.common.Constants
import io.github.sds100.keymapper.inputmethod.latin.common.LocaleUtils
import java.util.*

object InputMethodSubtypeCompatUtils {
    @kotlin.jvm.JvmStatic
    fun getLocaleObject(subtype: InputMethodSubtype): Locale {
        val languageTag = if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
            subtype.languageTag
        } else {
            null
        }
        if (!languageTag.isNullOrEmpty()) {
            return Locale.forLanguageTag(languageTag)
        }
        return LocaleUtils.constructLocaleFromString(subtype.locale)
    }
}