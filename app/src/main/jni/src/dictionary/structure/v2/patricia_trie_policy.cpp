/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "dictionary/structure/v2/patricia_trie_policy.h"
#include <algorithm>

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "dictionary/interface/ngram_listener.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/structure/pt_common/dynamic_pt_reading_helper.h"
#include "dictionary/structure/pt_common/patricia_trie_reading_utils.h"
#include "dictionary/utils/binary_dictionary_bigrams_iterator.h"
#include "dictionary/utils/multi_bigram_map.h"
#include "dictionary/utils/probability_utils.h"
#include "utils/char_utils.h"

namespace latinime {

void PatriciaTriePolicy::createAndGetAllChildDicNodes(const DicNode *const dicNode,
        DicNodeVector *const childDicNodes) const {
    if (!dicNode->hasChildren()) {
        return;
    }
    int nextPos = dicNode->getChildrenPtNodeArrayPos();
    if (!isValidPos(nextPos)) {
        AKLOGE("Children PtNode array position is invalid. pos: %d, dict size: %zd",
                nextPos, mBuffer.size());
        mIsCorrupted = true;
        ASSERT(false);
        return;
    }
    const int childCount = PatriciaTrieReadingUtils::getPtNodeArraySizeAndAdvancePosition(
            mBuffer, &nextPos);
    if (childCount < 0) { mIsCorrupted = true; return; }
    for (int i = 0; i < childCount; i++) {
        if (!isValidPos(nextPos)) {
            AKLOGE("Child PtNode position is invalid. pos: %d, dict size: %zd, childCount: %d / %d",
                    nextPos, mBuffer.size(), i, childCount);
            mIsCorrupted = true;
            ASSERT(false);
            return;
        }
        nextPos = createAndGetLeavingChildNode(dicNode, nextPos, childDicNodes);
    }
}

int PatriciaTriePolicy::getCodePointsAndReturnCodePointCount(const int wordId,
        const int maxCodePointCount, int *const outCodePoints) const {
    return getCodePointsAndProbabilityAndReturnCodePointCount(wordId, maxCodePointCount,
            outCodePoints, nullptr /* outUnigramProbability */);
}
// v2 arrays are ordered breadth-first. Descend the last child array whose position
// does not exceed the requested node, retaining at most the caller's output capacity.
int PatriciaTriePolicy::getCodePointsAndProbabilityAndReturnCodePointCount(
        const int wordId, const int maxCodePointCount, int *const outCodePoints,
        int *const outUnigramProbability) const {
    if (outUnigramProbability) *outUnigramProbability = NOT_A_PROBABILITY;
    if (!outCodePoints || maxCodePointCount <= 0) return 0;
    const int limit = std::min(maxCodePointCount, MAX_WORD_LENGTH);
    const int target = getTerminalPtNodePosFromWordId(wordId);
    if (!isValidPos(target)) return 0;
    int arrayPos = getRootPosition();
    int word[MAX_WORD_LENGTH];
    int length = 0;
    for (int depth = 0; depth < MAX_WORD_LENGTH; ++depth) {
        int count = 0, pos = NOT_A_DICT_POS;
        if (!mPtNodeArrayReader.readPtNodeArrayInfoAndReturnIfValid(arrayPos, &count, &pos)) {
            mIsCorrupted = true;
            return 0;
        }
        // PtNodeParams deliberately has no assignment operator.
        int candidatePos = NOT_A_DICT_POS;
        int candidateChildren = NOT_A_DICT_POS;
        for (int index = 0; index < count; ++index) {
            const PtNodeParams node = mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(pos);
            if (!node.isValid() || node.getSiblingNodePos() <= pos) {
                mIsCorrupted = true;
                return 0;
            }
            if (pos == target) {
                if (!node.isTerminal() || node.getCodePointCount() > limit - length) return 0;
                std::copy(node.getCodePoints(), node.getCodePoints() + node.getCodePointCount(), word + length);
                length += node.getCodePointCount();
                std::copy(word, word + length, outCodePoints);
                if (outUnigramProbability) *outUnigramProbability = node.getProbability();
                return length;
            }
            if (node.hasChildren() && node.getChildrenPos() <= target
                    && node.getChildrenPos() > candidateChildren) {
                candidatePos = pos;
                candidateChildren = node.getChildrenPos();
            }
            pos = node.getSiblingNodePos();
        }
        if (candidatePos == NOT_A_DICT_POS) return 0;
        const PtNodeParams candidate = mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(candidatePos);
        if (candidate.isValid() && candidate.getCodePointCount() >= limit - length) return 0;
        if (!candidate.isValid() || candidateChildren <= arrayPos) {
            mIsCorrupted = true;
            return 0;
        }
        std::copy(candidate.getCodePoints(), candidate.getCodePoints() + candidate.getCodePointCount(), word + length);
        length += candidate.getCodePointCount();
        arrayPos = candidateChildren;
    }
    mIsCorrupted = true;
    return 0;
}

// This function gets the position of the terminal PtNode of the exact matching word in the
// dictionary. If no match is found, it returns NOT_A_WORD_ID.
int PatriciaTriePolicy::getWordId(const CodePointArrayView wordCodePoints,
        const bool forceLowerCaseSearch) const {
    DynamicPtReadingHelper readingHelper(&mPtNodeReader, &mPtNodeArrayReader);
    readingHelper.initWithPtNodeArrayPos(getRootPosition());
    const int ptNodePos = readingHelper.getTerminalPtNodePositionOfWord(wordCodePoints.data(),
            wordCodePoints.size(), forceLowerCaseSearch);
    if (readingHelper.isError()) {
        mIsCorrupted = true;
        AKLOGE("Dictionary reading error in getWordId().");
    }
    return getWordIdFromTerminalPtNodePos(ptNodePos);
}

const WordAttributes PatriciaTriePolicy::getWordAttributesInContext(
        const WordIdArrayView prevWordIds, const int wordId,
        MultiBigramMap *const multiBigramMap) const {
    if (wordId == NOT_A_WORD_ID) {
        return WordAttributes();
    }
    const int ptNodePos = getTerminalPtNodePosFromWordId(wordId);
    const PtNodeParams ptNodeParams =
            mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos);
    if (multiBigramMap) {
        const int probability =  multiBigramMap->getBigramProbability(this /* structurePolicy */,
                prevWordIds, wordId, ptNodeParams.getProbability());
        return getWordAttributes(probability, ptNodeParams);
    }
    if (!prevWordIds.empty()) {
        const int bigramProbability = getProbabilityOfWord(prevWordIds, wordId);
        if (bigramProbability != NOT_A_PROBABILITY) {
            return getWordAttributes(bigramProbability, ptNodeParams);
        }
    }
    return getWordAttributes(getProbability(ptNodeParams.getProbability(), NOT_A_PROBABILITY),
            ptNodeParams);
}

