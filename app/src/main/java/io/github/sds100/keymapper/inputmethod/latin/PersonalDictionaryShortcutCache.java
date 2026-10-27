/*
 * Copyright (C) 2026 Kazuar Keyboard
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

package io.github.sds100.keymapper.inputmethod.latin;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;
import android.util.Log;

import io.github.sds100.keymapper.inputmethod.latin.common.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Caches personal dictionary shortcuts (e.g. "спс" -> "спасибо") in memory and
 * automatically refreshes when the system UserDictionary content provider changes.
 */
public final class PersonalDictionaryShortcutCache {
    private static final String TAG = PersonalDictionaryShortcutCache.class.getSimpleName();
    private static final String ALL_LANGUAGES = "";

    private static final String[] PROJECTION = new String[] {
            Words.WORD,
            Words.SHORTCUT,
            Words.LOCALE
    };

    private static final Map<String, String> DEFAULT_SHORTCUTS_EXACT = new HashMap<>();
    private static final Map<String, String> DEFAULT_SHORTCUTS_LOWER = new HashMap<>();

    static {
        DEFAULT_SHORTCUTS_EXACT.put("AOSP", "Android Open Source Project");
        DEFAULT_SHORTCUTS_EXACT.put("aosp", "android open source project");
        DEFAULT_SHORTCUTS_EXACT.put("км", "километр");
        DEFAULT_SHORTCUTS_EXACT.put("спс", "спасибо");

        DEFAULT_SHORTCUTS_LOWER.put("aosp", "Android Open Source Project");
        DEFAULT_SHORTCUTS_LOWER.put("км", "километр");
        DEFAULT_SHORTCUTS_LOWER.put("спс", "спасибо");
    }

    private static volatile boolean sNeedsReload = true;
    private static boolean sObserverRegistered = false;

    // Language code ("ru", "en", or "" for all languages) -> (shortcut -> expansion)
    private static final Map<String, Map<String, String>> sExactShortcutsByLang = new HashMap<>();
    private static final Map<String, Map<String, String>> sLowerShortcutsByLang = new HashMap<>();

    private PersonalDictionaryShortcutCache() {
    }

    public static synchronized void invalidate() {
        sNeedsReload = true;
    }

    private static synchronized void ensureLoaded(final Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        if (!sObserverRegistered) {
            try {
                final ContentObserver observer = new ContentObserver(null) {
                    @Override
                    public void onChange(final boolean selfChange) {
                        sNeedsReload = true;
                    }

                    @Override
                    public void onChange(final boolean selfChange, final Uri uri) {
                        sNeedsReload = true;
                    }
                };
                appContext.getContentResolver().registerContentObserver(
                        Words.CONTENT_URI, true, observer);
                sObserverRegistered = true;
            } catch (final Exception e) {
                Log.w(TAG, "Failed to register ContentObserver for UserDictionary", e);
            }
        }

        if (!sNeedsReload) {
            return;
        }

        sExactShortcutsByLang.clear();
        sLowerShortcutsByLang.clear();

        try (final Cursor cursor = appContext.getContentResolver().query(
                Words.CONTENT_URI, PROJECTION,
                Words.SHORTCUT + " IS NOT NULL AND " + Words.SHORTCUT + " != ''",
                null, null)) {
            if (cursor != null) {
                final int wordIdx = cursor.getColumnIndex(Words.WORD);
                final int shortcutIdx = cursor.getColumnIndex(Words.SHORTCUT);
                final int localeIdx = cursor.getColumnIndex(Words.LOCALE);
                while (cursor.moveToNext()) {
                    if (wordIdx < 0 || shortcutIdx < 0) {
                        break;
                    }
                    final String word = cursor.getString(wordIdx);
                    final String shortcut = cursor.getString(shortcutIdx);
                    if (TextUtils.isEmpty(word) || TextUtils.isEmpty(shortcut)) {
                        continue;
                    }
                    final String rawLocale = localeIdx >= 0 ? cursor.getString(localeIdx) : null;
                    final String langKey = extractBaseLanguage(rawLocale);

                    Map<String, String> exactMap = sExactShortcutsByLang.get(langKey);
                    if (exactMap == null) {
                        exactMap = new HashMap<>();
                        sExactShortcutsByLang.put(langKey, exactMap);
                    }
                    exactMap.put(shortcut, word);

                    Map<String, String> lowerMap = sLowerShortcutsByLang.get(langKey);
                    if (lowerMap == null) {
                        lowerMap = new HashMap<>();
                        sLowerShortcutsByLang.put(langKey, lowerMap);
                    }
                    lowerMap.put(shortcut.toLowerCase(Locale.ROOT), word);
                }
            }
        } catch (final Exception e) {
            Log.w(TAG, "Failed to load shortcuts from UserDictionary", e);
        }

        sNeedsReload = false;
    }

