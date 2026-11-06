// SPDX-License-Identifier: GPL-3.0-only
#include <gtest/gtest.h>
#include <climits>
#include <cmath>
#include <thread>
#include "utils/char_utils.h"
#include "utils/unicode_utils.h"
#include "utils/autocorrection_threshold_utils.h"
#include "utils/jni_data_utils.h"
#include "utils/time_keeper.h"
#include "dictionary/structure/v4/content/probability_entry.h"
#include "dictionary/structure/v4/content/dynamic_language_model_probability_utils.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "dictionary/header/header_policy.h"
#include "dictionary/utils/file_utils.h"
#include "dictionary/utils/forgetting_curve_utils.h"
#include "suggest/core/suggest.h"
#include "suggest/policyimpl/typing/typing_suggest_policy_factory.h"
#include "suggest/policyimpl/typing/typing_scoring.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/policyimpl/utils/edit_distance.h"
// Exercise the actual JNI entry points, which are otherwise excluded from the host library.
#include "../../io_github_aroum_kazuar_inputmethod_latin_BinaryDictionary.cpp"

namespace latinime {
class NativeBoundaryTestPeer {
 public:
    static void expand(const Suggest &suggest, DicTraverseSession *session) { suggest.expandCurrentDicNodes(session); }
};
namespace {
struct Value {
    enum Kind { Int, Float, Bool, Object, String } kind = Int;
    std::vector<int> ints;
    std::vector<float> floats;
    std::vector<jboolean> bools;
    std::vector<jobject> objects;
    std::u16string text;
    explicit Value(std::vector<int> data = {}) : ints(std::move(data)) {}
    int size() const { return kind == Float ? floats.size() : kind == Bool ? bools.size()
            : kind == Object ? objects.size() : ints.size(); }
    template<class T> T as() { return reinterpret_cast<T>(this); }
};
class JniHarness {
 public:
    JNINativeInterface_ table{}; JNIEnv env{};
    int refs = 0, peak = 0; std::vector<int> frames;
    static JniHarness *active;
    JniHarness() {
        active = this; env.functions = &table;
        table.GetArrayLength = [](JNIEnv *, jarray value) -> jsize { EXPECT_NE(nullptr,value); return value ? reinterpret_cast<Value *>(value)->size() : 0; };
        table.ExceptionCheck = [](JNIEnv *) -> jboolean { return JNI_FALSE; };
        table.ExceptionClear = [](JNIEnv *) {};
        table.FindClass = [](JNIEnv *, const char *) -> jclass { return nullptr; };
        table.PushLocalFrame = [](JNIEnv *, jint) -> jint { active->frames.push_back(active->refs); return JNI_OK; };
        table.PopLocalFrame = [](JNIEnv *, jobject value) -> jobject { active->refs = active->frames.back(); active->frames.pop_back(); return value; };
        table.DeleteLocalRef = [](JNIEnv *, jobject value) { if(value) --active->refs; };
        table.GetObjectArrayElement = [](JNIEnv *, jobjectArray array, jsize index) -> jobject {
            auto &values = reinterpret_cast<Value *>(array)->objects;
            EXPECT_GE(index,0); EXPECT_LT(index,values.size());
            if(index < 0 || index >= static_cast<int>(values.size())) return nullptr;
            if(values[index]) { ++active->refs; active->peak = std::max(active->peak,active->refs); }
            return values[index];
        };
        table.GetIntArrayRegion = [](JNIEnv *, jintArray array, jsize start, jsize count, jint *out) {
            const auto &v = reinterpret_cast<Value *>(array)->ints;
            ASSERT_GE(start,0); ASSERT_LE(start+count,v.size());
            if(count) std::copy(v.begin()+start,v.begin()+start+count,out);
        };
        table.SetIntArrayRegion = [](JNIEnv *, jintArray array, jsize start, jsize count, const jint *data) {
            auto &v = reinterpret_cast<Value *>(array)->ints;
            ASSERT_GE(start,0); ASSERT_LE(start+count,v.size());
            if(count) std::copy(data,data+count,v.begin()+start);
        };
        table.GetBooleanArrayRegion = [](JNIEnv *, jbooleanArray array, jsize start, jsize count, jboolean *out) {
            const auto &v = reinterpret_cast<Value *>(array)->bools;
            ASSERT_GE(start,0); ASSERT_LE(start+count,v.size());
            if(count) std::copy(v.begin()+start,v.begin()+start+count,out);
        };
        table.SetFloatArrayRegion = [](JNIEnv *, jfloatArray array, jsize start, jsize count, const jfloat *data) {
            auto &v = reinterpret_cast<Value *>(array)->floats;
            ASSERT_GE(start,0); ASSERT_LE(start+count,v.size());
            if(count) std::copy(data,data+count,v.begin()+start);
        };
        table.GetStringLength = [](JNIEnv *, jstring str) -> jsize { return reinterpret_cast<Value *>(str)->text.size(); };
        table.GetStringRegion = [](JNIEnv *, jstring str, jsize start, jsize count, jchar *out) {
            const auto &text = reinterpret_cast<Value *>(str)->text;
            ASSERT_GE(start,0); ASSERT_LE(start+count,text.size());
            if(count) std::copy(text.begin()+start,text.begin()+start+count,out);
        };
    }
    ~JniHarness() { EXPECT_TRUE(frames.empty()); EXPECT_EQ(0,refs); active = nullptr; }
};
JniHarness *JniHarness::active = nullptr;
class FutureClock {
 public:
    static constexpr int64_t NOW = 2200000000LL;
    FutureClock() { TimeKeeper::startTestModeWithForceCurrentTime(NOW); }
    ~FutureClock() { TimeKeeper::stopTestMode(); TimeKeeper::setCurrentTime(); }
};
constexpr int64_t FutureClock::NOW;

TEST(NativeBoundaryTest, UnicodeBoundariesAndInvalidScalarsProduceValidUtf8) {
    int input[] = {0x7F, 0x7FF, 0xFFFF, 0x10000, 0x10FFFF, -1, 0xD800, 0x110000};
    char output[64]; const int length = intArrayToCharArray(input,8,output,sizeof(output));
    auto utf16 = UnicodeUtils::utf8ToUtf16(std::string(output,length));
    auto decoded = UnicodeUtils::utf16ToCodePoints(reinterpret_cast<const uint16_t *>(utf16.data()),utf16.size());
    EXPECT_EQ((std::vector<int>{0x7F,0x7FF,0xFFFF,0x10000,0x10FFFF,0xFFFD,0xFFFD,0xFFFD}),decoded);
    char guard = 'x'; EXPECT_EQ(0,intArrayToCharArray(input,8,&guard,0)); EXPECT_EQ('x',guard);
    EXPECT_EQ(0,intArrayToCharArray(input,8,nullptr,10));
    const int nonBmp[] = {0x10000}; char shortOutput[4];
    EXPECT_EQ(0,intArrayToCharArray(nonBmp,1,shortOutput,4)); EXPECT_EQ(0,shortOutput[0]);
}
TEST(NativeBoundaryTest, SupplementaryCharactersDoNotAliasBmpLowercaseTable) {
    for (int c : {-1,-2,INT_MIN,0x10041,0x10410,0x10FFFF}) EXPECT_EQ(c,CharUtils::toBaseLowerCase(c));
    EXPECT_EQ('a',CharUtils::toLowerCase('A')); EXPECT_EQ(0x430,CharUtils::toLowerCase(0x410));
    auto text = UnicodeUtils::utf8ToUtf16(std::string("a\0b",3) + "\xF0\x9F\x98\x80");
    EXPECT_EQ(5u,text.size()); EXPECT_EQ(0,text[1]); EXPECT_EQ(0xD83D,text[3]);
    EXPECT_FALSE(UnicodeUtils::utf8ToUtf16("\xF0\x28\x8C\x28").empty());
}
TEST(NativeBoundaryTest, ArrayCopiesValidateCapacityBeforeWriting) {
    std::array<int,3> out{{1,2,3}}; int input[] = {4,5,6,7};
    IntArrayView(input,4).copyToArray(&out,0); EXPECT_EQ((std::array<int,3>{{1,2,3}}),out);
    IntArrayView(input,1).copyToArray(&out,SIZE_MAX); EXPECT_EQ(1,out[0]);
    IntArrayView().copyToArray(&out,0); IntArrayView(nullptr,1).copyToArray(&out,0);
    IntArrayView(input,2).copyToArray(&out,1); EXPECT_EQ((std::array<int,3>{{1,4,5}}),out);
}
TEST(NativeBoundaryTest, SuggestOptionsOwnTheInputAndRejectOffsetOverflow) {
    int options[] = {1,0,0,0,1000,1}; SuggestOptions owned(options,6); options[0]=0;
    EXPECT_TRUE(owned.isGesture()); EXPECT_TRUE(owned.getAdditionalFeaturesBoolOption(0));
    EXPECT_FALSE(owned.getAdditionalFeaturesBoolOption(-5)); EXPECT_FALSE(owned.getAdditionalFeaturesBoolOption(INT_MAX));
    SuggestOptions empty(nullptr,100); EXPECT_FALSE(empty.isGesture());
}
TEST(NativeBoundaryTest, EditDistanceUsesBoundedHeapRowsAndGuardsTransposition) {
    std::vector<int> before(2048,'a'), after(2048,'a'); after[2000]='b';
    EXPECT_EQ(1,AutocorrectionThresholdUtils::editDistance(before.data(),before.size(),after.data(),after.size()));
    int transposed[] = {'b','a'}, original[] = {'a','b'};
    EXPECT_EQ(1,AutocorrectionThresholdUtils::editDistance(original,2,transposed,2));
    EXPECT_EQ(-1,AutocorrectionThresholdUtils::editDistance(nullptr,1,original,2));
    EXPECT_FLOAT_EQ(0,AutocorrectionThresholdUtils::calcNormalizedScore(nullptr,-1,original,2,100));
    EXPECT_FLOAT_EQ(0,AutocorrectionThresholdUtils::calcNormalizedScore(original,INT_MAX,original,2,100));
    EXPECT_TRUE(std::isinf(EditDistance::getEditDistance(nullptr)));
}
TEST(NativeBoundaryTest, NgramInputRejectsOversizedContextAndReleasesInvalidWords) {
    JniHarness jni; Value longWord(std::vector<int>(MAX_WORD_LENGTH+1,'a')), words, bos;
    words.kind=Value::Object; words.objects={longWord.as<jobject>()}; bos.kind=Value::Bool; bos.bools={JNI_TRUE};
    for(int i=0;i<1000;++i) {
        auto context=JniDataUtils::constructNgramContext(&jni.env,words.as<jobjectArray>(),bos.as<jbooleanArray>(),1);
        EXPECT_TRUE(context.getNthPrevWordCodePoints(1).empty()); EXPECT_EQ(0,jni.refs);
    }
    EXPECT_EQ(0u,JniDataUtils::constructNgramContext(&jni.env,words.as<jobjectArray>(),bos.as<jbooleanArray>(),SIZE_MAX).getPrevWordCount());
    EXPECT_EQ(0u,JniDataUtils::constructNgramContext(&jni.env,nullptr,nullptr,3).getPrevWordCount());
    EXPECT_LE(jni.peak,1);
}
TEST(NativeBoundaryTest, AttributeMapRejectsMismatchesNullStringsAndLongValues) {
    JniHarness jni; Value keys,values,key,value; keys.kind=values.kind=Value::Object; key.kind=value.kind=Value::String;
    key.text=u"dictionary"; value.text=u"\u0410\U0001F600";
    keys.objects={key.as<jobject>()}; values.objects={value.as<jobject>()}; bool valid=false;
    auto map=JniDataUtils::constructAttributeMap(&jni.env,keys.as<jobjectArray>(),values.as<jobjectArray>(),&valid);
    EXPECT_TRUE(valid); EXPECT_EQ((std::vector<int>{0x410,0x1F600}),map.begin()->second); EXPECT_EQ(0,jni.refs);
    value.text.assign(100000,'a'); EXPECT_TRUE(JniDataUtils::constructAttributeMap(&jni.env,keys.as<jobjectArray>(),values.as<jobjectArray>(),&valid).empty()); EXPECT_FALSE(valid);
    values.objects={nullptr}; EXPECT_TRUE(JniDataUtils::constructAttributeMap(&jni.env,keys.as<jobjectArray>(),values.as<jobjectArray>()).empty()); EXPECT_EQ(0,jni.refs);
    values.objects.clear(); EXPECT_TRUE(JniDataUtils::constructAttributeMap(&jni.env,keys.as<jobjectArray>(),values.as<jobjectArray>()).empty());
    EXPECT_TRUE(JniDataUtils::constructAttributeMap(&jni.env,nullptr,nullptr).empty());
}
TEST(NativeBoundaryTest, SuggestionOutputHonorsAllJniCapacitiesAndKeepsBestWord) {
    JniHarness jni; SuggestionResults results(10); int a='a',b='b',c='c';
    results.addPrediction(&a,1,1);results.addPrediction(&b,1,2);results.addPrediction(&c,1,3);
    int scores[2]={-1,-1}; EXPECT_EQ(1,results.getSortedScores(scores,1)); EXPECT_EQ(3,scores[0]); EXPECT_EQ(-1,scores[1]);
    Value count({9}), points(std::vector<int>(MAX_WORD_LENGTH)), score({0}), space({0}), type({0}), confidence({0}), weight;
    weight.kind=Value::Float;weight.floats={0};
    results.outputSuggestions(&jni.env,count.as<jintArray>(),points.as<jintArray>(),score.as<jintArray>(),space.as<jintArray>(),type.as<jintArray>(),confidence.as<jintArray>(),weight.as<jfloatArray>());
    EXPECT_EQ(1,count.ints[0]);EXPECT_EQ('c',points.ints[0]);EXPECT_EQ(3,score.ints[0]);
    SuggestionResults zero(0);zero.addPrediction(&a,1,1);EXPECT_EQ(0,zero.getSuggestionCount());
    zero.addPrediction(nullptr,1,1);EXPECT_EQ(0,zero.getSuggestionCount());
}
TEST(NativeBoundaryTest, CodePointOutputHandlesZeroCapacityAndInvalidUnicode) {
    JniHarness jni;Value out({9,9,9});int points[]={0xD800,0x1F600};
    JniDataUtils::outputCodePoints(&jni.env,out.as<jintArray>(),0,100,points,2,true);
    EXPECT_EQ((std::vector<int>{0xFFFD,0x1F600,0}),out.ints);
    JniDataUtils::outputCodePoints(&jni.env,out.as<jintArray>(),INT_MAX,100,points,2,true);
    JniDataUtils::outputCodePoints(&jni.env,nullptr,0,0,nullptr,0,true);
}
TEST(NativeBoundaryTest, UnsupportedGesturePolicyReturnsWithoutDereferencingNull) {
    Suggest unsupported(nullptr);SuggestionResults results(10);
    unsupported.getSuggestions(nullptr,nullptr,nullptr,nullptr,nullptr,nullptr,nullptr,1,1,&results);
    EXPECT_EQ(0,results.getSuggestionCount());
}
TEST(NativeBoundaryTest, NativeEntriesRejectNullEventsAndOversizedGestureInput) {
    JniHarness jni;DictionaryHeaderStructurePolicy::AttributeMap attrs;
    auto policy=DictionaryStructureWithBufferPolicyFactory::newPolicyForOnMemoryDict(403,{'e','n'},&attrs);
    ASSERT_TRUE(policy);Dictionary dictionary(&jni.env,std::move(policy));DicTraverseSession session(nullptr,nullptr,false);
    Value events;events.kind=Value::Object;events.objects={nullptr};
    EXPECT_EQ(0,latinime_BinaryDictionary_updateEntriesForInputEvents(&jni.env,nullptr,reinterpret_cast<jlong>(&dictionary),events.as<jobjectArray>(),0));
    Value count({9}), codes({'a'}), options({1,0,0,0,1000});
    latinime_BinaryDictionary_getSuggestions(&jni.env,nullptr,reinterpret_cast<jlong>(&dictionary),0,reinterpret_cast<jlong>(&session),nullptr,nullptr,nullptr,nullptr,codes.as<jintArray>(),INT_MAX,options.as<jintArray>(),nullptr,nullptr,0,count.as<jintArray>(),nullptr,nullptr,nullptr,nullptr,nullptr,nullptr);
    EXPECT_EQ(0,count.ints[0]);
}
TEST(NativeBoundaryTest, ClockAndDictionaryTimestampsSurvive2038) {
    FutureClock clock;EXPECT_EQ(FutureClock::NOW,TimeKeeper::peekCurrentTime());
    std::vector<std::thread> threads;for(int i=0;i<4;++i)threads.emplace_back([] { for(int j=0;j<1000;++j){TimeKeeper::setCurrentTime();EXPECT_EQ(FutureClock::NOW,TimeKeeper::peekCurrentTime());} });
    for(auto &thread:threads)thread.join();
    HistoricalInfo history(FutureClock::NOW-10,2,3);EXPECT_TRUE(history.isValid());
    auto entry=ProbabilityEntry::decode(ProbabilityEntry(0,&history).encode(true),true);
    EXPECT_EQ(FutureClock::NOW-10,entry.getHistoricalInfo()->getTimestamp());
    EXPECT_EQ(history.getTimestamp(),HistoricalInfo::decodeTimestamp(static_cast<uint32_t>(history.getLegacyTimestamp())));
    EXPECT_FALSE(DynamicLanguageModelProbabilityUtils::shouldRemoveEntryDuringGC(history));
    DictionaryHeaderStructurePolicy::AttributeMap attrs;
    HeaderReadWriteUtils::setLongAttribute(&attrs,"date",FutureClock::NOW);
    HeaderReadWriteUtils::setLongAttribute(&attrs,"LAST_DECAYED_TIME",FutureClock::NOW-10);
    HeaderPolicy header(FormatUtils::VERSION_403,{'e','n'},&attrs);
    EXPECT_EQ(FutureClock::NOW,header.getDate());EXPECT_EQ(FutureClock::NOW-10,header.getLastDecayedTime());
    EXPECT_EQ(7,HeaderReadWriteUtils::readIntAttributeValue(&attrs,"date",7));
}
TEST(NativeBoundaryTest, QuadgramCountersAreReadWrittenAndUsedForDecayLimits) {
    FutureClock clock;DictionaryHeaderStructurePolicy::AttributeMap attrs;
    HeaderReadWriteUtils::setIntAttribute(&attrs,"QUADGRAM_COUNT",7);
    HeaderReadWriteUtils::setIntAttribute(&attrs,"MAX_QUADGRAM_ENTRY_COUNT",3);
    HeaderPolicy header(FormatUtils::VERSION_403,{'e','n'},&attrs);
    EXPECT_EQ(7,header.getNgramCounts().getNgramCount(NgramType::Quadgram));
    MutableEntryCounters counters;counters.setNgramCount(NgramType::Quadgram,7);
    EXPECT_TRUE(ForgettingCurveUtils::needsToDecay(true,counters.getEntryCounts(),&header));
    BufferWithExtendableBuffer buffer(8192);ASSERT_TRUE(header.fillInAndWriteHeaderToBuffer(false,counters.getEntryCounts(),0,&buffer));
    HeaderPolicy reopened(buffer.getReadOnlyBuffer(true),FormatUtils::VERSION_403);
    EXPECT_EQ(7,reopened.getNgramCounts().getNgramCount(NgramType::Quadgram));
    EXPECT_EQ(FutureClock::NOW,reopened.getDate());
}
} // namespace
} // namespace latinime
