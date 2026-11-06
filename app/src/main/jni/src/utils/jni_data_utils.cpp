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

#include "utils/jni_data_utils.h"

#include "utils/int_array_view.h"

namespace latinime {

const int JniDataUtils::CODE_POINT_REPLACEMENT_CHARACTER = 0xFFFD;
const int JniDataUtils::CODE_POINT_NULL = 0;

/* static */ void JniDataUtils::outputWordProperty(JNIEnv *const env,
        const WordProperty &wordProperty, jintArray outCodePoints, jbooleanArray outFlags,
        jintArray outProbabilityInfo, jobject outNgramPrevWordsArray,
        jobject outNgramPrevWordIsBeginningOfSentenceArray, jobject outNgramTargets,
        jobject outNgramProbabilities, jobject outShortcutTargets,
        jobject outShortcutProbabilities) {
    JniLocalFrame frame(env);
    if (!frame.valid() || !JniDataUtils::hasArrayRange(env, outFlags, 0, 5)
            || !JniDataUtils::hasArrayRange(env, outProbabilityInfo, 0, 4)
            || !outNgramPrevWordsArray || !outNgramPrevWordIsBeginningOfSentenceArray
            || !outNgramTargets || !outNgramProbabilities || !outShortcutTargets
            || !outShortcutProbabilities) return;
    const CodePointArrayView codePoints = wordProperty.getCodePoints();
    JniDataUtils::outputCodePoints(env, outCodePoints, 0 /* start */,
            MAX_WORD_LENGTH /* maxLength */, codePoints.data(), codePoints.size(),
            false /* needsNullTermination */);
    const UnigramProperty &unigramProperty = wordProperty.getUnigramProperty();
    const std::vector<NgramProperty> &ngrams = wordProperty.getNgramProperties();
    jboolean flags[] = {unigramProperty.isNotAWord(), unigramProperty.isPossiblyOffensive(),
            !ngrams.empty(), unigramProperty.hasShortcuts(),
            unigramProperty.representsBeginningOfSentence()};
    env->SetBooleanArrayRegion(outFlags, 0 /* start */, NELEMS(flags), flags);
    const HistoricalInfo &historicalInfo = unigramProperty.getHistoricalInfo();
    int probabilityInfo[] = {unigramProperty.getProbability(), historicalInfo.getLegacyTimestamp(),
            historicalInfo.getLevel(), historicalInfo.getCount()};
    env->SetIntArrayRegion(outProbabilityInfo, 0 /* start */, NELEMS(probabilityInfo),
            probabilityInfo);

    jclass integerClass = env->FindClass("java/lang/Integer");
    if (!integerClass || env->ExceptionCheck()) return;
    jmethodID intToIntegerConstructorId = env->GetMethodID(integerClass, "<init>", "(I)V");
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    if (!intToIntegerConstructorId || !arrayListClass || env->ExceptionCheck()) return;
    jmethodID addMethodId = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    // Output ngrams.
    jclass intArrayClass = env->FindClass("[I");
    if (!addMethodId || !intArrayClass || env->ExceptionCheck()) return;
    for (const auto &ngramProperty : ngrams) {
        JniLocalFrame ngramFrame(env);
        if (!ngramFrame.valid()) return;
        const NgramContext *const ngramContext = ngramProperty.getNgramContext();
        jobjectArray prevWordWordCodePointsArray = env->NewObjectArray(
                ngramContext->getPrevWordCount(), intArrayClass, nullptr);
        jbooleanArray prevWordIsBeginningOfSentenceArray =
                env->NewBooleanArray(ngramContext->getPrevWordCount());
        for (size_t i = 0; i < ngramContext->getPrevWordCount(); ++i) {
            const CodePointArrayView codePoints = ngramContext->getNthPrevWordCodePoints(i + 1);
            jintArray prevWordCodePoints = env->NewIntArray(codePoints.size());
            JniDataUtils::outputCodePoints(env, prevWordCodePoints, 0 /* start */,
                    codePoints.size(), codePoints.data(), codePoints.size(),
                    false /* needsNullTermination */);
            env->SetObjectArrayElement(prevWordWordCodePointsArray, i, prevWordCodePoints);
            env->DeleteLocalRef(prevWordCodePoints);
            JniDataUtils::putBooleanToArray(env, prevWordIsBeginningOfSentenceArray, i,
                    ngramContext->isNthPrevWordBeginningOfSentence(i + 1));
        }
        env->CallBooleanMethod(outNgramPrevWordsArray, addMethodId, prevWordWordCodePointsArray);
        env->CallBooleanMethod(outNgramPrevWordIsBeginningOfSentenceArray, addMethodId,
                prevWordIsBeginningOfSentenceArray);
        env->DeleteLocalRef(prevWordWordCodePointsArray);
        env->DeleteLocalRef(prevWordIsBeginningOfSentenceArray);

        const std::vector<int> *const targetWordCodePoints = ngramProperty.getTargetCodePoints();
        jintArray targetWordCodePointArray = env->NewIntArray(targetWordCodePoints->size());
        JniDataUtils::outputCodePoints(env, targetWordCodePointArray, 0 /* start */,
                targetWordCodePoints->size(), targetWordCodePoints->data(),
                targetWordCodePoints->size(), false /* needsNullTermination */);
        env->CallBooleanMethod(outNgramTargets, addMethodId, targetWordCodePointArray);
        env->DeleteLocalRef(targetWordCodePointArray);

        if (env->ExceptionCheck()) return;
        const HistoricalInfo &ngramHistoricalInfo = ngramProperty.getHistoricalInfo();
        int bigramProbabilityInfo[] = {ngramProperty.getProbability(),
                ngramHistoricalInfo.getLegacyTimestamp(), ngramHistoricalInfo.getLevel(),
                ngramHistoricalInfo.getCount()};
        jintArray bigramProbabilityInfoArray = env->NewIntArray(NELEMS(bigramProbabilityInfo));
        env->SetIntArrayRegion(bigramProbabilityInfoArray, 0 /* start */,
                NELEMS(bigramProbabilityInfo), bigramProbabilityInfo);
        env->CallBooleanMethod(outNgramProbabilities, addMethodId, bigramProbabilityInfoArray);
        env->DeleteLocalRef(bigramProbabilityInfoArray);
    }

    // Output shortcuts.
    for (const auto &shortcut : unigramProperty.getShortcuts()) {
        JniLocalFrame shortcutFrame(env);
        if (!shortcutFrame.valid()) return;
        const std::vector<int> *const targetCodePoints = shortcut.getTargetCodePoints();
        jintArray shortcutTargetCodePointArray = env->NewIntArray(targetCodePoints->size());
        JniDataUtils::outputCodePoints(env, shortcutTargetCodePointArray, 0 /* start */,
                targetCodePoints->size(), targetCodePoints->data(), targetCodePoints->size(),
                false /* needsNullTermination */);
        env->CallBooleanMethod(outShortcutTargets, addMethodId, shortcutTargetCodePointArray);
        env->DeleteLocalRef(shortcutTargetCodePointArray);
        jobject integerProbability = env->NewObject(integerClass, intToIntegerConstructorId,
                shortcut.getProbability());
        env->CallBooleanMethod(outShortcutProbabilities, addMethodId, integerProbability);
        env->DeleteLocalRef(integerProbability);
    }
    if (env->ExceptionCheck()) return;
    env->DeleteLocalRef(intArrayClass);
    env->DeleteLocalRef(integerClass);
    env->DeleteLocalRef(arrayListClass);
}

} // namespace latinime
