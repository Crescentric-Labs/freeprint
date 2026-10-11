# Generate a one-page A4 600 dpi sRGB 8-bit PWG raster test page (stdlib only).
import struct, sys
W, H = 4960, 7016            # A4 at 600 dpi
BARS = [(255,0,0),(0,255,0),(0,0,255),(0,255,255),(255,0,255),(255,255,0),(0,0,0)]

def pixel_row(y):
    """Return a bytes row for line y."""
    if 300 <= y < 900:       # colour bars
        bw = (W - 800) // len(BARS)
        row = bytearray(b'\xff' * (W * 3))
        for i, c in enumerate(BARS):
            x0 = 400 + i * bw
            row[x0*3:(x0+bw)*3] = bytes(c) * bw
        return bytes(row)
    if 1000 <= y < 1300:     # grey ramp
        row = bytearray(b'\xff' * (W * 3))
        n = 16; bw = (W - 800) // n
        for i in range(n):
            g = 255 - i * 17
            x0 = 400 + i * bw
            row[x0*3:(x0+bw)*3] = bytes((g, g, g)) * bw
        return bytes(row)
    if y in (100, 101, H - 102, H - 101) or 100 <= y < H - 100 and False:
        row = bytearray(b'\xff' * (W * 3)); row[300*3:(W-300)*3] = b'\x00' * ((W-600)*3); return bytes(row)
    if 100 <= y <= H - 101:  # side border lines
        row = bytearray(b'\xff' * (W * 3))
        for x in (300, 301, W - 302, W - 301): row[x*3:x*3+3] = b'\x00\x00\x00'
        return bytes(row)
    return b'\xff' * (W * 3)

def encode_line(row):
    out = bytearray(); i = 0; n = W
    px = [row[k*3:k*3+3] for k in range(n)]
    while i < n:
        j = i + 1
        while j < n and j - i < 128 and px[j] == px[i]: j += 1
        if j - i > 1:
            out.append(j - i - 1); out += px[i]; i = j
        else:
            k = i
            while k < n and k - i < 128 and (k + 1 >= n or px[k] != px[k+1]): k += 1
            if k == i: k = i + 1
            out.append(0 if k - i == 1 else 257 - (k - i))   # a lone pixel is a run of 1
            for t in range(i, k): out += px[t]
            i = k
    return bytes(out)

def header():
    h = bytearray(1796)
    def s(off, txt): h[off:off+len(txt)] = txt.encode()
    s(0, "PwgRaster"); s(192, "")
    struct.pack_into(">II", h, 276, 600, 600)
    struct.pack_into(">I", h, 340, 1)                  # NumCopies
    struct.pack_into(">II", h, 352, 595, 842)          # PageSize in points
    struct.pack_into(">II", h, 372, W, H)              # cupsWidth/Height
    struct.pack_into(">III", h, 384, 8, 24, W * 3)     # bits/colour, bits/pixel, bytes/line
    struct.pack_into(">II", h, 396, 0, 19)             # chunky, sRGB
    struct.pack_into(">I", h, 420, 3)                  # cupsNumColors
    struct.pack_into(">I", h, 452, 1)                  # TotalPageCount
    s(1732, "iso_a4_210x297mm")
    return bytes(h)

out = bytearray(b"RaS2") + header()
y = 0
while y < H:
    row = pixel_row(y); run = 1
    while y + run < H and run < 256 and pixel_row(y + run) == row: run += 1
    out.append(run - 1); out += encode_line(row)
    y += run
open(sys.argv[1], "wb").write(out)
print(len(out), "bytes")
