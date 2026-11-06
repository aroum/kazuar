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

#define LOG_TAG "LatinIME: jni: BinaryDictionaryUtils"

#include "io_github_aroum_kazuar_inputmethod_latin_BinaryDictionaryUtils.h"

#include "defines.h"
#include <climits>
#include "dictionary/utils/dict_file_writing_utils.h"
#include "jni.h"
#include "jni_common.h"
#include "utils/autocorrection_threshold_utils.h"
#include "utils/char_utils.h"
#include "utils/jni_data_utils.h"
#include "utils/time_keeper.h"

namespace latinime {

static jboolean latinime_BinaryDictionaryUtils_createEmptyDictFile(JNIEnv *env, jclass clazz,
        jstring filePath, jlong dictVersion, jstring locale, jobjectArray attributeKeyStringArray,
        jobjectArray attributeValueStringArray) {
    if (!filePath) return false;
    const jsize filePathUtf8Length = env->GetStringUTFLength(filePath);
    if (filePathUtf8Length <= 0 || filePathUtf8Length >= PATH_MAX) return false;
    std::vector<char> filePathChars(filePathUtf8Length + 1, 0);
    env->GetStringUTFRegion(filePath, 0, env->GetStringLength(filePath), filePathChars.data());
    filePathChars[filePathUtf8Length] = '\0';

    if (!locale) return 0;
    const jsize localeUtf8Length = env->GetStringUTFLength(locale);
    if (localeUtf8Length > 128) return 0;
    std::vector<char> localeChars(localeUtf8Length + 1, 0);
    env->GetStringUTFRegion(locale, 0, env->GetStringLength(locale), localeChars.data());
    localeChars[localeUtf8Length] = '\0';
    std::vector<int> localeCodePoints;
    HeaderReadWriteUtils::insertCharactersIntoVector(localeChars.data(), &localeCodePoints);

    if (!attributeKeyStringArray || !attributeValueStringArray) return 0;
    const int keyCount = env->GetArrayLength(attributeKeyStringArray);
    const int valueCount = env->GetArrayLength(attributeValueStringArray);
    if (keyCount != valueCount) {
        return false;
    }
    bool validAttributes = false;
    DictionaryHeaderStructurePolicy::AttributeMap attributeMap =
            JniDataUtils::constructAttributeMap(env, attributeKeyStringArray,
                    attributeValueStringArray, &validAttributes);
    if (!validAttributes || env->ExceptionCheck()) return 0;
    return DictFileWritingUtils::createEmptyDictFile(filePathChars.data(), static_cast<int>(dictVersion),
            localeCodePoints, &attributeMap);
}

static jfloat latinime_BinaryDictionaryUtils_calcNormalizedScore(JNIEnv *env, jclass clazz,
        jintArray before, jintArray after, jint score) {
    if (!before || !after) return 0.0f;
    jsize beforeLength = env->GetArrayLength(before);
    jsize afterLength = env->GetArrayLength(after);
    if (beforeLength > 4096 || afterLength > 4096) return 0.0f;
    std::vector<int> beforeCodePoints(beforeLength), afterCodePoints(afterLength);
    env->GetIntArrayRegion(before, 0, beforeLength, beforeCodePoints.data());
    env->GetIntArrayRegion(after, 0, afterLength, afterCodePoints.data());
    return AutocorrectionThresholdUtils::calcNormalizedScore(beforeCodePoints.data(), beforeLength,
            afterCodePoints.data(), afterLength, score);
}

static int latinime_BinaryDictionaryUtils_setCurrentTimeForTest(JNIEnv *env, jclass clazz,
        jint currentTime) {
    if (currentTime >= 0) {
        TimeKeeper::startTestModeWithForceCurrentTime(currentTime);
    } else {
        TimeKeeper::stopTestMode();
    }
    TimeKeeper::setCurrentTime();
    return HistoricalInfo(TimeKeeper::peekCurrentTime(), 0, 0).getLegacyTimestamp();
}

static const JNINativeMethod sMethods[] = {
    {
        const_cast<char *>("createEmptyDictFileNative"),
        const_cast<char *>(
                "(Ljava/lang/String;JLjava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(latinime_BinaryDictionaryUtils_createEmptyDictFile)
    },
    {
        const_cast<char *>("calcNormalizedScoreNative"),
        const_cast<char *>("([I[II)F"),
        reinterpret_cast<void *>(latinime_BinaryDictionaryUtils_calcNormalizedScore)
    },
    {
        const_cast<char *>("setCurrentTimeForTestNative"),
        const_cast<char *>("(I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionaryUtils_setCurrentTimeForTest)
    }
};

int register_BinaryDictionaryUtils(JNIEnv *env) {
    const char *const kClassPathName = "io/github/aroum/kazuar/inputmethod/latin/utils/BinaryDictionaryUtils";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
