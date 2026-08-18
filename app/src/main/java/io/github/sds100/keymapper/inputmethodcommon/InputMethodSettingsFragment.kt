package io.github.sds100.keymapper.inputmethodcommon

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.provider.Settings
import android.text.TextUtils
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager

abstract class InputMethodSettingsFragment : PreferenceFragment() {
    private var mSubtypeEnablerPreference: Preference? = null
    private var mInputMethodSettingsCategoryTitleRes = 0
    private var mInputMethodSettingsCategoryTitle: CharSequence? = null
    private var mSubtypeEnablerTitleRes = 0
    private var mSubtypeEnablerTitle: CharSequence? = null
    private var mSubtypeEnablerIconRes = 0
    private var mSubtypeEnablerIcon: Drawable? = null
    private var mImm: InputMethodManager? = null
    private var mImi: InputMethodInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = activity
        preferenceScreen = preferenceManager.createPreferenceScreen(context)
        initSettings(context)
    }

    private fun initSettings(context: Context): Boolean {
        mImm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        mImi = getMyImi(context, mImm)
        if (mImi == null || mImi!!.subtypeCount <= 1) {
            return false
        }
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS)
        intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, mImi!!.id)
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pref = Preference(context)
        pref.intent = intent
        mSubtypeEnablerPreference = pref
        preferenceScreen.addPreference(pref)
        updateSubtypeEnabler()
        return true
    }

    fun setInputMethodSettingsCategoryTitle(resId: Int) {
        mInputMethodSettingsCategoryTitleRes = resId
        updateSubtypeEnabler()
    }

    fun setInputMethodSettingsCategoryTitle(title: CharSequence?) {
        mInputMethodSettingsCategoryTitleRes = 0
        mInputMethodSettingsCategoryTitle = title
        updateSubtypeEnabler()
    }

    fun setSubtypeEnablerTitle(resId: Int) {
        mSubtypeEnablerTitleRes = resId
        updateSubtypeEnabler()
    }

    fun setSubtypeEnablerTitle(title: CharSequence?) {
        mSubtypeEnablerTitleRes = 0
        mSubtypeEnablerTitle = title
        updateSubtypeEnabler()
    }

    fun setSubtypeEnablerIcon(resId: Int) {
        mSubtypeEnablerIconRes = resId
        updateSubtypeEnabler()
    }

    fun setSubtypeEnablerIcon(drawable: Drawable?) {
        mSubtypeEnablerIconRes = 0
        mSubtypeEnablerIcon = drawable
        updateSubtypeEnabler()
    }

    fun updateSubtypeEnabler() {
        val pref = mSubtypeEnablerPreference ?: return
        val context = pref.context
        val title: CharSequence? = if (mSubtypeEnablerTitleRes != 0) {
            context.getString(mSubtypeEnablerTitleRes)
        } else {
            mSubtypeEnablerTitle
        }
        pref.title = title
        pref.intent?.putExtra(Intent.EXTRA_TITLE, title)
        val summary = getEnabledSubtypesLabel(context, mImm, mImi)
        if (!TextUtils.isEmpty(summary)) {
            pref.summary = summary
        }
        if (mSubtypeEnablerIconRes != 0) {
            pref.setIcon(mSubtypeEnablerIconRes)
        } else {
            pref.icon = mSubtypeEnablerIcon
        }
    }

    override fun onResume() {
        super.onResume()
        updateSubtypeEnabler()
    }

    companion object {
        private fun getMyImi(context: Context, imm: InputMethodManager?): InputMethodInfo? {
            val imis = imm?.inputMethodList ?: return null
            for (i in imis.indices) {
                val imi = imis[i]
                if (imi.packageName == context.packageName) {
                    return imi
                }
            }
            return null
        }

        private fun getEnabledSubtypesLabel(
                context: Context?, imm: InputMethodManager?, imi: InputMethodInfo?): String? {
            if (context == null || imm == null || imi == null) return null
            val subtypes = imm.getEnabledInputMethodSubtypeList(imi, true)
            val sb = StringBuilder()
            for (i in subtypes.indices) {
                val subtype = subtypes[i]
                if (sb.isNotEmpty()) {
                    sb.append(", ")
                }
                sb.append(subtype.getDisplayName(context, imi.packageName,
                        imi.serviceInfo.applicationInfo))
            }
            return sb.toString()
        }
    }
}