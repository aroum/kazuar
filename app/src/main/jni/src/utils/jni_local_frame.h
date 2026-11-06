// SPDX-License-Identifier: GPL-3.0-only
#ifndef LATINIME_JNI_LOCAL_FRAME_H
#define LATINIME_JNI_LOCAL_FRAME_H
#include "jni.h"
namespace latinime {
class JniLocalFrame {
 public:
    explicit JniLocalFrame(JNIEnv *env, int capacity = 32)
            : mEnv(env), mValid(env && env->PushLocalFrame(capacity) == JNI_OK) {}
    ~JniLocalFrame() { if (mValid) mEnv->PopLocalFrame(nullptr); }
    bool valid() const { return mValid; }
 private:
    JniLocalFrame(const JniLocalFrame &) = delete;
    JniLocalFrame &operator=(const JniLocalFrame &) = delete;
    JNIEnv *mEnv;
    bool mValid;
};
}
#endif
