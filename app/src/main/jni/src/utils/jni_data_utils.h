/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef LATINIME_JNI_DATA_UTILS_H
#define LATINIME_JNI_DATA_UTILS_H

#include <vector>

#include "defines.h"
#include "dictionary/header/header_read_write_utils.h"
#include "dictionary/interface/dictionary_header_structure_policy.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/property/word_property.h"
#include "jni.h"
#include "utils/char_utils.h"
#include "utils/jni_local_frame.h"
#include "utils/unicode_utils.h"

namespace latinime {

class JniDataUtils {
 public:
    static bool hasArrayRange(JNIEnv *env, jarray array, int start, int count) {
        if (!env || !array || start < 0 || count < 0) return false;
        const int length = env->GetArrayLength(array);
        return start <= length && count <= length - start;
    }
    static void jintarrayToVector(JNIEnv *env, jintArray array, std::vector<int> *const outVector) {
        if (!array) {
            outVector->clear();
            return;
        }
        const jsize arrayLength = env->GetArrayLength(array);
        outVector->resize(arrayLength);
        env->GetIntArrayRegion(array, 0 /* start */, arrayLength, outVector->data());
    }

    static DictionaryHeaderStructurePolicy::AttributeMap constructAttributeMap(JNIEnv *env,
            jobjectArray attributeKeyStringArray, jobjectArray attributeValueStringArray, bool *success = nullptr) {
        if (success) *success = false;
        DictionaryHeaderStructurePolicy::AttributeMap attributeMap;
        if (!env || !attributeKeyStringArray || !attributeValueStringArray) return attributeMap;
        const int count = env->GetArrayLength(attributeKeyStringArray);
        if (count > 256 || env->GetArrayLength(attributeValueStringArray) != count) return attributeMap;
        for (int i = 0; i < count; ++i) {
            JniLocalFrame frame(env, 4);
            if (!frame.valid()) return {};
            jstring keyString = static_cast<jstring>(env->GetObjectArrayElement(attributeKeyStringArray, i));
            jstring valueString = static_cast<jstring>(env->GetObjectArrayElement(attributeValueStringArray, i));
            if (!keyString || !valueString || env->ExceptionCheck()) return {};
            const int keyLength = env->GetStringLength(keyString), valueLength = env->GetStringLength(valueString);
            if (keyLength <= 0 || keyLength >= 256 || valueLength >= 2048) return {};
            std::vector<jchar> key(keyLength), value(valueLength);
            env->GetStringRegion(keyString, 0, keyLength, key.data());
            if (valueLength) env->GetStringRegion(valueString, 0, valueLength, value.data());
            if (env->ExceptionCheck()) return {};
            attributeMap[UnicodeUtils::utf16ToCodePoints(key.data(), key.size())] =
                    UnicodeUtils::utf16ToCodePoints(value.data(), value.size());
        }
        if (success) *success = true;
        return attributeMap;
    }

    static void outputCodePoints(JNIEnv *env, jintArray intArrayToOutputCodePoints, const int start,
            const int maxLength, const int *const codePoints, const int codePointCount,
            const bool needsNullTermination) {
        if (!env || !intArrayToOutputCodePoints || maxLength < 0 || codePointCount < 0
                || (codePointCount && !codePoints) || !hasArrayRange(env, intArrayToOutputCodePoints, start, 0)) return;
        const int available = std::min(maxLength, env->GetArrayLength(intArrayToOutputCodePoints) - start);
        const int codePointBufSize = std::min(available, codePointCount);
        std::vector<int> outputCodePonts(codePointBufSize);
        int outputCodePointCount = 0;
        for (int i = 0; i < codePointBufSize; ++i) {
            const int codePoint = codePoints[i];
            int codePointToOutput = codePoint;
            if (!CharUtils::isInUnicodeSpace(codePoint)) {
                if (codePoint == CODE_POINT_BEGINNING_OF_SENTENCE) {
                    // Just skip Beginning-of-Sentence marker.
                    continue;
                }
                codePointToOutput = CODE_POINT_REPLACEMENT_CHARACTER;
            } else if (codePoint >= 0x01 && codePoint <= 0x1F) {
                // Control code.
                codePointToOutput = CODE_POINT_REPLACEMENT_CHARACTER;
            }
            outputCodePonts[outputCodePointCount++] = codePointToOutput;
        }
        if (outputCodePointCount) env->SetIntArrayRegion(intArrayToOutputCodePoints, start, outputCodePointCount,
                outputCodePonts.data());
        if (needsNullTermination && outputCodePointCount < available) {
            env->SetIntArrayRegion(intArrayToOutputCodePoints, start + outputCodePointCount,
                    1 /* len */, &CODE_POINT_NULL);
        }
    }

