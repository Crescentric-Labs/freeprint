// PWG raster (RaS2) streaming encoder. Header-only, no Android or USB dependencies,
// so it can be unit-tested on a desktop (see tools/pwg-test/).
//
// Usage: startDocument(); per page: startPage(), addRgbaRows() any number of times
// (top to bottom), endPage(). Output goes to the sink in order.
#pragma once

#include <cstdint>
#include <cstring>
#include <functional>
#include <vector>

namespace pwg {

class Encoder {
public:
    using Sink = std::function<bool(const uint8_t *, size_t)>;

    explicit Encoder(Sink sink) : sink_(std::move(sink)) {}

    bool startDocument() { return emit(reinterpret_cast<const uint8_t *>("RaS2"), 4); }

    // pageWidthPt/pageHeightPt are the paper size in points (1/72 in).
    bool startPage(int width, int height, int dpi, int pageWidthPt, int pageHeightPt, bool color,
                   const char *mediaName = "iso_a4_210x297mm") {
        width_ = width;
        height_ = height;
        bpp_ = color ? 3 : 1;
        rowsExpected_ = height;
        rowsSeen_ = 0;
        repeat_ = 0;
        prev_.clear();
        std::vector<uint8_t> h(1796, 0);
        auto put = [&](size_t off, uint32_t v) {
            h[off] = v >> 24; h[off + 1] = v >> 16; h[off + 2] = v >> 8; h[off + 3] = v;
        };
        memcpy(h.data(), "PwgRaster", 9);
        put(276, dpi); put(280, dpi);
        put(340, 1);                                  // NumCopies
        put(352, pageWidthPt); put(356, pageHeightPt);
        put(372, width); put(376, height);
        put(384, 8); put(388, 8 * bpp_); put(392, width * bpp_);
        put(396, 0);                                  // chunky
        put(400, color ? 19 : 18);                    // sRGB or sGray
        put(420, bpp_);                               // cupsNumColors
        put(452, 1);                                  // TotalPageCount
        strncpy(reinterpret_cast<char *>(h.data()) + 1732, mediaName, 63);
        return emit(h.data(), h.size());
    }

    // rgba: rows*width*4 bytes, R,G,B,A order (Android ARGB_8888). Alpha is ignored; the
    // caller must have composited onto white.
    bool addRgbaRows(const uint8_t *rgba, int rows) {
        std::vector<uint8_t> line(static_cast<size_t>(width_) * bpp_);
        for (int r = 0; r < rows && rowsSeen_ < rowsExpected_; r++, rowsSeen_++) {
            const uint8_t *src = rgba + static_cast<size_t>(r) * width_ * 4;
            if (bpp_ == 3) {
                for (int x = 0; x < width_; x++) {
                    line[x * 3] = src[x * 4]; line[x * 3 + 1] = src[x * 4 + 1]; line[x * 3 + 2] = src[x * 4 + 2];
                }
            } else {
                for (int x = 0; x < width_; x++)       // Rec. 601 luma, integer
                    line[x] = (src[x * 4] * 77 + src[x * 4 + 1] * 150 + src[x * 4 + 2] * 29) >> 8;
            }
            if (!prev_.empty() && repeat_ < 256 && line == prev_) { repeat_++; continue; }
            if (!flushLine()) return false;
            prev_ = line;
            repeat_ = 1;
        }
        return true;
    }

    // Flushes the last line; fails if the page did not receive exactly `height` rows.
    bool endPage() {
        if (!flushLine()) return false;
        prev_.clear();
        return rowsSeen_ == rowsExpected_;
    }

private:
    bool emit(const uint8_t *d, size_t n) { return sink_(d, n); }

    // Writes (repeat-1) then the PackBits-style pixel runs of prev_.
    bool flushLine() {
        if (prev_.empty()) return true;
        std::vector<uint8_t> out;
        out.reserve(prev_.size() + prev_.size() / 64 + 4);
        out.push_back(static_cast<uint8_t>(repeat_ - 1));
        const int n = width_, b = bpp_;
        auto same = [&](int i, int j) { return memcmp(&prev_[i * b], &prev_[j * b], b) == 0; };
        int i = 0;
        while (i < n) {
            int run = 1;
            while (i + run < n && run < 128 && same(i, i + run)) run++;
            if (run > 1) {
                out.push_back(run - 1);
                out.insert(out.end(), &prev_[i * b], &prev_[i * b] + b);
                i += run;
                continue;
            }
            // Literal: pixels until the next repeated pair, at most 128.
            int lit = 1;
            while (i + lit < n && lit < 128 && !(i + lit + 1 < n && same(i + lit, i + lit + 1))) lit++;
            if (lit == 1) {
                out.push_back(0);                     // a single pixel is a run of 1
            } else {
                out.push_back(static_cast<uint8_t>(257 - lit));
            }
            out.insert(out.end(), &prev_[i * b], &prev_[i * b] + static_cast<size_t>(lit) * b);
            i += lit;
        }
        return emit(out.data(), out.size());
    }

    Sink sink_;
    int width_ = 0, height_ = 0, bpp_ = 3, rowsExpected_ = 0, rowsSeen_ = 0, repeat_ = 0;
    std::vector<uint8_t> prev_;
};

}  // namespace pwg
