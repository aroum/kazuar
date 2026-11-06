// SPDX-License-Identifier: GPL-3.0-only
#ifndef LATINIME_UNICODE_UTILS_H
#define LATINIME_UNICODE_UTILS_H
#include <cstdint>
#include <string>
#include <vector>
namespace latinime {
class UnicodeUtils {
 public:
    static std::u16string utf8ToUtf16(const std::string &text) {
        std::u16string out;
        for (size_t i = 0; i < text.size();) {
            const uint8_t lead = text[i++];
            uint32_t c = lead; int extra = 0; uint32_t minimum = 0;
            if (lead >= 0xC2 && lead <= 0xDF) { c &= 0x1F; extra = 1; minimum = 0x80; }
            else if (lead >= 0xE0 && lead <= 0xEF) { c &= 0x0F; extra = 2; minimum = 0x800; }
            else if (lead >= 0xF0 && lead <= 0xF4) { c &= 7; extra = 3; minimum = 0x10000; }
            else if (lead >= 0x80) { out.push_back(0xFFFD); continue; }
            bool valid = i + extra <= text.size();
            for (int j = 0; valid && j < extra; ++j) {
                const uint8_t next = text[i + j];
                if ((next & 0xC0) != 0x80) valid = false;
                else c = (c << 6) | (next & 0x3F);
            }
            if (!valid) { out.push_back(0xFFFD); continue; }
            i += extra;
            if (c < minimum || c > 0x10FFFF || (c >= 0xD800 && c <= 0xDFFF)) c = 0xFFFD;
            if (c <= 0xFFFF) out.push_back(c);
            else { c -= 0x10000; out.push_back(0xD800 + (c >> 10)); out.push_back(0xDC00 + (c & 0x3FF)); }
        }
        return out;
    }
    static std::vector<int> utf16ToCodePoints(const uint16_t *data, size_t length) {
        std::vector<int> out;
        if (!data) return out;
        for (size_t i = 0; i < length; ++i) {
            uint32_t c = data[i];
            if (c >= 0xD800 && c <= 0xDBFF && i + 1 < length
                    && data[i + 1] >= 0xDC00 && data[i + 1] <= 0xDFFF) {
                c = 0x10000 + ((c - 0xD800) << 10) + data[++i] - 0xDC00;
            } else if (c >= 0xD800 && c <= 0xDFFF) c = 0xFFFD;
            out.push_back(c);
        }
        return out;
    }
};
}
#endif
