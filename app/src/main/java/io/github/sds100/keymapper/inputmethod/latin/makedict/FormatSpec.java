/*
 * Copyright (C) 2012 The Android Open Source Project
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

package io.github.sds100.keymapper.inputmethod.latin.makedict;

import io.github.sds100.keymapper.inputmethod.annotations.UsedForTesting;

import java.util.Date;
import java.util.HashMap;

/**
 * Dictionary File Format Specification.
 */
public final class FormatSpec {
    public static final int MAGIC_NUMBER = 0x9BC13AFE;
    static final int NOT_A_VERSION_NUMBER = -1;

    public static final int VERSION2 = 2;
    public static final int VERSION201 = 201;
    public static final int VERSION202 = 202;
    public static final int VERSION_DELIGHT3 = 86736212;
    public static final int MINIMUM_SUPPORTED_VERSION_OF_CODE_POINT_TABLE = VERSION201;
    public static final int VERSION4_ONLY_FOR_TESTING = 399;
    public static final int VERSION402 = 402;
    public static final int VERSION403 = 403;
    public static final int VERSION4 = VERSION403;
    public static final int MINIMUM_SUPPORTED_STATIC_VERSION = VERSION202;
    public static final int MAXIMUM_SUPPORTED_STATIC_VERSION = VERSION_DELIGHT3;
    static final int MINIMUM_SUPPORTED_DYNAMIC_VERSION = VERSION4;
    static final int MAXIMUM_SUPPORTED_DYNAMIC_VERSION = VERSION403;

    public static final int SHORTCUT_WHITELIST_FREQUENCY = 15;

    /**
     * Options about file format.
     */
    public static final class FormatOptions {
        public final int mVersion;
        public final boolean mHasTimestamp;

        @UsedForTesting
        public FormatOptions(final int version) {
            this(version, false /* hasTimestamp */);
        }

        public FormatOptions(final int version, final boolean hasTimestamp) {
            mVersion = version;
            mHasTimestamp = hasTimestamp;
        }
    }

    /**
     * Options global to the dictionary.
     */
    public static final class DictionaryOptions {
        public final HashMap<String, String> mAttributes;
        public DictionaryOptions(final HashMap<String, String> attributes) {
            mAttributes = attributes;
        }
        @Override
        public String toString() { // Convenience method
            return toString(0, false);
        }
        public String toString(final int indentCount, final boolean plumbing) {
            final StringBuilder indent = new StringBuilder();
            if (plumbing) {
                indent.append("H:");
            } else {
                for (int i = 0; i < indentCount; ++i) {
                    indent.append(" ");
                }
            }
            final StringBuilder s = new StringBuilder();
            for (final String optionKey : mAttributes.keySet()) {
                s.append(indent);
                s.append(optionKey);
                s.append(" = ");
                if ("date".equals(optionKey) && !plumbing) {
                    // Date needs a number of milliseconds, but the dictionary contains seconds
                    s.append(new Date(
                            1000 * Long.parseLong(mAttributes.get(optionKey))).toString());
                } else {
                    s.append(mAttributes.get(optionKey));
                }
                s.append("\n");
            }
            return s.toString();
        }
    }

    private FormatSpec() {
        // This utility class is not publicly instantiable.
    }
}
