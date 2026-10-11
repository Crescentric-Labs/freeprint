# Findings: Canon PIXMA G2020 over USB

Test printer: Canon PIXMA G2020 (USB only). Findings from Phase 0 and the Mac half of
Phase 1, all verified on real hardware on 2026-10-11 unless marked otherwise.

## Summary

- The G2020 prints **driverless over IPP-over-USB**. Send `image/pwg-raster` (or `image/urf`)
  in an IPP `Print-Job`, tunnelled as HTTP over USB bulk endpoints. No driver, no Gutenprint.
- The Gutenprint path **did not print** for this model (see below). It stays in the plan only
  as a fallback for other host-based printers.
- The IPP interfaces are **alternate setting 1** of interfaces 2 and 3. `ioreg` and
  `system_profiler` only show alt setting 0, which hid them. Use libusb (or Android's
  `UsbDevice.getInterface`, which lists every alt setting) to see them.

## Device

| Item | Value |
|---|---|
| USB IDs | VID `0x04a9`, PID `0x18bd` |
| Speed | USB 2.0 high speed, one configuration, 6 interfaces |
| CUPS URI (macOS) | `usb://Canon/G2020%20series?serial=034DB5` |
| Legacy device ID (IF1) | `MFG:Canon;CMD:IVEC;MDL:G2020 series;CLS:PRINTER;CID:CA_IVEC1TYPE4_IJP;` |
| IPP device ID | `...CMD:IVEC,URF;...URF:V1.4,CP1,PQ3-5,RS600,SRGB24,W8,OB9,OFU0,IS1;` |
| HTTP-interface device ID (IF2/IF3) | `MFG:Canon;CMD:MultiPass 2.1,IVEC;MDL:G2020 series HTTP;` |

### Interface layout (libusb)

| IF | alt | class/sub/proto | OUT / IN | Use |
|---|---|---|---|---|
| 0 | 0 | 255/0/255 | 0x07 / 0x88, interrupt 0x89 | vendor (probably scanner) |
| 1 | 0 | 7/1/2 | 0x01 / 0x82 | legacy bidirectional printing (IVEC) |
| 2 | 0 | 255/255/255 | 0x0c / 0x8d | vendor |
| **2** | **1** | **7/1/4** | **0x0c / 0x8d** | **IPP-over-USB** |
| 3 | 0 | 255/255/255 | 0x0f / 0x8e | vendor |
| **3** | **1** | **7/1/4** | **0x0f / 0x8e** | **IPP-over-USB** |
| 4 | 0 | 255/1/1 | 0x03 / 0x86 | vendor |
| 5 | 0 | 255/1/1 | 0x0a / 0x8b | vendor |

All endpoints are bulk with 512-byte packets except the interrupt endpoint on IF0.
There are two IPP interfaces, most likely so printing and scanning can each have one.

## IPP capabilities (queried with `ipptool` against the printer, not a CUPS queue)

- Document formats: `image/urf`, `image/pwg-raster`, `image/jpeg`, `application/octet-stream`.
  **No PDF**, so the app must render pages itself.
- Resolution: 600 dpi only. Raster types: `srgb_8`, `sgray_8`.
- `print-color-mode`: color, monochrome, auto, auto-monochrome.
- `print-quality`: draft, normal, high. IPP 1.1 and 2.0. Default media A4.
- URF: `V1.4,CP1,PQ3-5,RS600,SRGB24,W8,OB9,OFU0,IS1`.
- Exposes ink levels (`marker-levels`) and `printer-state-reasons`, useful for the UI later.
- Duplex not supported (`sides-supported = one-sided`).

## What worked (Mac, libusb, `tools/mac-spike/`)

1. `dump.c` lists descriptors and the IEEE 1284 device ID per interface. It found the
   class 7 / protocol 4 alt settings.
2. `ippusb.c` claims IF2 (and IF3), sets alt 1, writes an HTTP `POST /ipp/print` carrying an
   IPP `Get-Printer-Attributes`, and reads the reply. HTTP 200 with a valid IPP body on both.
3. `mkpwg.py` writes an A4, 600 dpi, sRGB 8-bit PWG raster test page (7.5 KB for a mostly
   white page: border, seven colour bars, 16-step grey ramp).
4. `ippprint.c` sends it as a `Print-Job` (`document-format=image/pwg-raster`,
   `media=iso_a4_210x297mm`, `print-color-mode=color`, `print-quality=4`). The printer
   returned IPP `successful-ok` and printed the page correctly: border, colours and ramp intact.

Claiming the interfaces from libusb worked while macOS's own IPP-USB bridge was running.
That may not hold on Android; it is part of what the phone spike tests.

## Protocol gotchas

- The printer answers with `Transfer-Encoding: chunked` and **keeps the connection open**
  (`Connection: Keep-Alive`). A read loop that waits for a timeout will appear to hang. End the
  read when the chunked terminator `0\r\n\r\n` arrives.
- The IPP status code is the 2 bytes after the version in the IPP payload, which starts after
  the first chunk-size line.
- Write large documents in 16 KB bulk pieces. The 7.5 KB test page fits easily; a full
  photographic page will not, so Phase 2 must stream and use chunked or Content-Length.
