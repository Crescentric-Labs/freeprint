# Process log

How the project got from nothing to a working driverless print, in order, including the
dead ends. Dates are 2026-10-11.

## Phase 0: Mac check (Gutenprint)

1. `lpinfo -m` showed no Gutenprint models, so installed it: `brew install --cask gutenprint`
   (needs `sudo`, so it was run interactively). Version 5.3.3.
2. No G2020 PPD exists. Used `gutenprint.5.3://bjc-G2000-series/expert`, extracted with
   `/usr/libexec/cups/driver/gutenprint.5.3 cat <uri>`.
3. `cupsfilter` does not run the PPD's `rastertogutenprint` filter by itself (with
   `-m printer/foo` it stops at PostScript; with no `-m` it passes the PDF through). The working
   two-stage run:
   ```
   cupsfilter -p g2000.ppd -m application/vnd.cups-raster -o PageSize=A4 test.pdf > page.ras
   PPD=g2000.ppd /usr/libexec/cups/filter/rastertogutenprint.5.3 1 user title 1 "PageSize=A4" < page.ras > out.prn
   ```
4. macOS has no raw queues (`lpadmin ... -m raw` is refused). To send bytes unchanged:
   `DEVICE_URI='usb://Canon/G2020%20series?serial=034DB5' /usr/libexec/cups/backend/usb 1 u t 1 "" out.prn`
5. Printer connected. Both the direct send and a real CUPS queue completed without errors, and
   the printer did nothing. See `findings.md`.

## Pivot: driverless

6. macOS had already created an `ippusb://` queue (AirPrint-style PPD) when the printer was
   plugged in. A test PDF through it printed, proving the hardware was fine.
