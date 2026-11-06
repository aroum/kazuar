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

#include "log_utils.h"

#include <cstdio>
#include <stdarg.h>

#include "defines.h"
#include <vector>
#include "utils/unicode_utils.h"
#include "utils/jni_local_frame.h"

namespace latinime {
    /* static */ void LogUtils::logToJava(JNIEnv *const env, const char *const format, ...) {
        if (!env || !format || env->ExceptionCheck()) return;
        JniLocalFrame frame(env, 8);
        if (!frame.valid()) return;
        const jclass logClass = env->FindClass("android/util/Log");
        if (!logClass) { env->ExceptionClear(); return; }
        const jmethodID method = env->GetStaticMethodID(logClass, "i", "(Ljava/lang/String;Ljava/lang/String;)I");
        if (!method) { env->ExceptionClear(); return; }
        va_list args;
        va_start(args, format);
        va_list copied;
        va_copy(copied, args);
        const int needed = vsnprintf(nullptr, 0, format, copied);
        va_end(copied);
        if (needed < 0 || needed > 65536) { va_end(args); return; }
        std::vector<char> bytes(needed + 1);
        const int written = vsnprintf(bytes.data(), bytes.size(), format, args);
        va_end(args);
        if (written < 0 || written != needed) return;
        const std::u16string text = UnicodeUtils::utf8ToUtf16(std::string(bytes.data(), written));
        const jstring tag = env->NewStringUTF("LatinIME:LogUtils"); // ASCII is valid Modified UTF-8.
        if (!tag || env->ExceptionCheck()) return;
        const jstring message = env->NewString(reinterpret_cast<const jchar *>(text.data()), text.size());
        if (!message || env->ExceptionCheck()) return;
        env->CallStaticIntMethod(logClass, method, tag, message);
    }
}
