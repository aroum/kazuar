// SPDX-License-Identifier: GPL-3.0-only
package io.github.aroum.kazuar.inputmethod.latin.settings

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.LayoutInflater
import android.widget.Switch
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import io.github.aroum.kazuar.inputmethod.latin.InputAttributes
import io.github.aroum.kazuar.inputmethod.latin.utils.DeviceProtectedUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36])
class AppOverridesTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val prefs get() = DeviceProtectedUtils.getSharedPreferences(context)
    private val app = "org.example.browser"
    @Before fun provideInputMethodManagerForAttributeTests() {
        // Attribute construction only needs shortcut availability, not a registered live IME.
        val type = io.github.aroum.kazuar.inputmethod.latin.RichInputMethodManager::class.java
        val singleton = type.getDeclaredField("sInstance").apply { isAccessible = true }.get(null)
        type.getDeclaredField("mImm").apply { isAccessible = true }
                .set(singleton, context.getSystemService(android.view.inputmethod.InputMethodManager::class.java))
    }
    private fun put(option: String, value: Boolean) {
        prefs.edit().putBoolean(AppOverrides.key(app, option), value).commit()
    }
    private fun values(pkg: String = app, password: Boolean = false): SettingsValues {
        val info = EditorInfo().apply {
            packageName = pkg
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or
                    (if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else 0)
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        return SettingsValues(context, prefs, context.resources, InputAttributes(info, true, context.packageName))
    }
    @Test fun withoutOverrideEditorRestrictionsAndLegacyExceptionsDoNotLeakIntoLearning() {
        prefs.edit().putBoolean("incognito_ignore_$app", true).commit()
        val v = values()
        assertTrue(v.mIncognitoModeEnabled)
        assertFalse(v.needsToLookupSuggestions())
        assertFalse(v.mAutoCorrectionEnabledPerUserSettings)
        assertFalse(v.mInputAttributes.mHasAppOverride)
    }
    @Test fun enabledOverrideUsesIndividualValuesEvenInPasswordFields() {
        put(AppOverrides.ENABLED, true)
        put(AppOverrides.LEARNING, true)
        put(AppOverrides.SUGGESTIONS, true)
        put(AppOverrides.CORRECTION, true)
        prefs.edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true)
                .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, false)
                .putBoolean(Settings.PREF_AUTO_CORRECTION, false).commit()
        val v = values(password = true)
        assertFalse(v.mIncognitoModeEnabled)
        assertTrue(v.needsToLookupSuggestions())
        assertTrue(v.isSuggestionsEnabledPerUserSettings)
        assertTrue(v.mAutoCorrectionEnabledPerUserSettings)
        assertFalse(v.isApplicationSpecifiedCompletionsOn)
        assertTrue(v.mInputAttributes.mIsPasswordField) // Keep the original field identity.
        assertFalse(v.mInputAttributes.mNoLearning)
    }
    @Test fun overridesCanDisableEveryFeatureAndRemainPackageScoped() {
        put(AppOverrides.ENABLED, true)
        for (option in listOf(AppOverrides.LEARNING, AppOverrides.SUGGESTIONS, AppOverrides.CORRECTION,
                AppOverrides.PERSONALIZED, AppOverrides.PREDICTIONS, AppOverrides.CAPS)) put(option, false)
        val v = values()
        assertTrue(v.mIncognitoModeEnabled)
        assertFalse(v.isSuggestionsEnabledPerUserSettings)
        assertFalse(v.mAutoCorrectionEnabledPerUserSettings)
        assertFalse(v.mUsePersonalizedDicts)
        assertFalse(v.mBigramPredictionEnabled)
        assertFalse(v.mAutoCap)
        assertFalse(values("org.example.other").mInputAttributes.mHasAppOverride)
    }
    @Test fun disablingAndReenablingRetainsChoicesAndRebuildsRawAttributes() {
        put(AppOverrides.ENABLED, true); put(AppOverrides.LEARNING, true)
        val first = values()
        put(AppOverrides.ENABLED, false)
        val disabled = SettingsValues(context, prefs, context.resources, first.mInputAttributes)
        assertTrue(disabled.mIncognitoModeEnabled)
        assertFalse(disabled.mInputAttributes.mHasAppOverride)
        assertFalse(disabled.needsToLookupSuggestions())
        put(AppOverrides.ENABLED, true)
        assertFalse(values().mIncognitoModeEnabled)
    }
    @Test fun overrideSettingsSurviveTypedBackupAndUnknownOptionsAreRejected() {
        put(AppOverrides.ENABLED, true); put(AppOverrides.LEARNING, false)
        prefs.edit().putBoolean(AppOverrides.key(app, "unknown"), true).commit()
        val restored = BackupSettingsCodec.decode(BackupSettingsCodec.encode(prefs.all))
        assertEquals(true, restored[AppOverrides.key(app, AppOverrides.ENABLED)])
        assertEquals(false, restored[AppOverrides.key(app, AppOverrides.LEARNING)])
        assertFalse(restored.containsKey(AppOverrides.key(app, "unknown")))
    }
    @Test fun appPageIsRestorableAndOptionsDependOnOverrideWithoutErasingValues() {
        put(AppOverrides.LEARNING, false)
        val intent = Intent().putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT,
                AppOverrideSettingsFragment::class.java.name)
                .putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                        Bundle().apply { putString(AppOverrideSettingsFragment.ARG_PACKAGE, app) })
        Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().use { controller ->
            controller.get().supportFragmentManager.executePendingTransactions()
            var fragment = controller.get().supportFragmentManager.fragments.single() as AppOverrideSettingsFragment
            val learning = fragment.findPreference<SwitchPreference>(AppOverrides.key(app, AppOverrides.LEARNING))!!
            assertFalse(learning.isEnabled)
            val row = LayoutInflater.from(controller.get()).inflate(learning.layoutResource, null)
            val frame = row.findViewById<android.view.ViewGroup>(android.R.id.widget_frame)
            LayoutInflater.from(controller.get()).inflate(learning.widgetLayoutResource, frame)
            learning.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
            val toggle = row.findViewById<Switch>(0x01020040)
            assertNotNull(toggle)
            assertNotNull(toggle.thumbDrawable)
            assertNotNull(toggle.trackDrawable)
            fragment.findPreference<SwitchPreference>(AppOverrides.key(app, AppOverrides.ENABLED))!!.performClick()
            assertTrue(learning.isEnabled)
            assertFalse(learning.isChecked)
            controller.recreate()
            controller.get().supportFragmentManager.executePendingTransactions()
            fragment = controller.get().supportFragmentManager.fragments.single() as AppOverrideSettingsFragment
            fragment.findPreference<SwitchPreference>(AppOverrides.key(app, AppOverrides.ENABLED))!!.performClick()
            assertFalse(prefs.getBoolean(AppOverrides.key(app, AppOverrides.LEARNING), true))
        }
    }
    @Test fun rowOpensPageButOnlySwitchChangesOverride() {
        val intent = Intent().putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT,
                IncognitoModeRulesFragment::class.java.name)
        Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val fragment = activity.supportFragmentManager.fragments.single() as IncognitoModeRulesFragment
            var opened = 0
            val pref = AppOverridePreference(activity) { opened++ }.apply {
                key = AppOverrides.key(app, AppOverrides.ENABLED); title = "Browser"
            }
            fragment.preferenceScreen.addPreference(pref)
            pref.performClick()
            assertEquals(1, opened)
            assertFalse(pref.isChecked)
            val row = LayoutInflater.from(activity).inflate(pref.layoutResource, null)
            val frame = row.findViewById<android.view.ViewGroup>(android.R.id.widget_frame)
            LayoutInflater.from(activity).inflate(pref.widgetLayoutResource, frame)
            pref.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
            val toggle = row.findViewById<Switch>(0x01020040)
            assertNotNull(toggle)
            assertNotNull(toggle.thumbDrawable)
            assertNotNull(toggle.trackDrawable)
            toggle.performClick()
            assertTrue(pref.isChecked)
            assertTrue(AppOverrides.enabled(prefs, app))
            assertEquals(1, opened)
        }
    }
    @Test fun learningCanRemainEnabledWithoutVisibleSuggestionsOrAutocorrection() {
        put(AppOverrides.ENABLED, true); put(AppOverrides.LEARNING, true)
        put(AppOverrides.PERSONALIZED, true); put(AppOverrides.SUGGESTIONS, false)
        put(AppOverrides.CORRECTION, false)
        val v = values()
        assertFalse(v.mIncognitoModeEnabled)
        assertFalse(v.isSuggestionsEnabledPerUserSettings)
        assertFalse(v.mAutoCorrectionEnabledPerUserSettings)
        assertTrue(v.needsToLookupSuggestions()) // Compose and learn without displaying candidates.
    }
    @Test fun appPersonalizationSwitchDoesNotDeleteSharedHistory() {
        put(AppOverrides.ENABLED, true); put(AppOverrides.PERSONALIZED, false)
        val file = java.io.File(context.filesDir, "UserHistoryDictionary.en_US.dict").apply { writeText("shared history") }
        assertTrue(io.github.aroum.kazuar.inputmethod.latin.personalization.PersonalizationHelper.DictFilter(
                io.github.aroum.kazuar.inputmethod.latin.personalization.UserHistoryDictionary.NAME)
                .accept(context.filesDir, file.name))
        val service = Robolectric.buildService(io.github.aroum.kazuar.inputmethod.latin.LatinIME::class.java).get()
        val method = service.javaClass.getDeclaredMethod("refreshPersonalizationDictionarySession", SettingsValues::class.java)
        method.isAccessible = true
        method.invoke(service, values())
        assertEquals("shared history", file.readText())
    }
    @Test fun geminiPageLinksToItsGoogleHostedEditorWithoutEnablingAnotherApp() {
        val gemini = "com.google.android.apps.bard"
        val google = "com.google.android.googlequicksearchbox"
        val intent = Intent().putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT,
                AppOverrideSettingsFragment::class.java.name)
                .putExtra(PreferenceHostActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                        Bundle().apply { putString(AppOverrideSettingsFragment.ARG_PACKAGE, gemini) })
        Robolectric.buildActivity(SettingsActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.supportFragmentManager.executePendingTransactions()
            val fragment = activity.supportFragmentManager.fragments.single() as AppOverrideSettingsFragment
            val link = fragment.findPreference<androidx.preference.Preference>("app_override_editor_host")!!
            link.performClick()
            activity.supportFragmentManager.executePendingTransactions()
            val editor = activity.supportFragmentManager.fragments.filterIsInstance<AppOverrideSettingsFragment>()
                    .single { it.arguments?.getString(AppOverrideSettingsFragment.ARG_PACKAGE) == google }
            assertNotNull(editor.findPreference<SwitchPreference>(AppOverrides.key(google, AppOverrides.ENABLED)))
            assertFalse(AppOverrides.enabled(prefs, google))
            assertFalse(AppOverrides.enabled(prefs, gemini))
        }
    }
    @Test fun absentOrInvalidPackageNeverEnablesOverride() {
        assertFalse(AppOverrides.enabled(prefs, null))
        assertFalse(AppOverrides.enabled(prefs, "../../other"))
        assertFalse(AppOverrides.enabled(prefs, ""))
    }
}
