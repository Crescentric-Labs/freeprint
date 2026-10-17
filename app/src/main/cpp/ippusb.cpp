// IPP-over-USB spike. Takes the file descriptor Android's UsbManager hands out,
// wraps it with libusb, claims an IPP interface (class 7, subclass 1, protocol 4,
// alternate setting 1 on the G2020), and runs one HTTP+IPP exchange over bulk USB.
#include <jni.h>
#include <android/log.h>
#include "ipp_usb.h"

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#define TAG "FreePrintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

using namespace ippusb;

// Runs one request (HTTP headers + IPP bytes + optional document) over the first
// IPP interface that works. Returns a human-readable log.
std::string transact(int fd, const Bytes &ipp, const uint8_t *doc, size_t docLen) {
    std::string log;
    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    int r = libusb_init(nullptr);
    if (r != 0) return std::string("libusb_init: ") + libusb_error_name(r);

    libusb_device_handle *h = nullptr;
    r = libusb_wrap_sys_device(nullptr, (intptr_t)fd, &h);
    if (r != 0) {
        libusb_exit(nullptr);
        return std::string("libusb_wrap_sys_device: ") + libusb_error_name(r);
    }
    log += "wrapped fd " + std::to_string(fd) + "\n";

    std::vector<Endpoints> candidates = findIppInterfaces(libusb_get_device(h));
    log += "IPP-over-USB interfaces: " + std::to_string(candidates.size()) + "\n";

    for (const Endpoints &e : candidates) {
        char line[160];
        snprintf(line, sizeof line, "try IF %d alt %d OUT 0x%02x IN 0x%02x\n", e.interfaceNumber, e.altSetting, e.out, e.in);
        log += line;
        r = libusb_claim_interface(h, e.interfaceNumber);
        if (r != 0) { log += std::string("  claim: ") + libusb_error_name(r) + "\n"; continue; }
        r = libusb_set_interface_alt_setting(h, e.interfaceNumber, e.altSetting);
        if (r != 0) {
            log += std::string("  set alt: ") + libusb_error_name(r) + "\n";
            libusb_release_interface(h, e.interfaceNumber);
            continue;
        }

        char hdr[256];
        int hl = snprintf(hdr, sizeof hdr,
                          "POST /ipp/print HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/ipp\r\n"
                          "Content-Length: %zu\r\n\r\n", ipp.size() + docLen);
        bool ok = writeAll(h, e.out, reinterpret_cast<uint8_t *>(hdr), hl, log) &&
                  writeAll(h, e.out, ipp.data(), ipp.size(), log) &&
                  (docLen == 0 || writeAll(h, e.out, doc, docLen, log));
        if (ok) {
            log += "sent " + std::to_string(hl + ipp.size() + docLen) + " bytes\n";
            Bytes resp = readResponse(h, e.in, log);
            int st = ippStatus(resp);
            char buf[64];
            snprintf(buf, sizeof buf, "IPP status: 0x%04x\n", st < 0 ? 0xffff : st);
            log += buf;
            log += "---- response ----\n" + render(resp) + "\n";
        }
        libusb_release_interface(h, e.interfaceNumber);
        if (ok) break;
    }
    libusb_close(h);
    libusb_exit(nullptr);
    LOGI("%s", log.c_str());
    return log;
}

jstring toJString(JNIEnv *env, const std::string &s) { return env->NewStringUTF(s.c_str()); }

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_crescentric_freeprint_otg_NativeIpp_getPrinterAttributes(JNIEnv *env, jobject, jint fd) {
    Bytes ipp = ippHeader(0x000B, 1);                      // Get-Printer-Attributes
    sattr(ipp, 0x44, "requested-attributes", "printer-make-and-model");
    ipp.push_back(0x03);
    return toJString(env, transact(fd, ipp, nullptr, 0));
}

extern "C" JNIEXPORT jstring JNICALL
Java_crescentric_freeprint_otg_NativeIpp_printPwgRaster(JNIEnv *env, jobject, jint fd, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    Bytes doc(len);
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(doc.data()));

    Bytes ipp = ippHeader(0x0002, 2);                      // Print-Job
    sattr(ipp, 0x42, "job-name", "freeprint-spike");
    sattr(ipp, 0x49, "document-format", "image/pwg-raster");
    ipp.push_back(0x02);                                   // job-attributes-tag
    sattr(ipp, 0x44, "media", "iso_a4_210x297mm");
    sattr(ipp, 0x44, "print-color-mode", "color");
    iattr(ipp, 0x23, "print-quality", 4);
    iattr(ipp, 0x21, "copies", 1);
    ipp.push_back(0x03);
    return toJString(env, transact(fd, ipp, doc.data(), doc.size()));
}