const WordAttributes PatriciaTriePolicy::getWordAttributes(const int probability,
        const PtNodeParams &ptNodeParams) const {
    return WordAttributes(probability, false /* isBlacklisted */, ptNodeParams.isNotAWord(),
            ptNodeParams.isPossiblyOffensive());
}

int PatriciaTriePolicy::getProbability(const int unigramProbability,
        const int bigramProbability) const {
    // Due to space constraints, the probability for bigrams is approximate - the lower the unigram
    // probability, the worse the precision. The theoritical maximum error in resulting probability
    // is 8 - although in the practice it's never bigger than 3 or 4 in very bad cases. This means
    // that sometimes, we'll see some bigrams interverted here, but it can't get too bad.
    if (unigramProbability == NOT_A_PROBABILITY) {
        return NOT_A_PROBABILITY;
    } else if (bigramProbability == NOT_A_PROBABILITY) {
        return ProbabilityUtils::backoff(unigramProbability);
    } else {
        return ProbabilityUtils::computeProbabilityForBigram(unigramProbability,
                bigramProbability);
    }
}

int PatriciaTriePolicy::getProbabilityOfWord(const WordIdArrayView prevWordIds,
        const int wordId) const {
    if (wordId == NOT_A_WORD_ID) {
        return NOT_A_PROBABILITY;
    }
    const int ptNodePos = getTerminalPtNodePosFromWordId(wordId);
    const PtNodeParams ptNodeParams =
            mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos);
    if (ptNodeParams.isNotAWord()) {
        // If this is not a word, it should behave as having no probability outside of the
        // suggestion process (where it should be used for shortcuts).
        return NOT_A_PROBABILITY;
    }
    if (!prevWordIds.empty()) {
        const int bigramsPosition = getBigramsPositionOfPtNode(
                getTerminalPtNodePosFromWordId(prevWordIds[0]));
        BinaryDictionaryBigramsIterator bigramsIt(&mBigramListPolicy, bigramsPosition);
        while (bigramsIt.hasNext()) {
            bigramsIt.next();
            if (bigramsIt.getBigramPos() == ptNodePos
                    && bigramsIt.getProbability() != NOT_A_PROBABILITY) {
                return getProbability(ptNodeParams.getProbability(), bigramsIt.getProbability());
            }
        }
        return NOT_A_PROBABILITY;
    }
    return getProbability(ptNodeParams.getProbability(), NOT_A_PROBABILITY);
}

