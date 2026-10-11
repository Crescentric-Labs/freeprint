# Independent PWG raster decoder; compares every case against the source pixels.
import glob, struct, sys
bad = 0
for pwg in sorted(glob.glob("case_*.pwg")):
    d = open(pwg, "rb").read()
    src = open(pwg[:-4] + ".rgba", "rb").read()
    assert d[:4] == b"RaS2"
    h = d[4:4 + 1796]
    w, ht = struct.unpack(">II", h[372:380]); bits, bpp, bpl = struct.unpack(">III", h[384:396])
    cs = struct.unpack(">I", h[400:404])[0]
    nb = bpp // 8
    assert cs == (19 if nb == 3 else 18) and bpl == w * nb, pwg
    p = 4 + 1796; rows = []
    while len(rows) < ht:
        rep = d[p] + 1; p += 1; line = bytearray()
        while len(line) < bpl:
            c = d[p]; p += 1
            if c < 128:
                line += d[p:p + nb] * (c + 1); p += nb
            elif c == 128:
                raise SystemExit(pwg + ": control byte 128 emitted")
            else:
                n = 257 - c; line += d[p:p + n * nb]; p += n * nb
        assert len(line) == bpl, (pwg, len(line), bpl)
        rows += [bytes(line)] * rep
    assert len(rows) == ht and p == len(d), (pwg, len(rows), p, len(d))
    for y in range(ht):
        exp = bytearray()
        for x in range(w):
            r, g, b, a = src[(y * w + x) * 4:(y * w + x) * 4 + 4]
            exp += bytes((r, g, b)) if nb == 3 else bytes([(r * 77 + g * 150 + b * 29) >> 8])
        if bytes(exp) != rows[y]:
            print("MISMATCH", pwg, "row", y); bad += 1; break
print("cases:", len(glob.glob("case_*.pwg")), "failures:", bad)
sys.exit(1 if bad else 0)
