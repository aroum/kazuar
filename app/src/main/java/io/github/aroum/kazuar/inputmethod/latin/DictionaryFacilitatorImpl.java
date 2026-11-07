/*
7 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.aroum.kazuar.inputmethod.latin;

import android.Manifest;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import io.github.aroum.kazuar.inputmethod.annotations.UsedForTesting;
import io.github.aroum.kazuar.inputmethod.keyboard.Keyboard;
import io.github.aroum.kazuar.inputmethod.latin.NgramContext.WordInfo;
import io.github.aroum.kazuar.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import io.github.aroum.kazuar.inputmethod.latin.common.ComposedData;
import io.github.aroum.kazuar.inputmethod.latin.common.Constants;
import io.github.aroum.kazuar.inputmethod.latin.common.StringUtils;
import io.github.aroum.kazuar.inputmethod.latin.permissions.PermissionsUtil;
import io.github.aroum.kazuar.inputmethod.latin.personalization.UserHistoryDictionary;
import io.github.aroum.kazuar.inputmethod.latin.settings.SettingsValuesForSuggestion;
import io.github.aroum.kazuar.inputmethod.latin.utils.ExecutorUtils;
import io.github.aroum.kazuar.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Facilitates interaction with different kinds of dictionaries. Provides APIs
 * to instantiate and select the correct dictionaries (based on language or account),
 * update entries and fetch suggestions.
 *
 * Currently AndroidSpellCheckerService and LatinIME both use DictionaryFacilitator as
 * a client for interacting with dictionaries.
 */
public class DictionaryFacilitatorImpl implements DictionaryFacilitator {
    // TODO: Consolidate dictionaries in native code.
    public static final String TAG = DictionaryFacilitatorImpl.class.getSimpleName();

    // HACK: This threshold is being used when adding a capitalized entry in the User History
    // dictionary.
    private static final int CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140;

    private static final int MAX_CACHED_LOCALES = 2;
    private final ConcurrentHashMap<String, DictionaryGroup> mDictionaryGroups =
            new ConcurrentHashMap<>();
    private DictionaryGroup mDictionaryGroup = new DictionaryGroup();
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionaries = new CountDownLatch(0);
    // To synchronize assigning mDictionaryGroup to ensure closing dictionaries.
    private final Object mLock = new Object();

    private static String getLocaleKey(@Nullable final Locale locale) {
        if (locale == null) return "";
        return locale.toString().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private DictionaryGroup findDictionaryGroupForLocale(@Nullable final Locale locale) {
        if (locale == null) return null;
        final String key = getLocaleKey(locale);
        final DictionaryGroup exact = mDictionaryGroups.get(key);
        if (exact != null) {
            return exact;
        }
        final String lang = locale.getLanguage();
        for (final DictionaryGroup dg : mDictionaryGroups.values()) {
            if (dg.mLocale != null && dg.mLocale.getLanguage().equalsIgnoreCase(lang)) {
                return dg;
            }
        }
        return null;
    }

    public static final Map<String, Class<? extends ExpandableBinaryDictionary>>
            DICT_TYPE_TO_CLASS = new HashMap<>();

    static {
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER_HISTORY, UserHistoryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER, UserBinaryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTACTS, ContactsBinaryDictionary.class);
    }


    private LruCache<String, Boolean> mValidSpellingWordReadCache;
    private LruCache<String, Boolean> mValidSpellingWordWriteCache;

