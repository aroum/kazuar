package io.github.sds100.keymapper.inputmethod.compat

import android.graphics.Outline
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.View
import android.view.ViewOutlineProvider

object ViewOutlineProviderCompatUtils {
    private val EMPTY_INSETS_UPDATER: InsetsUpdater = object : InsetsUpdater {
        override fun setInsets(insets: InputMethodService.Insets) {}
    }

    @kotlin.jvm.JvmStatic
    fun setInsetsOutlineProvider(view: View): InsetsUpdater {
        if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
            return EMPTY_INSETS_UPDATER
        }
        val provider = InsetsOutlineProvider(view)
        view.outlineProvider = provider
        return provider
    }

    interface InsetsUpdater {
        fun setInsets(insets: InputMethodService.Insets)
    }

    private class InsetsOutlineProvider(private val mView: View) : ViewOutlineProvider(), InsetsUpdater {
        private var mLastVisibleTopInsets = NO_DATA

        override fun setInsets(insets: InputMethodService.Insets) {
            val visibleTopInsets = insets.visibleTopInsets
            if (mLastVisibleTopInsets != visibleTopInsets) {
                mLastVisibleTopInsets = visibleTopInsets
                mView.invalidateOutline()
            }
        }

        override fun getOutline(view: View, outline: Outline) {
            if (mLastVisibleTopInsets == NO_DATA) {
                BACKGROUND.getOutline(view, outline)
                return
            }
            outline.setRect(view.left, mLastVisibleTopInsets, view.right, view.bottom)
        }

        companion object {
            private const val NO_DATA = -1
        }

        init {
            mView.outlineProvider = this
        }
    }
}