void PatriciaTriePolicy::iterateNgramEntries(const WordIdArrayView prevWordIds,
        NgramListener *const listener) const {
    if (prevWordIds.empty()) {
        return;
    }
    const int bigramsPosition = getBigramsPositionOfPtNode(
            getTerminalPtNodePosFromWordId(prevWordIds[0]));
    BinaryDictionaryBigramsIterator bigramsIt(&mBigramListPolicy, bigramsPosition);
    while (bigramsIt.hasNext()) {
        bigramsIt.next();
        listener->onVisitEntry(bigramsIt.getProbability(),
                getWordIdFromTerminalPtNodePos(bigramsIt.getBigramPos()));
    }
}

BinaryDictionaryShortcutIterator PatriciaTriePolicy::getShortcutIterator(const int wordId) const {
    const int shortcutPos = getShortcutPositionOfPtNode(getTerminalPtNodePosFromWordId(wordId));
    return BinaryDictionaryShortcutIterator(&mShortcutListPolicy, shortcutPos);
}

int PatriciaTriePolicy::getShortcutPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    return mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos).getV2ShortcutPos();
}

int PatriciaTriePolicy::getBigramsPositionOfPtNode(const int ptNodePos) const {
    if (ptNodePos == NOT_A_DICT_POS) {
        return NOT_A_DICT_POS;
    }
    return mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos).getV2BigramsPos();
}

int PatriciaTriePolicy::createAndGetLeavingChildNode(const DicNode *const dicNode,
        const int ptNodePos, DicNodeVector *childDicNodes) const {
    const PtNodeParams node = mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos);
    if (!node.isValid() || node.getCodePointCount() <= 0) {
        mIsCorrupted = true;
        return NOT_A_DICT_POS;
    }
    // Non-word information can start with a non-Unicode code point.
    if (CharUtils::isInUnicodeSpace(node.getCodePoints()[0])) {
        const int wordId = node.isTerminal() ? ptNodePos : NOT_A_WORD_ID;
        childDicNodes->pushLeavingChild(dicNode, node.getChildrenPos(), wordId, node.getCodePointArrayView());
    }
    return node.getSiblingNodePos();
}

