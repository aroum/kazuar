// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.keyboard

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import io.github.aroum.kazuar.inputmethod.keyboard.internal.CustomLayoutLoader
import io.github.aroum.kazuar.inputmethod.keyboard.internal.KeyboardBuilder
import io.github.aroum.kazuar.inputmethod.keyboard.internal.KeyboardParams
import io.github.aroum.kazuar.inputmethod.latin.R
import io.github.aroum.kazuar.inputmethod.latin.RichInputMethodSubtype
import io.github.aroum.kazuar.inputmethod.latin.common.Constants
import io.github.aroum.kazuar.inputmethod.latin.utils.SubtypeLocaleUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomLayoutParserTest {
    private fun keys(xml: String?, mode: Int = KeyboardId.MODE_TEXT): List<Key> {
        val context: Context = RuntimeEnvironment.getApplication()
        SubtypeLocaleUtils.init(context)
        val set = KeyboardLayoutSet.Params().apply {
            mMode = mode
            mKeyboardLayoutSetName = "qwerty"
            mKeyboardWidth = 1080
            mKeyboardHeight = 480
            mSubtype = RichInputMethodSubtype(InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeLocale("en_US").setSubtypeMode("keyboard")
                .setSubtypeExtraValue("KeyboardLayoutSet=qwerty").build())
            mEditorInfo = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_DONE }
        }
        val params = KeyboardParams()
        val themed = ContextThemeWrapper(context, KeyboardTheme.getKeyboardTheme(context).mStyleId)
        val builder = KeyboardBuilder(themed, params)
        val id = KeyboardId(KeyboardId.ELEMENT_ALPHABET, set)
        if (xml == null) builder.load(R.xml.kbd_qwerty, id)
        else {
            builder.loadHeaderOnly(R.xml.kbd_qwerty, id)
            CustomLayoutLoader.loadCustomLayoutOrThrow(themed, params, xml)
        }
        return params.mSortedKeys.filter { !it.isSpacer }
    }

    @Test fun compiledKeyboardStillBuildsAcrossTextEmailAndUrlCases() {
        for (mode in listOf(KeyboardId.MODE_TEXT, KeyboardId.MODE_EMAIL, KeyboardId.MODE_URL)) {
            val codes = keys(null, mode).map { it.code }
            assertTrue(codes.contains('q'.code))
            assertTrue(codes.contains(Constants.CODE_SPACE))
            assertTrue(codes.contains(Constants.CODE_ENTER))
        }
    }

    @Test fun exactStylesGiveCommandsAndExplicitCodesWin() {
        val result = keys("""<Keyboard><Row>
            <Key keyStyle="spaceKeyStyle"/>
            <Key keyStyle="shiftKeyStyle"/>
            <Key keyStyle="enterKeyStyle"/>
            <Key keyStyle="deleteKeyStyle" code="97"/>
            <Key keyStyle="spacebar_shift" keySpec="x"/>
            <Key keyStyle="reenterSettings" keySpec="y"/>
            </Row></Keyboard>""")
        assertEquals(listOf(Constants.CODE_SPACE, Constants.CODE_SHIFT, Constants.CODE_ENTER,
            97, 'x'.code, 'y'.code), result.map { it.code })
        assertEquals(Key.BACKGROUND_TYPE_ACTION, result[2].backgroundType)
        assertEquals(Key.BACKGROUND_TYPE_NORMAL, result[5].backgroundType)
    }

    @Test fun nestedSwitchSelectsFirstMatchingBranchAndHonorsParent() {
        val result = keys("""<Keyboard><switch>
            <case mode="email"><Row><Key keySpec="e"/></Row>
                <switch><default><Row><Key keySpec="z"/></Row></default></switch></case>
            <case mode="text"><Row><switch>
                <case imeAction="search"><Key keySpec="s"/></case>
                <case imeAction="actionDone"><Key keySpec="d"/></case>
                <default><Key keySpec="f"/></default>
            </switch></Row></case>
            <default><Row><Key keySpec="n"/></Row></default>
            </switch></Keyboard>""")
        assertEquals(listOf('d'.code), result.map { it.code })
    }

    @Test fun importedCasesHonorBooleanLocaleAndLayoutConditions() {
        val result = keys("""<Keyboard><switch>
            <case passwordInput="true"><Row><Key keySpec="p"/></Row></case>
            <case languageCode="ru"><Row><Key keySpec="r"/></Row></case>
            <case keyboardLayoutSet="qwerty|east_slavic" languageCode="en"
                countryCode="US" passwordInput="false" isSplitLayout="false">
                <Row><Key keySpec="a" mode="!email"/><Key keySpec="e" mode="email"/></Row>
            </case><default><Row><Key keySpec="f"/></Row></default>
            </switch></Keyboard>""")
        assertEquals(listOf('a'.code), result.map { it.code })
    }

    @Test fun unknownModeDoesNotSilentlyMeanTextAndRowScopeEnds() {
        val result = keys("""<Keyboard><switch>
            <case mode="typo"><Row><Key keySpec="t"/></Row></case>
            <default><Row><Key keySpec="a"/></Row><Key keySpec="x"/></default>
            </switch></Keyboard>""")
        assertEquals(listOf('a'.code), result.map { it.code })
    }

    @Test fun supplementaryKeyCodeIsPreserved() {
        assertEquals(0x1F600, keys("""<Keyboard><Row><Key keySpec="😀"/></Row></Keyboard>""").single().code)
    }
}
