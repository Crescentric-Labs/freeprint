# Samsung QPDL ("SPL2") job format for the ML-2160 — functional description

Purpose: describe the byte stream a Samsung ML-2160 (monochrome laser, 600 dpi) accepts over its USB
"printer" interface, so an independent encoder can be written. This text states facts about the
device's input format. It contains no program code. Numbers are decimal unless written 0x...

Items marked **(verify)** are believed correct but must be confirmed against a real printer or
against reference output before being trusted.

## 1. Transport

- The printer exposes USB class 7, subclass 1, protocol 2 (a plain bidirectional printer port): one
  bulk-OUT and one bulk-IN endpoint, 512-byte packets. The job is written to bulk-OUT as one
  continuous byte stream. No handshake or status read is needed for a basic print. **(verify)**
- Its IEEE-1284 device ID contains `CMD:SPL` and model `ML-2160 Series`.

## 2. Job layout

```
JOB   := PJL_HEADER  PAGE*  PJL_FOOTER
PAGE  := PAGE_HEADER  BAND*  PAGE_FOOTER
```

### 2.1 PJL header (ASCII text)

1. The 5 bytes `0x1B '%' '-' '1' '2' '3' '4' '5' 'X'` (this is ESC followed by `%-12345X`, the
   Universal Exit Language sequence, 9 bytes in total).
2. Text lines, each ended by a single LF (0x0A):
   ```
   @PJL DEFAULT SERVICEDATE=YYYYMMDD       (today's date)
   @PJL SET USERNAME="<name>"
   @PJL SET JOBNAME="<job title>"
   @PJL SET JAMRECOVERY=OFF
   @PJL SET DUPLEX=OFF                     (see section 5 for other duplex values)
   @PJL SET PAPERTYPE=OFF
   @PJL SET ALTITUDE=LOW
   @PJL SET DENSITY=3                      (1..5; 3 is the normal setting)
   @PJL SET RET=NORMAL
   @PJL ENTER LANGUAGE = QPDL
   ```
   The `ENTER LANGUAGE` line (note the spaces around `=`) must be last; binary data follows
   immediately after its LF. Optional lines `@PJL SET ECONOMODE=ON` (toner save) may be added before
   `JAMRECOVERY`. **(verify order tolerance)**

### 2.2 PJL footer

The byte 0x09 (TAB) followed by the 9-byte UEL sequence from 2.1 item 1.

## 3. Page header (17 bytes)

| offset | size | value |
|---|---|---|
| 0 | 1 | 0x00 (marks a page start) |
| 1 | 1 | vertical resolution / 100 → 6 for 600 dpi |
| 2 | 2 | copies, big-endian (1 for one copy) |
| 4 | 1 | paper size code (table below) |
| 5 | 2 | page width in pixels, big-endian (section 6) |
| 7 | 2 | page height in pixels, big-endian (section 6) |
| 9 | 1 | paper source: 1 = default tray |
| 10 | 1 | 0x00 (meaning unknown) |
| 11 | 1 | 0x01 for single-sided, 0x00 for the duplex modes of section 5 |
| 12 | 1 | 0x00 normally; 1 on back sides when duplexing (section 5) |
| 13 | 1 | 0x00 (meaning unknown) |
| 14 | 1 | 0x03 (format version 3) |
| 15 | 1 | 0x01 (meaning unknown) |
| 16 | 1 | horizontal resolution / 100 → 6 |

Paper size codes: Letter 0, Legal 1, A4 2, Executive 3, Ledger 4, A3 5, Env#10 6, Monarch 7, C5 8,
DL 9, B4 10, B5 11, EnvISOB5 12, Postcard 14, A5 16, A6 17, B6 18, Custom 21, C6 23.

## 4. Page footer (3 bytes)

0x01, then copies as 2 bytes big-endian.

## 5. Duplex

Simplex: PJL `SET DUPLEX=OFF`, page-header byte 11 = 1, byte 12 = 0. For manual duplex (the ML-2160 has
no automatic duplexer) the host prints the odd pages, the user re-feeds the stack, then it prints the
even pages as a second job; for that the PJL is `@PJL SET DUPLEX=MANUAL` plus
`@PJL SET BINDING=LONGEDGE` (or `SHORTEDGE`), the header byte 11 = 0, and byte 12 = 1 on the even
(back-side) pages, with paper source 3 on the odd pages except the last. **(verify)** Free Print
already handles two-pass manual duplex itself, so a first version can simply use simplex jobs.