const WordProperty PatriciaTriePolicy::getWordProperty(
        const CodePointArrayView wordCodePoints) const {
    const int wordId = getWordId(wordCodePoints, false /* forceLowerCaseSearch */);
    if (wordId == NOT_A_WORD_ID) {
        AKLOGE("getWordProperty was called for invalid word.");
        return WordProperty();
    }
    const int ptNodePos = getTerminalPtNodePosFromWordId(wordId);
    const PtNodeParams ptNodeParams =
            mPtNodeReader.fetchPtNodeParamsInBufferFromPtNodePos(ptNodePos);
    // Fetch bigram information.
    std::vector<NgramProperty> ngrams;
    const int bigramListPos = getBigramsPositionOfPtNode(ptNodePos);
    int bigramWord1CodePoints[MAX_WORD_LENGTH];
    BinaryDictionaryBigramsIterator bigramsIt(&mBigramListPolicy, bigramListPos);
    while (bigramsIt.hasNext()) {
        // Fetch the next bigram information and forward the iterator.
        bigramsIt.next();
        // Skip the entry if the entry has been deleted. This never happens for ver2 dicts.
        if (bigramsIt.getBigramPos() != NOT_A_DICT_POS) {
            int word1Probability = NOT_A_PROBABILITY;
            const int word1CodePointCount = getCodePointsAndProbabilityAndReturnCodePointCount(
                    getWordIdFromTerminalPtNodePos(bigramsIt.getBigramPos()), MAX_WORD_LENGTH,
                    bigramWord1CodePoints, &word1Probability);
            const int probability = getProbability(word1Probability, bigramsIt.getProbability());
            ngrams.emplace_back(
                    NgramContext(wordCodePoints.data(), wordCodePoints.size(),
                            ptNodeParams.representsBeginningOfSentence()),
                    CodePointArrayView(bigramWord1CodePoints, word1CodePointCount).toVector(),
                    probability, HistoricalInfo());
        }
    }
    // Fetch shortcut information.
    std::vector<UnigramProperty::ShortcutProperty> shortcuts;
    int shortcutPos = getShortcutPositionOfPtNode(ptNodePos);
    if (shortcutPos != NOT_A_DICT_POS) {
        int shortcutTargetCodePoints[MAX_WORD_LENGTH];
        shortcutPos = mShortcutListPolicy.getStartPos(shortcutPos);
        bool hasNext = shortcutPos != NOT_A_DICT_POS;
        while (hasNext) {
            const int before = shortcutPos;
            int count = 0;
            bool whitelist = false;
            // Preserve the original per-entry probability (the policy exposes whitelist only).
            int flagsPos = shortcutPos;
            const auto flags = ShortcutListReadingUtils::getFlagsAndForwardPointer(mBuffer, &flagsPos);
            mShortcutListPolicy.getNextShortcut(MAX_WORD_LENGTH, shortcutTargetCodePoints,
                    &count, &whitelist, &hasNext, &shortcutPos);
            if (count <= 0 || shortcutPos <= before) { mIsCorrupted = true; break; }
            shortcuts.emplace_back(CodePointArrayView(shortcutTargetCodePoints, count).toVector(),
                    ShortcutListReadingUtils::getProbabilityFromFlags(flags));
        }
    }
    const UnigramProperty unigramProperty(ptNodeParams.representsBeginningOfSentence(),
            ptNodeParams.isNotAWord(), ptNodeParams.isPossiblyOffensive(),
            ptNodeParams.getProbability(), HistoricalInfo(), std::move(shortcuts));
    return WordProperty(wordCodePoints.toVector(), unigramProperty, ngrams);
}

int PatriciaTriePolicy::getNextWordAndNextToken(const int token, int *const outCodePoints,
        int *const outCodePointCount) {
    *outCodePointCount = 0;
    if (token == 0) {
        // Start iterating the dictionary.
        mTerminalPtNodePositionsForIteratingWords.clear();
        DynamicPtReadingHelper::TraversePolicyToGetAllTerminalPtNodePositions traversePolicy(
                &mTerminalPtNodePositionsForIteratingWords);
        DynamicPtReadingHelper readingHelper(&mPtNodeReader, &mPtNodeArrayReader);
        readingHelper.initWithPtNodeArrayPos(getRootPosition());
        readingHelper.traverseAllPtNodesInPostorderDepthFirstManner(&traversePolicy);
    }
    const int terminalPtNodePositionsVectorSize =
            static_cast<int>(mTerminalPtNodePositionsForIteratingWords.size());
    if (token < 0 || token >= terminalPtNodePositionsVectorSize) {
        AKLOGE("Given token %d is invalid.", token);
        return 0;
    }
    const int terminalPtNodePos = mTerminalPtNodePositionsForIteratingWords[token];
    *outCodePointCount = getCodePointsAndReturnCodePointCount(
            getWordIdFromTerminalPtNodePos(terminalPtNodePos), MAX_WORD_LENGTH, outCodePoints);
    const int nextToken = token + 1;
    if (nextToken >= terminalPtNodePositionsVectorSize) {
        // All words have been iterated.
        mTerminalPtNodePositionsForIteratingWords.clear();
        return 0;
    }
    return nextToken;
}

int PatriciaTriePolicy::getWordIdFromTerminalPtNodePos(const int ptNodePos) const {
    return ptNodePos == NOT_A_DICT_POS ? NOT_A_WORD_ID : ptNodePos;
}

int PatriciaTriePolicy::getTerminalPtNodePosFromWordId(const int wordId) const {
    return wordId == NOT_A_WORD_ID ? NOT_A_DICT_POS : wordId;
}

bool PatriciaTriePolicy::isValidPos(const int pos) const {
    return pos >= 0 && pos < static_cast<int>(mBuffer.size());
}

} // namespace latinime