    static NgramContext constructNgramContext(JNIEnv *env, jobjectArray prevWordCodePointArrays,
            jbooleanArray isBeginningOfSentenceArray, const size_t prevWordCount) {
        int prevWordCodePoints[MAX_PREV_WORD_COUNT_FOR_N_GRAM][MAX_WORD_LENGTH] = {};
        int prevWordCodePointCount[MAX_PREV_WORD_COUNT_FOR_N_GRAM] = {};
        bool isBeginningOfSentence[MAX_PREV_WORD_COUNT_FOR_N_GRAM] = {};
        if (!env || !prevWordCodePointArrays || !isBeginningOfSentenceArray
                || prevWordCount > MAX_PREV_WORD_COUNT_FOR_N_GRAM
                || !hasArrayRange(env, prevWordCodePointArrays, 0, prevWordCount)
                || !hasArrayRange(env, isBeginningOfSentenceArray, 0, prevWordCount)) return NgramContext();
        for (size_t i = 0; i < prevWordCount; ++i) {
            JniLocalFrame frame(env, 2);
            if (!frame.valid()) return NgramContext();
            jintArray prevWord = static_cast<jintArray>(env->GetObjectArrayElement(prevWordCodePointArrays, i));
            if (env->ExceptionCheck()) return NgramContext();
            if (!prevWord) continue;
            const int length = env->GetArrayLength(prevWord);
            if (length > MAX_WORD_LENGTH) continue;
            if (length) env->GetIntArrayRegion(prevWord, 0, length, prevWordCodePoints[i]);
            jboolean bos = JNI_FALSE;
            env->GetBooleanArrayRegion(isBeginningOfSentenceArray, i, 1, &bos);
            if (env->ExceptionCheck()) return NgramContext();
            prevWordCodePointCount[i] = length;
            isBeginningOfSentence[i] = bos == JNI_TRUE;
        }
        return NgramContext(prevWordCodePoints, prevWordCodePointCount, isBeginningOfSentence, prevWordCount);
    }

    static void putBooleanToArray(JNIEnv *env, jbooleanArray array, const int index,
            const jboolean value) {
        if (hasArrayRange(env, array, index, 1)) env->SetBooleanArrayRegion(array, index, 1 /* len */, &value);
    }

    static void putIntToArray(JNIEnv *env, jintArray array, const int index, const int value) {
        if (hasArrayRange(env, array, index, 1)) env->SetIntArrayRegion(array, index, 1 /* len */, &value);
    }

    static void putFloatToArray(JNIEnv *env, jfloatArray array, const int index,
            const float value) {
        if (hasArrayRange(env, array, index, 1)) env->SetFloatArrayRegion(array, index, 1 /* len */, &value);
    }

    static void outputWordProperty(JNIEnv *const env, const WordProperty &wordProperty,
            jintArray outCodePoints, jbooleanArray outFlags, jintArray outProbabilityInfo,
            jobject outNgramPrevWordsArray, jobject outNgramPrevWordIsBeginningOfSentenceArray,
            jobject outNgramTargets, jobject outNgramProbabilities, jobject outShortcutTargets,
            jobject outShortcutProbabilities);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(JniDataUtils);

    static const int CODE_POINT_REPLACEMENT_CHARACTER;
    static const int CODE_POINT_NULL;
};
} // namespace latinime
#endif // LATINIME_JNI_DATA_UTILS_H
