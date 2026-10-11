// Writes <name>.rgba (expected source pixels) and <name>.pwg (encoder output) for a set of cases.
#include "../../app/src/main/cpp/pwg_encoder.h"
#include <cstdio>
#include <cstdlib>
#include <string>

static void run(const std::string &name, int w, int h, bool color, int mode, unsigned seed) {
    srand(seed);
    std::vector<uint8_t> img((size_t)w * h * 4);
    for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
            uint8_t *p = &img[((size_t)y * w + x) * 4];
            uint8_t r, g, b;
            switch (mode) {
                case 0: r = g = b = 255; break;                                         // white
                case 1: r = rand(); g = rand(); b = rand(); break;                      // noise
                case 2: r = g = b = (x % 2) ? 0 : 255; break;                           // alternating
                case 3: r = (x * 255) / (w > 1 ? w - 1 : 1); g = y & 255; b = 128; break;  // gradient
                case 4: r = g = b = (x % 130 == 5 || x % 130 == 70) ? 0 : 255; break;   // sparse isolated pixels
                default: r = g = b = ((x / 127) & 1) ? 10 : 200; break;                 // runs of 127
            }
            p[0] = r; p[1] = g; p[2] = b; p[3] = 255;
        }
    FILE *f = fopen((name + ".rgba").c_str(), "wb"); fwrite(img.data(), 1, img.size(), f); fclose(f);
    FILE *o = fopen((name + ".pwg").c_str(), "wb");
    pwg::Encoder enc([&](const uint8_t *d, size_t n) { return fwrite(d, 1, n, o) == n; });
    bool ok = enc.startDocument() && enc.startPage(w, h, 600, 595, 842, color);
    int y = 0;
    while (ok && y < h) {                                   // random band sizes
        int rows = 1 + rand() % 37; if (y + rows > h) rows = h - y;
        ok = enc.addRgbaRows(&img[(size_t)y * w * 4], rows); y += rows;
    }
    ok = ok && enc.endPage();
    fclose(o);
    printf("%s %s\n", name.c_str(), ok ? "encoded" : "ENCODE FAILED");
    if (!ok) exit(1);
}

int main() {
    int widths[] = {1, 2, 3, 127, 128, 129, 255, 300, 1031};
    int n = 0;
    for (int w : widths)
        for (int mode = 0; mode < 6; mode++)
            for (int color = 0; color < 2; color++)
            {
                int id = n++;
                run("case_" + std::to_string(id) + "_w" + std::to_string(w) + "_m" + std::to_string(mode) +
                        (color ? "_c" : "_g"), w, 70, color, mode, 1000 + id);
            }
    return 0;
}
