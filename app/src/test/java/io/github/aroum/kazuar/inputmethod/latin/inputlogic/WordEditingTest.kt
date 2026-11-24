package io.github.aroum.kazuar.inputmethod.latin.inputlogic

import android.content.Context
import android.content.res.Configuration
import android.text.InputType
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import io.github.aroum.kazuar.inputmethod.event.Event
import io.github.aroum.kazuar.inputmethod.latin.*
import io.github.aroum.kazuar.inputmethod.latin.common.Constants
import io.github.aroum.kazuar.inputmethod.latin.settings.SettingsValues
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import io.github.aroum.kazuar.inputmethod.latin.utils.ScriptUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class WordEditingTest {
    class TestIme : LatinIME() {
        lateinit var editorConnection: InputConnection
        override fun getCurrentInputConnection() = editorConnection
        override fun getCoordinatesForCurrentKeyboard(codePoints: IntArray) =
            IntArray(codePoints.size * 2) { Constants.NOT_A_COORDINATE }
        override fun setNeutralSuggestionStrip() {}
    }

    @Test fun deleteAndInsertAtEitherWordKeepsSeparator() {
        for (position in listOf("цифрые".length, "цифрые которые".length)) {
            val ime = Robolectric.buildService(TestIme::class.java).get()
            val editor = EditText(ime).apply {
                setText("цифрые которые")
                setSelection(position)
            }
            ime.editorConnection = object : BaseInputConnection(editor, true) {
                override fun getEditable() = editor.text
            }
            val singleton = RichInputMethodManager::class.java.getDeclaredField("sInstance")
                .apply { isAccessible = true }.get(null) as RichInputMethodManager
            singleton.javaClass.getDeclaredField("mImm").apply { isAccessible = true }
                .set(singleton, ime.getSystemService(Context.INPUT_METHOD_SERVICE))
            val locale = Locale.forLanguageTag("ru-RU")
            val context = RuntimeEnvironment.getApplication<android.app.Application>()
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(locale)
            })
            val prefs = DeviceProtectedUtils.getSharedPreferences(context)
            prefs.edit().putBoolean("pref_enable_double_tap_replacements", false)
                .putBoolean("pref_enable_macro_replacements", false).commit()
            val info = EditorInfo().apply {
                packageName = "org.example.editor"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            }
            val settings = SettingsValues(localized, prefs, localized.resources,
                InputAttributes(info, false, context.packageName))
            val dictionaries = Proxy.newProxyInstance(
                DictionaryFacilitator::class.java.classLoader,
                arrayOf(DictionaryFacilitator::class.java)
            ) { _, method, _ ->
                when (method.returnType) {
                    Locale::class.java -> locale
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    else -> null
                }
            } as DictionaryFacilitator
            val logic = InputLogic(ime, dictionaries)
            logic.mConnection.resetCachesUponCursorMoveAndReturnSuccess(position, position, false)
            logic.restartSuggestionsOnWordTouchedByCursor(settings, false, ScriptUtils.SCRIPT_CYRILLIC)
            logic.onCodeInput(settings,
                Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, Constants.CODE_DELETE,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false),
                0, ScriptUtils.SCRIPT_CYRILLIC, ime.mHandler)
            val afterDelete = if (position == "цифрые".length) "цифры которые" else "цифрые которы"
            assertEquals(afterDelete, editor.text.toString())
            logic.onCodeInput(settings,
                Event.createSoftwareKeypressEvent('а'.code, 'а'.code,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false),
                0, ScriptUtils.SCRIPT_CYRILLIC, ime.mHandler)
            val expected = if (position == "цифрые".length) "цифрыа которые" else "цифрые которыа"
            assertEquals(expected, editor.text.toString())
            assertEquals(editor.selectionStart, logic.mConnection.expectedSelectionStart)
            ime.mHandler.removeCallbacksAndMessages(null)
        }
    }
}
