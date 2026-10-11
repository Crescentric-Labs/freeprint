# Development setup (macOS, Apple Silicon)

## Toolchain installed for this project

| Tool | Version | How |
|---|---|---|
| JDK | OpenJDK 17 | `brew install openjdk@17` (the default JDK 25 is too new for this AGP) |
| Gradle | 8.10.2 via wrapper | `brew install gradle` was used once to generate `gradlew`; the wrapper downloads 8.10.2 |
| Android command-line tools | latest | `brew install --cask android-commandlinetools` (SDK root `/opt/homebrew/share/android-commandlinetools`) |
| SDK packages | platform-tools, `platforms;android-35`, `build-tools;35.0.0`, `cmake;3.22.1`, `ndk;27.2.12479018` | `sdkmanager` |
| libusb (Mac tools only) | 1.0.30 | `brew install libusb` |

Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01 (see `gradle/libs.versions.toml`).

Environment for each shell (or put in `~/.zshrc`):
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/platform-tools:$PATH
```
`local.properties` (git-ignored) holds `sdk.dir`. Licences were accepted with `sdkmanager --licenses`.

## Build

```
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```
The first build downloads Gradle and dependencies and compiles libusb; expect a few minutes.

## Run on the Galaxy S25 Ultra

The phone has one USB-C port and the printer needs it, so install and read logs **wirelessly**.

1. Phone: Settings, About phone, Software information, tap Build number 7 times; then
   Settings, Developer options: enable USB debugging and Wireless debugging.
2. Same Wi-Fi as the Mac. In Wireless debugging choose "Pair device with pairing code".
3. Mac:
   ```
   adb pair <ip>:<pairing-port>        # enter the 6-digit code
   adb connect <ip>:<connect-port>     # port shown on the Wireless debugging screen
   adb devices
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb logcat -s FreePrintNative:I AndroidRuntime:E     # or: adb logcat FreePrintNative:I AndroidRuntime:E '*:S'
   ```
4. Connect the printer to the phone with a USB-C OTG adapter and the printer's USB cable. Power
   the printer on. The phone may offer to open the app when it attaches.
5. In the app: Scan (dump interfaces), Permission (accept the system dialog), Attributes
   (IPP `Get-Printer-Attributes`), then Print test page (load A4 first).

Verified working on a Galaxy S25 Ultra (Android 17). What to look for: Scan should list IF 2 and IF 3 with `alt 1 class 7 sub 1 proto 4`. If Attributes
logs `libusb_wrap_sys_device` or `claim` errors, record the exact error name in `findings.md`.

## Mac-side tools

`tools/mac-spike/` holds the C programs and the PWG generator used before the Android work
(build commands in `process.md`). They need the printer plugged into the Mac.

## Testing the print service on the phone

1. Install the APK. Then enable the service: Settings, Connections, More connection settings, Printing (on
   other phones: Settings, Connected devices, Connection preferences, Printing), switch on "Free Print".
2. Connect the printer. From any app (Chrome, Files, Gallery) choose Share or Print, then pick the USB printer
   in the print dialog's printer list.
3. The first job asks for USB access with the system dialog; allow it. Choosing "Always open Free Print" when
   the printer is plugged in makes that permission stick.
4. Watch `adb logcat FreePrintNative:I AndroidRuntime:E '*:S'` and `adb shell dumpsys print` for job state.

## Languages (English and Sinhala)

All user-facing text lives in `app/src/main/res/values/strings.xml` (English) and `values-si/strings.xml` (Sinhala).
Do not edit those two files by hand: they are generated from one table so the languages cannot drift apart.
Edit `tools/gen_strings.py`, then run `python3 -I tools/gen_strings.py` from the repo root.

- Language button in the top bar: System default, English or සිංහල. The choice is saved in `app_settings` and applied
  to the activity (`attachBaseContext`) and to the print service's own messages (through a wrapped context, because
  `PrintService.attachBaseContext` is final). Changing it recreates the activity; the document and options stay.
- Android 13 and later also list both languages in the system per-app language setting (`locales_config.xml`).
- Technical detail from the printer layers (for example "write error: LIBUSB_ERROR_TIMEOUT") stays English under a
  translated headline.
- Sinhala wording was written without a native reviewer; ask a Sinhala speaker to review before a public release.