    public static synchronized String getShortcutReplacement(
            final Context context, final String typedWord, final Locale locale) {
        if (TextUtils.isEmpty(typedWord)) {
            return null;
        }
        ensureLoaded(context);

        final Locale effectiveLocale = locale != null ? locale : Locale.getDefault();
        final String langKey = extractBaseLanguage(effectiveLocale.getLanguage());

        // 1. Check exact match in current language, then in all-languages dictionary
        final Map<String, String> exactLangMap = sExactShortcutsByLang.get(langKey);
        if (exactLangMap != null) {
            final String exact = exactLangMap.get(typedWord);
            if (exact != null) {
                return exact;
            }
        }
        final Map<String, String> exactAllMap = sExactShortcutsByLang.get(ALL_LANGUAGES);
        if (exactAllMap != null) {
            final String exact = exactAllMap.get(typedWord);
            if (exact != null) {
                return exact;
            }
        }

        // 2. Check case-insensitive match in current language, then in all-languages dictionary
        final String lowerTyped = typedWord.toLowerCase(effectiveLocale);
        final Map<String, String> lowerLangMap = sLowerShortcutsByLang.get(langKey);
        if (lowerLangMap != null) {
            final String match = lowerLangMap.get(lowerTyped);
            if (match != null) {
                return adjustCase(typedWord, match, effectiveLocale);
            }
        }
        final Map<String, String> lowerAllMap = sLowerShortcutsByLang.get(ALL_LANGUAGES);
        if (lowerAllMap != null) {
            final String match = lowerAllMap.get(lowerTyped);
            if (match != null) {
                return adjustCase(typedWord, match, effectiveLocale);
            }
        }

        // 3. Fallback to built-in default shortcuts
        final String defaultExact = DEFAULT_SHORTCUTS_EXACT.get(typedWord);
        if (defaultExact != null) {
            return defaultExact;
        }
        final String defaultLower = DEFAULT_SHORTCUTS_LOWER.get(lowerTyped);
        if (defaultLower != null) {
            return adjustCase(typedWord, defaultLower, effectiveLocale);
        }

        return null;
    }

    private static String adjustCase(
            final String typedWord, final String replacement, final Locale locale) {
        if (TextUtils.isEmpty(typedWord) || TextUtils.isEmpty(replacement)) {
            return replacement;
        }
        final int firstCp = typedWord.codePointAt(0);
        if (!Character.isUpperCase(firstCp)) {
            return replacement;
        }
        if (typedWord.codePointCount(0, typedWord.length()) > 1
                && StringUtils.isIdenticalAfterUpcase(typedWord)) {
            return replacement.toUpperCase(locale);
        }
        return StringUtils.capitalizeFirstCodePoint(replacement, locale);
    }

    private static String extractBaseLanguage(final String localeStr) {
        if (TextUtils.isEmpty(localeStr)) {
            return ALL_LANGUAGES;
        }
        int sep = localeStr.indexOf('_');
        if (sep < 0) {
            sep = localeStr.indexOf('-');
        }
        final String base = sep > 0 ? localeStr.substring(0, sep) : localeStr;
        return base.toLowerCase(Locale.ROOT);
    }
}
