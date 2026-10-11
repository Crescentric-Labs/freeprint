# Mac spike tools

Throwaway C and Python used to prove IPP-over-USB on the G2020 before touching Android.
See `docs/findings.md` and `docs/process.md`.

- `dump.c`: dump USB descriptors and the IEEE 1284 device ID (read-only).
- `ippusb.c <if>`: IPP `Get-Printer-Attributes` over USB interface `<if>` (2 or 3).
- `ippprint.c <if> <file.pwg>`: IPP `Print-Job` of a PWG raster file. Prints a page.
- `mkpwg.py <out.pwg>`: writes the A4 600 dpi test page (run with `python3 -I`).

```
cc -o ippprint ippprint.c -I/opt/homebrew/include/libusb-1.0 -L/opt/homebrew/lib -lusb-1.0
```
