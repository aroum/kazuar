package io.github.sds100.keymapper.inputmethod.compat

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.SuggestionSpan
import io.github.sds100.keymapper.inputmethod.annotations.UsedForTesting
import java.util.*

object SuggestionSpanUtils {
    @kotlin.jvm.JvmStatic
    @UsedForTesting
    fun getTextWithAutoCorrectionIndicatorUnderline(
            context: Context?, text: String, locale: Locale?): CharSequence {
        if (TextUtils.isEmpty(text)) {
            return text
        }
        val spannable: Spannable = SpannableString(text)
        val suggestionSpan = SuggestionSpan(context, locale, arrayOf(), SuggestionSpan.FLAG_AUTO_CORRECTION, null)
        spannable.setSpan(suggestionSpan, 0, text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
        return spannable
    }
}