## 6. Geometry (600 × 600 dpi)

- Page width in pixels = (paper width in points / 72 × 600), rounded up, then rounded up to a
  multiple of 8. A4 (595 × 842 pt): width 4960.
- Page height in pixels = (paper height in points / 72 × 600) rounded up, **minus the vertical hard
  margin** of 105 rows (12.5 pt rounded up at 600 dpi); written in the page header after that
  subtraction. **(verify exact rounding)**
- Hard margins are 12.5 pt on every side. Horizontally this is 104.2 px rounded up to a multiple of
  8 = 112 px = 14 bytes. The first 105 pixel rows of the rendered page and the first 14 byte-columns
  of every row are not sent (they cannot be printed). Rendered content is cut there.
- The page is processed in horizontal **bands of 128 pixel rows**. The last band is padded with
  white rows to 128 rows. A band that contains no black pixel at all is **not sent**; band numbers
  (section 7) still count every band, starting at 0, so numbers simply skip.
- Band width = the page width in pixels (a multiple of 8; A4: 4960 px = 620 bytes per row). The
  rendered page is shifted left by the 14-byte hard margin, so the last 14 byte-columns of every
  band row are white padding. **(verify on a real print)**

## 7. Band record

For every non-empty band, in increasing band number:

```
0x0C
band number            1 byte
band width (pixels)    2 bytes, big-endian
band height            2 bytes, big-endian (128)
compression id         1 byte: 0x11
record length N        4 bytes, big-endian
signature              4 bytes: EF CD AB 09 in this order
compressed data        M bytes (section 8)
checksum               4 bytes, big-endian
```

N = 4 (signature) + M + 4 (checksum). The checksum is the 32-bit unsigned sum of every byte of the
compressed data plus the four signature bytes (0xEF+0xCD+0xAB+0x09), taken modulo 2^32.

## 8. Band pixels and the 0x11 compression

Band pixel arrangement before compression:

- 1 bit per pixel, most significant bit = leftmost pixel of its byte.
- **Black is bit 0, white is bit 1** (a normal "1 = black" bitmap is inverted before sending).
- The byte array is **column-major**: for byte-column x (0 at the left of the band) and pixel row y
  (0 at the top of the band, 0..127), the byte is stored at index `x × 128 + y`. So the stream walks
  down one 8-pixel-wide column, then moves to the next column.

The compressed block is a self-contained sequence:

1. 4 bytes: L, a little-endian count of leading literal bytes.
2. 64 little-endian 16-bit numbers: the **distance table** (each entry is a back-reference
   distance in bytes, 1 or more).
3. L raw bytes (the first L bytes of the band, copied as is). L must be at least as large as the
   largest table entry and at most 128. **(verify the exact bound)**
4. Then a series of operations until all band bytes (width_bytes × 128) are produced:
   - Byte `b` with top bit 0: a **literal run** — the next `(b & 0x7F) + 1` bytes are copied to the
     output as is (1 to 128 bytes).
   - Byte `b` with top bit 1, followed by byte `c`: a **copy** — copy `n` bytes from earlier output,
     starting `distance` bytes back from the current end of output, where
     `distance = table[c & 0x3F]` and `n = 3 + ((b & 0x7F) | ((c & 0xC0) << 1))`
     (so n = 3 .. 514). The copy may overlap its own output (byte-by-byte).

An encoder is free to choose any valid table and any valid operations; the printer only decodes. A
trivial encoder that uses a table of 64 ones (or any values ≤ L) and only literal runs yields a
valid, uncompressed stream; real compression is optional but shrinks 600 dpi pages a lot.
A convenient table is the 64 distances that occur most often between equal bytes in the band, and
including distance 1 helps with long white runs. A single compressed band record must not be larger
than the uncompressed band (if it would be, the reference driver aborts; an encoder should fall back to literals only).

## 9. USB writing

Write the stream in chunks that are a multiple of the 512-byte packet size where possible; a short
final packet ends the transfer. Use a generous timeout (the printer pauses while it prints). **(verify)**

## 10. Things deliberately left out

Only the monochrome 600 dpi simplex/manual-duplex path of the ML-2160 is described. Colour models,
other compression ids and other format versions are different and not covered.