7. Queried the real printer rather than the CUPS queue (the first query hit the local queue and
   returned CUPS's own format list, which was misleading): resolve the bridge port with
   `dns-sd -L "Canon G2020 series" _ipp._tcp local.` then
   `ipptool -tv http://localhost:<port>/ipp/print get-printer-attributes.test`.
   It supports URF and PWG raster at 600 dpi.
8. Earlier claim that the G2020 has no IPP-over-USB interface was **wrong**: it came from
   `ioreg`, which only shows alt setting 0. libusb showed the class 7 / protocol 4 interfaces
   at alt setting 1.

## Phase 1, Mac half: libusb spike (`tools/mac-spike/`)

9. `dump.c`: all descriptors plus `GET_DEVICE_ID` for each interface. Needs `brew install libusb`.
10. `ippusb.c`: claim IF2, set alt 1, bulk-write an HTTP+IPP `Get-Printer-Attributes`, read the
    reply. Worked on IF2 and IF3.
11. `mkpwg.py` + independent decode check (7016 rows of 4960 pixels, all bytes consumed) + `ippprint.c`
    `Print-Job`. Printed correctly.

Build the C tools:
```
cc -o dump dump.c -I/opt/homebrew/include/libusb-1.0 -L/opt/homebrew/lib -lusb-1.0
python3 -I mkpwg.py test-a4.pwg
```

## Phase 1, Android half: scaffold

12. Installed the toolchain (see `dev-setup.md`).
13. Created the Gradle project in the repo root: one `:app` module, Kotlin and Compose, minSdk 26,
    target and compile SDK 35 (the Galaxy S25 Ultra runs Android 17; API 35 is a conservative target), arm64-v8a only.
14. Vendored libusb 1.0.30 (LGPL 2.1) as source under `app/src/main/cpp/third_party/libusb/`,
    built as a static library by CMake with the Android config shipped in the release.
    Release tarball SHA-256: `fea36f34f9156400209595e300840767ab1a385ede1dc7ee893015aea9c6dbaf`.
15. `app/src/main/cpp/ippusb.cpp` ports the Mac spike to JNI: wrap the `UsbManager` file
    descriptor with `libusb_wrap_sys_device` (device discovery disabled, since Android forbids
    enumerating `/dev/bus/usb`), find every class 7/1/4 alt setting, claim, set alt, run one
    HTTP+IPP exchange, read until the chunked terminator.
16. Kotlin side: `UsbPrinter` (find device, request permission with a mutable `PendingIntent`,
    open, dump descriptors), `NativeIpp` (JNI declarations), `MainActivity` (Compose spike UI,
    all USB I/O on `Dispatchers.IO`). `test-a4.pwg` is bundled in `assets/`.
17. `./gradlew assembleDebug` builds a ~9 MB APK containing `libfreeprint_native.so` for arm64-v8a.
    Run on the phone in the next step.

## Phase 1, Android half: verified on the phone

18. Paired the S25 Ultra over wireless debugging (`adb pair`, `adb connect`; the phone also
    appears via mDNS as a second `adb devices` entry), installed the APK with `adb install -r`,
    and watched `adb logcat` (native tag `FreePrintNative`).
19. With the printer on OTG: Get-Printer-Attributes returned IPP `0x0000`; Print-Job returned
    `0x0000` with `job-printing` and the test page printed correctly. Details in
    `findings.md`. Phase 1 is verified on hardware.
20. A logcat monitor command that included `*:S` unquoted failed under zsh (glob expansion);
    quote it as `'*:S'`.

## Phase 2: native pipeline

21. Wrote `pwg_encoder.h`, a streaming PWG raster encoder with no Android dependencies, and a host
    test (`tools/pwg-test/gen.cpp` + `check.py`): 108 cases decode pixel-exact with sanitizers on.
    Run: `c++ -std=c++17 -fsanitize=address,undefined -o gen tools/pwg-test/gen.cpp && ./gen && python3 -I tools/pwg-test/check.py`
    (in an empty scratch directory; it writes `case_*` files).
22. Moved shared IPP and USB helpers into `ipp_usb.h`. New `print_session.cpp` streams a Print-Job
    with chunked HTTP; `PrintSession.kt` wraps it; `PdfPrinter.kt` renders bands with `PdfRenderer`.
    UI gained Pick PDF, Print PDF and a colour switch (a bundled one-page sample is used if no PDF
    is chosen).
23. Installed over wireless adb and watched `logcat`. Jobs 4, 5 and 6 returned IPP `0x0000` /
    `job-printing` and all printed correctly, including a real PDF from the phone. Phase 2 verified.

## Phase 3: app features and print service (built 2026-10-11)

25. UI features added in order: layout core (`PageLayout` pure maths + `SheetComposer`), position and scale,
    orientation, pages per sheet, page selection, actual-size mode. Name clashes between Compose state
    properties and `setX` functions (`setOptions`, `setParity`) broke the build twice; the functions are called `updateX`.
26. The layout and selection maths have JVM unit tests (33 in total). Android `Matrix` cannot run in plain
    unit tests, so the placement maths was split out as `PageLayout.place`.
27. Print service: `UsbPrintService` (jobs), `UsbDiscoverySession` (printer appears and disappears with USB),
    `PrinterProfile` (capabilities and media mapping), `JobExecutor` (shared by the app and the service),
    `printservice.xml`. Registered with the system; awaiting an on-phone test.

## Rename

24. Application ID and package renamed from the placeholder `org.freeprint.otg` to
    `crystaldigits.freeprint.otg`: Gradle `applicationId` and `namespace`, source folders and `package`
    lines, JNI function names (`Java_crystaldigits_freeprint_otg_*`, checked with `nm -D`), and the
    `USB_PERMISSION` broadcast action. It is a different app to Android, so the old build was uninstalled
    from the phone and the USB permission has to be granted again.

## Cleanup

- Removed generated artefacts: Gutenprint streams (`out.prn`, `out-a4.prn`), the generated PPD,
  the compiled Mac binaries, and scratch test inputs. They are reproducible from the steps above.
- Moved the Mac spike sources from `spike-mac/` to `tools/mac-spike/`.