    @Override
    public void setValidSpellingWordReadCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordReadCache = cache;
    }

    @Override
    public void setValidSpellingWordWriteCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordWriteCache = cache;
    }

    @Override
    public boolean isForLocale(final Locale locale) {
        return locale != null && locale.equals(mDictionaryGroup.mLocale);
    }

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    public boolean isForAccount(@Nullable final String account) {
        return TextUtils.equals(mDictionaryGroup.mAccount, account);
    }

    /**
     * A group of dictionaries that work together for a single language.
     */
    private static class DictionaryGroup {
        // TODO: Add null analysis annotations.
        // TODO: Run evaluation to determine a reasonable value for these constants. The current
        // values are ad-hoc and chosen without any particular care or methodology.
        public static final float WEIGHT_FOR_MOST_PROBABLE_LANGUAGE = 1.0f;
        public static final float WEIGHT_FOR_GESTURING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.95f;
        public static final float WEIGHT_FOR_TYPING_IN_NOT_MOST_PROBABLE_LANGUAGE = 0.6f;

        /**
         * The locale associated with the dictionary group.
         */
        @Nullable public final Locale mLocale;

        /**
         * The user account associated with the dictionary group.
         */
        @Nullable public final String mAccount;

        @Nullable private Dictionary mMainDict;
        // Confidence that the most probable language is actually the language the user is
        // typing in. For now, this is simply the number of times a word from this language
        // has been committed in a row.
        private int mConfidence = 0;

        public float mWeightForTypingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
        public float mWeightForGesturingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
        public final ConcurrentHashMap<String, ExpandableBinaryDictionary> mSubDictMap =
                new ConcurrentHashMap<>();

        public DictionaryGroup() {
            this(null /* locale */, null /* mainDict */, null /* account */,
                    Collections.<String, ExpandableBinaryDictionary>emptyMap() /* subDicts */);
        }

        public DictionaryGroup(@Nullable final Locale locale,
                @Nullable final Dictionary mainDict,
                @Nullable final String account,
                final Map<String, ExpandableBinaryDictionary> subDicts) {
            mLocale = locale;
            mAccount = account;
            // The main dictionary can be asynchronously loaded.
            setMainDict(mainDict);
            for (final Map.Entry<String, ExpandableBinaryDictionary> entry : subDicts.entrySet()) {
                setSubDict(entry.getKey(), entry.getValue());
            }
        }

        private void setSubDict(final String dictType, final ExpandableBinaryDictionary dict) {
            if (dict != null) {
                mSubDictMap.put(dictType, dict);
            }
        }

        public void setMainDict(final Dictionary mainDict) {
            // Close old dictionary if exists. Main dictionary can be assigned multiple times.
            final Dictionary oldDict = mMainDict;
            mMainDict = mainDict;
            if (oldDict != null && mainDict != oldDict) {
                oldDict.close();
            }
        }

        public Dictionary getDict(final String dictType) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict;
            }
            return getSubDict(dictType);
        }

        public ExpandableBinaryDictionary getSubDict(final String dictType) {
            return mSubDictMap.get(dictType);
        }

        public boolean hasDict(final String dictType, @Nullable final String account) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict != null;
            }
            if (Dictionary.TYPE_USER_HISTORY.equals(dictType) &&
                    !TextUtils.equals(account, mAccount)) {
                // If the dictionary type is user history, & if the account doesn't match,
                // return immediately. If the account matches, continue looking it up in the
                // sub dictionary map.
                return false;
            }
            return mSubDictMap.containsKey(dictType);
        }

        public void closeDict(final String dictType) {
            final Dictionary dict;
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                dict = mMainDict;
            } else {
                dict = mSubDictMap.remove(dictType);
            }
            if (dict != null) {
                dict.close();
            }
        }
    }

    public DictionaryFacilitatorImpl() {
    }

    @Override
    public void onStartInput() {
    }

    @Override
    public void onFinishInput(Context context) {
        for (final DictionaryGroup group : mDictionaryGroups.values()) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                final Dictionary dict = group.getDict(dictType);
                if (dict != null) dict.onFinishInput();
            }
        }
        if (mDictionaryGroups.isEmpty() && mDictionaryGroup != null) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                final Dictionary dict = mDictionaryGroup.getDict(dictType);
                if (dict != null) dict.onFinishInput();
            }
        }
    }

    @Override
    public boolean isActive() {
        return mDictionaryGroup.mLocale != null;
    }

    @Override
    public Locale getLocale() {
        return mDictionaryGroup.mLocale;
    }

    @Override
    public boolean usesContacts() {
        return mDictionaryGroup.getSubDict(Dictionary.TYPE_CONTACTS) != null;
    }

    @Override
    public String getAccount() {
        return null;
    }

    @Nullable
    private static ExpandableBinaryDictionary getSubDict(final String dictType,
            final Context context, final Locale locale, final File dictFile,
            final String dictNamePrefix, @Nullable final String account) {
        if (Dictionary.TYPE_USER_HISTORY.equals(dictType)) {
            return UserHistoryDictionary.getDictionary(context, locale, dictFile, dictNamePrefix, account);
        } else if (Dictionary.TYPE_USER.equals(dictType)) {
            return UserBinaryDictionary.getDictionary(context, locale, dictFile, dictNamePrefix, account);
        } else if (Dictionary.TYPE_CONTACTS.equals(dictType)) {
            return ContactsBinaryDictionary.getDictionary(context, locale, dictFile, dictNamePrefix, account);
        }
        return null;
    }

    @Nullable
    static DictionaryGroup findDictionaryGroupWithLocale(final DictionaryGroup dictionaryGroup,
            final Locale locale) {
        return locale.equals(dictionaryGroup.mLocale) ? dictionaryGroup : null;
    }

    @Override
    public void resetDictionaries(
            final Context originalContext,
            final Locale newLocale,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener) {
        if (newLocale == null) {
            return;
        }

        // Personal history stays credential-protected. Before first unlock only use
        // device-protected caches for the dictionaries needed on the lock screen.
        final Context context = androidx.core.os.UserManagerCompat.isUserUnlocked(originalContext)
                ? originalContext : androidx.core.content.ContextCompat.createDeviceProtectedStorageContext(originalContext);

        final HashSet<String> subDictTypesToUse = new HashSet<>();
        subDictTypesToUse.add(Dictionary.TYPE_USER);

        // Do not use contacts dictionary if we do not have permissions to read contacts.
        final boolean contactsPermissionGranted = PermissionsUtil.checkAllPermissionsGranted(
                context, Manifest.permission.READ_CONTACTS);
        if (useContactsDict && contactsPermissionGranted) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        if (usePersonalizedDicts && androidx.core.os.UserManagerCompat.isUserUnlocked(context)) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
        }

        final DictionaryGroup existingGroup = findDictionaryGroupForLocale(newLocale);
        final boolean canReuseEntireGroup = !forceReloadMainDictionary
                && existingGroup != null
                && TextUtils.equals(existingGroup.mAccount, account)
                && existingGroup.hasDict(Dictionary.TYPE_MAIN, account)
                && existingGroup.mSubDictMap.keySet().equals(subDictTypesToUse);

        if (canReuseEntireGroup) {
            synchronized (mLock) {
                mDictionaryGroup = existingGroup;
            }
            if (listener != null) {
                listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
            }
            if (mValidSpellingWordReadCache != null) {
                mValidSpellingWordReadCache.evictAll();
            }
            if (mValidSpellingWordWriteCache != null) {
                mValidSpellingWordWriteCache.evictAll();
            }
            return;
        }

        final Dictionary mainDict;
        if (forceReloadMainDictionary || existingGroup == null
                || !existingGroup.hasDict(Dictionary.TYPE_MAIN, account)) {
            mainDict = null;
        } else {
            mainDict = existingGroup.getDict(Dictionary.TYPE_MAIN);
        }

        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();
        for (final String subDictType : subDictTypesToUse) {
            final ExpandableBinaryDictionary subDict;
            if (existingGroup == null || !existingGroup.hasDict(subDictType, account)) {
                // Create a new dictionary.
                subDict = getSubDict(subDictType, context, newLocale, null /* dictFile */,
                        dictNamePrefix, account);
            } else {
                // Reuse the existing dictionary
                subDict = existingGroup.getSubDict(subDictType);
            }
            subDicts.put(subDictType, subDict);
        }
        final DictionaryGroup newDictionaryGroup =
                new DictionaryGroup(newLocale, mainDict, account, subDicts);

        final String localeKey = getLocaleKey(newLocale);
        mDictionaryGroups.put(localeKey, newDictionaryGroup);

        // Evict if we exceed MAX_CACHED_LOCALES
        if (mDictionaryGroups.size() > MAX_CACHED_LOCALES) {
            final String activeLocaleKey = mDictionaryGroup != null ? getLocaleKey(mDictionaryGroup.mLocale) : "";
            for (final Map.Entry<String, DictionaryGroup> entry : mDictionaryGroups.entrySet()) {
                final String key = entry.getKey();
                if (!key.equals(localeKey) && !key.equals(activeLocaleKey)) {
                    final DictionaryGroup groupToEvict = entry.getValue();
                    for (final String dictType : ALL_DICTIONARY_TYPES) {
                        groupToEvict.closeDict(dictType);
                    }
                    mDictionaryGroups.remove(key);
                    break;
                }
            }
        }

        // Replace Dictionaries.
        synchronized (mLock) {
            mDictionaryGroup = newDictionaryGroup;
            if (hasAtLeastOneUninitializedMainDictionary()) {
                asyncReloadUninitializedMainDictionaries(context, newLocale, listener);
            }
        }
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }

        if (mValidSpellingWordReadCache != null) {
            mValidSpellingWordReadCache.evictAll();
        }
        if (mValidSpellingWordWriteCache != null) {
            mValidSpellingWordWriteCache.evictAll();
        }
    }

    private void asyncReloadUninitializedMainDictionaries(final Context context,
            final Locale locale, final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionaries = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
            @Override
            public void run() {
                doReloadUninitializedMainDictionaries(
                        context, locale, listener, latchForWaitingLoadingMainDictionary);
            }
        });
    }

    void doReloadUninitializedMainDictionaries(final Context context, final Locale locale,
            final DictionaryInitializationListener listener,
            final CountDownLatch latchForWaitingLoadingMainDictionary) {
        final DictionaryGroup dictionaryGroup = findDictionaryGroupForLocale(locale);
        if (null == dictionaryGroup) {
            // This should never happen, but better safe than crashy
            Log.w(TAG, "Expected a dictionary group for " + locale + " but none found");
            return;
        }
        final Dictionary mainDict =
                DictionaryFactory.createMainDictionaryFromManager(context, locale);
        synchronized (mLock) {
            dictionaryGroup.setMainDict(mainDict);
        }
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }
        latchForWaitingLoadingMainDictionary.countDown();
    }

    @UsedForTesting
    public void resetDictionariesForTesting(final Context context, final Locale locale,
            final ArrayList<String> dictionaryTypes, final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes,
            @Nullable final String account) {
        Dictionary mainDictionary = null;
        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();

        for (final String dictType : dictionaryTypes) {
            if (dictType.equals(Dictionary.TYPE_MAIN)) {
                mainDictionary = DictionaryFactory.createMainDictionaryFromManager(context,
                        locale);
            } else {
                final File dictFile = dictionaryFiles.get(dictType);
                final ExpandableBinaryDictionary dict = getSubDict(
                        dictType, context, locale, dictFile, "" /* dictNamePrefix */, account);
                if (additionalDictAttributes.containsKey(dictType)) {
                    dict.clearAndFlushDictionaryWithAdditionalAttributes(
                            additionalDictAttributes.get(dictType));
                }
                if (dict == null) {
                    throw new RuntimeException("Unknown dictionary type: " + dictType);
                }
                dict.reloadDictionaryIfRequired();
                dict.waitAllTasksForTests();
                subDicts.put(dictType, dict);
            }
        }
        mDictionaryGroup = new DictionaryGroup(locale, mainDictionary, account, subDicts);
        mDictionaryGroups.put(getLocaleKey(locale), mDictionaryGroup);
    }

    public void closeDictionaries() {
        final List<DictionaryGroup> groupsToClose;
        synchronized (mLock) {
            groupsToClose = new ArrayList<>(mDictionaryGroups.values());
            if (mDictionaryGroups.isEmpty() && mDictionaryGroup != null) {
                groupsToClose.add(mDictionaryGroup);
            }
            mDictionaryGroups.clear();
            mDictionaryGroup = new DictionaryGroup();
        }
        for (final DictionaryGroup group : groupsToClose) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                group.closeDict(dictType);
            }
        }
    }

    @UsedForTesting
    public ExpandableBinaryDictionary getSubDictForTesting(final String dictName) {
        return mDictionaryGroup.getSubDict(dictName);
    }

    // The main dictionaries are loaded asynchronously.  Don't cache the return value
    // of these methods.
    public boolean hasAtLeastOneInitializedMainDictionary() {
        final Dictionary mainDict = mDictionaryGroup.getDict(Dictionary.TYPE_MAIN);
        return mainDict != null && mainDict.isInitialized();
    }

    public boolean hasAtLeastOneUninitializedMainDictionary() {
        final Dictionary mainDict = mDictionaryGroup.getDict(Dictionary.TYPE_MAIN);
        return mainDict == null || !mainDict.isInitialized();
    }

    public void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mLatchForWaitingLoadingMainDictionaries.await(timeout, unit);
    }

    @UsedForTesting
    public void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        waitForLoadingMainDictionaries(timeout, unit);
        for (final ExpandableBinaryDictionary dict : mDictionaryGroup.mSubDictMap.values()) {
            dict.waitAllTasksForTests();
        }
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        // Update the spelling cache before learning. Words that are not yet added to user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("addToUserHistory", suggestion);

        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);
        NgramContext ngramContextForCurrentWord = ngramContext;
        for (int i = 0; i < words.length; i++) {
            final String currentWord = words[i];
            final boolean wasCurrentWordAutoCapitalized = (i == 0) && wasAutoCapitalized;
            addWordToUserHistory(mDictionaryGroup, ngramContextForCurrentWord, currentWord,
                    wasCurrentWordAutoCapitalized, (int) timeStampInSeconds,
                    blockPotentiallyOffensive);
            ngramContextForCurrentWord =
                    ngramContextForCurrentWord.getNextNgramContext(new WordInfo(currentWord));
        }
    }

    private void putWordIntoValidSpellingWordCache(
            @Nonnull final String caller,
            @Nonnull final String originalWord) {
        if (mValidSpellingWordWriteCache == null) {
            return;
        }

        final String lowerCaseWord = originalWord.toLowerCase(getLocale());
        final boolean lowerCaseValid = isValidSpellingWord(lowerCaseWord);
        mValidSpellingWordWriteCache.put(lowerCaseWord, lowerCaseValid);

        final String capitalWord =
                StringUtils.capitalizeFirstAndDowncaseRest(originalWord, getLocale());
        final boolean capitalValid;
        if (lowerCaseValid) {
            // The lower case form of the word is valid, so the upper case must be valid.
            capitalValid = true;
        } else {
            capitalValid = isValidSpellingWord(capitalWord);
        }
        mValidSpellingWordWriteCache.put(capitalWord, capitalValid);
    }

    private void addWordToUserHistory(final DictionaryGroup dictionaryGroup,
            final NgramContext ngramContext, final String word, final boolean wasAutoCapitalized,
            final int timeStampInSeconds, final boolean blockPotentiallyOffensive) {
        final ExpandableBinaryDictionary userHistoryDictionary =
                dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDictionary == null || !isForLocale(userHistoryDictionary.mLocale)) {
            return;
        }
        final int maxFreq = getFrequency(word);
        if (maxFreq == 0 && blockPotentiallyOffensive) {
            return;
        }
        final String lowerCasedWord = word.toLowerCase(dictionaryGroup.mLocale);
        final String secondWord;
        if (wasAutoCapitalized) {
            if (isValidSuggestionWord(word) && !isValidSuggestionWord(lowerCasedWord)) {
                // If the word was auto-capitalized and exists only as a capitalized word in the
                // dictionary, then we must not downcase it before registering it. For example,
                // the name of the contacts in start-of-sentence position would come here with the
                // wasAutoCapitalized flag: if we downcase it, we'd register a lower-case version
                // of that contact's name which would end up popping in suggestions.
                secondWord = word;
            } else {
                // If however the word is not in the dictionary, or exists as a lower-case word
                // only, then we consider that was a lower-case word that had been auto-capitalized.
                secondWord = lowerCasedWord;
            }
        } else {
            // HACK: We'd like to avoid adding the capitalized form of common words to the User
            // History dictionary in order to avoid suggesting them until the dictionary
            // consolidation is done.
            // TODO: Remove this hack when ready.
            final int lowerCaseFreqInMainDict = dictionaryGroup.hasDict(Dictionary.TYPE_MAIN,
                    null /* account */) ?
                    dictionaryGroup.getDict(Dictionary.TYPE_MAIN).getFrequency(lowerCasedWord) :
                    Dictionary.NOT_A_PROBABILITY;
            if (maxFreq < lowerCaseFreqInMainDict
                    && lowerCaseFreqInMainDict >= CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT) {
                // Use lower cased word as the word can be a distracter of the popular word.
                secondWord = lowerCasedWord;
            } else {
                secondWord = word;
            }
        }
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final boolean isValid = maxFreq > 0;
        UserHistoryDictionary.addToDictionary(userHistoryDictionary, ngramContext, secondWord,
                isValid, timeStampInSeconds);
    }

    private void removeWord(final String dictName, final String word) {
        final ExpandableBinaryDictionary dictionary = mDictionaryGroup.getSubDict(dictName);
        if (dictionary != null) {
            dictionary.removeUnigramEntryDynamically(word);
        }
    }

    @Override
    public void unlearnFromUserHistory(final String word,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final int eventType) {
        // TODO: Decide whether or not to remove the word on EVENT_BACKSPACE.
        if (eventType != Constants.EVENT_BACKSPACE) {
            removeWord(Dictionary.TYPE_USER_HISTORY, word);
        }

        // Update the spelling cache after unlearning. Words that are removed from user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("unlearnFromUserHistory", word.toLowerCase());
    }

    // TODO: Revise the way to fusion suggestion results.
    @Override
    @Nonnull public SuggestionResults getSuggestionResults(ComposedData composedData,
            NgramContext ngramContext, @Nonnull final Keyboard keyboard,
            SettingsValuesForSuggestion settingsValuesForSuggestion, int sessionId,
            int inputStyle) {
        long proximityInfoHandle = keyboard.getProximityInfo().getNativeProximityInfo();
        final SuggestionResults suggestionResults = new SuggestionResults(
                SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext(),
                false /* firstSuggestionExceedsConfidenceThreshold */);
        final float[] weightOfLangModelVsSpatialModel =
                new float[] { Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL };
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = mDictionaryGroup.getDict(dictType);
            if (null == dictionary) continue;
            final float weightForLocale = composedData.mIsBatchMode
                    ? mDictionaryGroup.mWeightForGesturingInLocale
                    : mDictionaryGroup.mWeightForTypingInLocale;
            final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                    dictionary.getSuggestions(composedData, ngramContext,
                            proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                            weightForLocale, weightOfLangModelVsSpatialModel);
            if (null == dictionarySuggestions) continue;
            suggestionResults.addAll(dictionarySuggestions);
            if (null != suggestionResults.mRawSuggestions) {
                suggestionResults.mRawSuggestions.addAll(dictionarySuggestions);
            }
        }
        return suggestionResults;
    }

    public boolean isValidSpellingWord(final String word) {
        if (mValidSpellingWordReadCache != null) {
            final Boolean cachedValue = mValidSpellingWordReadCache.get(word);
            if (cachedValue != null) {
                return cachedValue;
            }
        }

        return isValidWord(word, ALL_DICTIONARY_TYPES);
    }

    public boolean isValidSuggestionWord(final String word) {
        return isValidWord(word, ALL_DICTIONARY_TYPES);
    }

    private boolean isValidWord(final String word, final String[] dictionariesToCheck) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        if (mDictionaryGroup.mLocale == null) {
            return false;
        }
        for (final String dictType : dictionariesToCheck) {
            final Dictionary dictionary = mDictionaryGroup.getDict(dictType);
            // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
            // would be immutable once it's finished initializing, but concretely a null test is
            // probably good enough for the time being.
            if (null == dictionary) continue;
            if (dictionary.isValidWord(word)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getFrequency(final String word) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = Dictionary.NOT_A_PROBABILITY;
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = mDictionaryGroup.getDict(dictType);
            if (dictionary == null) continue;
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }

    private boolean clearSubDictionary(final String dictName) {
        final ExpandableBinaryDictionary dictionary = mDictionaryGroup.getSubDict(dictName);
        if (dictionary == null) {
            return false;
        }
        dictionary.clear();
        return true;
    }

    @Override
    public boolean clearUserHistoryDictionary(final Context context) {
        boolean cleared = false;
        for (final DictionaryGroup group : mDictionaryGroups.values()) {
            final ExpandableBinaryDictionary dictionary = group.getSubDict(Dictionary.TYPE_USER_HISTORY);
            if (dictionary != null) {
                dictionary.clear();
                cleared = true;
            }
        }
        return cleared || clearSubDictionary(Dictionary.TYPE_USER_HISTORY);
    }

    @Override
    public void dumpDictionaryForDebug(final String dictName) {
        final ExpandableBinaryDictionary dictToDump = mDictionaryGroup.getSubDict(dictName);
        if (dictToDump == null) {
            Log.e(TAG, "Cannot dump " + dictName + ". "
                    + "The dictionary is not being used for suggestion or cannot be dumped.");
            return;
        }
        dictToDump.dumpAllWordsForDebug();
    }

    @Override
    @Nonnull public List<DictionaryStats> getDictionaryStats(final Context context) {
        final ArrayList<DictionaryStats> statsOfEnabledSubDicts = new ArrayList<>();
        for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
            final ExpandableBinaryDictionary dictionary = mDictionaryGroup.getSubDict(dictType);
            if (dictionary == null) continue;
            statsOfEnabledSubDicts.add(dictionary.getDictionaryStats());
        }
        return statsOfEnabledSubDicts;
    }

    @Override
    public String dump(final Context context) {
        return "";
    }
}