- PWG raster file layout used: sync word `RaS2`, then per page a 1796-byte big-endian header
  (resolution at 276, page size in points at 352, width/height at 372, bits at 384,
  colour space at 400: 19 = sRGB, 18 = sGray), then each line as
  `repeat-count-1` followed by PackBits-style pixel runs (`0..127` repeat the next pixel
  n+1 times, `128..255` copy 257-n pixels).
- One uncompressed A4 page at 600 dpi RGB is about 100 MB. Render in bands and compress per
  line instead of holding the whole page in memory on the phone.

## What did not work: Gutenprint

- Installed Gutenprint 5.3.3 (macOS cask; the Homebrew entry is flagged deprecated because
  upstream is discontinued). It has no G2020 entry. Closest PPDs:
  `gutenprint.5.3://bjc-G2000-series/expert` and `bjc-PIXMA-G2000`.
- Generated a print stream with the series PPD (A4, defaults: RGB, Plain, 600 dpi). The output
  is Canon's XML-framed IVEC protocol (`<cmd ...><ivec:operation>StartJob...`, then a
  `ModeShift`, binary raster, `EndJob`; 71 to 74 KB for a text page).
- Sent it two ways: straight to the CUPS USB backend (all bytes accepted, exit 0) and through a
  normal CUPS queue (job completed). **The printer printed nothing either time.** The printer
  was healthy (it printed via IPP minutes later), so the stream itself is not accepted by this
  model or firmware. Root cause not investigated.
- Practical consequence: do not build the Gutenprint backend until a printer needs it.

## Android result (Galaxy S25 Ultra, SM-S938B, Android 17, via OTG)

Verified 2026-10-11 with the spike app in `app/`:

- `UsbManager` permission dialog, then `openDevice` gives a file descriptor.
- `libusb_set_option(NO_DEVICE_DISCOVERY)` then `libusb_wrap_sys_device(fd)` works.
- libusb sees both IPP alt settings (IF2 alt 1 and IF3 alt 1). The app tried IF2 first: claim and
  `set_interface_alt_setting` succeeded with no kernel-driver detach needed.
- Get-Printer-Attributes: 257 bytes sent, HTTP 200, IPP status `0x0000`,
  `printer-make-and-model = Canon G2020 series`.
- Print-Job: 7,922 bytes sent (HTTP header, IPP request, 7,557-byte PWG raster), HTTP 200,
  IPP `0x0000`, `job-state-reasons = job-printing`, `job-id` 3. The page printed correctly
  (border, colour bars, grey ramp), same as the Mac result.
- The system dialog and fd lifetime behaved as documented: keep the `UsbDeviceConnection` open
  for the whole native call, close it afterwards. USB I/O ran on `Dispatchers.IO`.
- Wireless adb stays connected while the phone's single USB-C port is used by the printer.

Not yet covered: IF3 on Android, replies larger than the 32 KB read buffer, jobs larger than the
test page, error states (paper out, no ink), reconnecting after unplug, and printing while the
Android print stack or another app also holds the device.

## Phase 2 result: streaming PDF printing (S25 Ultra, 2026-10-11)

- Design: render with `PdfRenderer` one 256-row band at a time (A4 600 dpi = 4960 x 7016 px, about
  5 MB per band), fit and centre on white, convert to PWG raster in native code, and send as an
  IPP `Print-Job` using HTTP `Transfer-Encoding: chunked` (64 KB chunks, written to USB in 16 KB
  pieces). Content-Length is unknown up front, so chunked is required.
- **The G2020 accepts chunked request bodies over IPP-over-USB.** This was the main risk and it
  worked first time.
- Three streamed jobs returned IPP `0x0000` with `job-printing` (job ids 4, 5, 6) and printed
  correctly, including a real PDF chosen from the phone's storage. Claim-to-reply time was
  roughly 0.6 s, 0.3 s and 1.5 s for jobs 4, 5 and 6; the reply arrives once the whole body has been received, so these
  measure transfer, not printing time.
- Encoder correctness: `tools/pwg-test/` checks 108 synthetic cases (widths 1 to 1031, colour and
  grey, random band splits) against an independent Python decoder, and the C++ builds clean under
  AddressSanitizer and UBSan. Found while writing it: the Phase 1 Python generator (`mkpwg.py`) would
  crash on a single isolated literal pixel (control byte 257-1 = 256); its test image never hit that
  case. The C++ encoder emits a run of 1 (control byte 0) for that case instead.
- PWG raster notes: control byte 128 is never emitted; literal runs are 2 to 128 pixels, repeats 1 to
  128; line repeat count is 1 to 256.
- `ARGB_8888` bitmaps are R,G,B,A in memory on Android, so the encoder reads them directly;
  `bitmap.eraseColor(WHITE)` before each band keeps untouched regions white.

Not yet covered: colour-off printing on paper, multi-page PDFs, photo-heavy pages (PWG compresses
photos poorly, so USB throughput and printing speed matter there), polling `Get-Job-Attributes` to
report completion, and error handling (a failed job mid-stream may leave the printer needing a
power cycle).

## Open questions

- URF versus PWG raster for production. PWG raster works on Mac and Android today; URF is lighter to encode.
- Prefer IF2 or IF3? Both answer on the Mac; only IF2 has printed from Android.
- Job completion and error reporting: `Get-Job-Attributes`, `printer-state-reasons`, ink levels.
- Why Gutenprint output is rejected by this printer (low priority).
