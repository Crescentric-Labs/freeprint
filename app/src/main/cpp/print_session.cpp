// Streaming print session: one IPP Print-Job whose PWG raster body is produced band by band
// and sent with HTTP chunked transfer-encoding, so no whole page is ever held in memory.
//
// Call order from Kotlin (all on one background thread):
//   open(fd) -> beginJob(color) -> [beginPage -> addBand* -> endPage]+ -> finish() -> close()
#include <jni.h>
#include <android/log.h>

#include "ipp_usb.h"
#include "pwg_encoder.h"

#include <memory>
#include <string>

#define TAG "FreePrintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

using namespace ippusb;

constexpr size_t kHttpChunk = 64 * 1024;   // HTTP chunk size; flushed as several 16 KB USB writes

class Session {
public:
    ~Session() { close(); }

    bool open(int fd) {
        libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
        int r = libusb_init(nullptr);
        if (r != 0) return fail(std::string("libusb_init: ") + libusb_error_name(r));
        initialised_ = true;
        r = libusb_wrap_sys_device(nullptr, (intptr_t)fd, &h_);
        if (r != 0) return fail(std::string("libusb_wrap_sys_device: ") + libusb_error_name(r));

        std::vector<Endpoints> candidates = findIppInterfaces(libusb_get_device(h_));
        if (candidates.empty()) return fail("no IPP-over-USB interface (class 7/1/4) on this device");
        for (const Endpoints &e : candidates) {
            if (libusb_claim_interface(h_, e.interfaceNumber) != 0) continue;
            if (libusb_set_interface_alt_setting(h_, e.interfaceNumber, e.altSetting) != 0) {
                libusb_release_interface(h_, e.interfaceNumber);
                continue;
            }
            ep_ = e;
            claimed_ = true;
            LOGI("claimed IF %d alt %d OUT 0x%02x IN 0x%02x", e.interfaceNumber, e.altSetting, e.out, e.in);
            return true;
        }
        return fail("could not claim any IPP-over-USB interface");
    }

    void close() {
        if (claimed_) libusb_release_interface(h_, ep_.interfaceNumber);
        claimed_ = false;
        if (h_) libusb_close(h_);
        h_ = nullptr;
        if (initialised_) libusb_exit(nullptr);
        initialised_ = false;
    }

    bool isOpen() const { return claimed_; }

    // Sends the HTTP request line and headers, then buffers the IPP Print-Job request as the
    // first piece of the chunked body.
    bool beginJob(bool color, const char *media, int quality, int copies) {
        const char hdr[] =
            "POST /ipp/print HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/ipp\r\n"
            "Transfer-Encoding: chunked\r\n\r\n";
        if (!writeAll(h_, ep_.out, reinterpret_cast<const uint8_t *>(hdr), sizeof hdr - 1, err_)) return false;

        Bytes ipp = ippHeader(0x0002, 1);                      // Print-Job
        sattr(ipp, 0x42, "job-name", "freeprint");
        sattr(ipp, 0x49, "document-format", "image/pwg-raster");
        ipp.push_back(0x02);                                   // job-attributes-tag
        sattr(ipp, 0x44, "media", media);
        sattr(ipp, 0x44, "print-color-mode", color ? "color" : "monochrome");
        iattr(ipp, 0x23, "print-quality", quality);   // 3 draft, 4 normal, 5 high
        iattr(ipp, 0x21, "copies", copies);
        ipp.push_back(0x03);
        color_ = color;
        media_ = media;
        encoder_ = std::make_unique<pwg::Encoder>([this](const uint8_t *d, size_t n) { return body(d, n); });
        return body(ipp.data(), ipp.size()) && encoder_->startDocument();
    }

    bool beginPage(int w, int h, int dpi, int ptW, int ptH) {
        return encoder_ && encoder_->startPage(w, h, dpi, ptW, ptH, color_, media_.c_str());
    }
    bool addBand(const uint8_t *rgba, int rows) { return encoder_ && encoder_->addRgbaRows(rgba, rows); }
    bool endPage() { return encoder_ && encoder_->endPage(); }

    // Ends the chunked body and returns the log of the printer's reply.
    std::string finish() {
        std::string log;
        if (!flushChunk() || !writeAll(h_, ep_.out, reinterpret_cast<const uint8_t *>("0\r\n\r\n"), 5, err_))
            return err_;
        Bytes resp = readResponse(h_, ep_.in, err_);
        int st = ippStatus(resp);
        char buf[64];
        snprintf(buf, sizeof buf, "IPP status: 0x%04x\n", st < 0 ? 0xffff : st);
        log = err_ + buf + "---- response ----\n" + render(resp) + "\n";
        LOGI("%s", log.c_str());
        return log;
    }

    const std::string &error() const { return err_; }

private:
    bool fail(const std::string &m) { err_ += m + "\n"; LOGI("%s", m.c_str()); return false; }

    // Appends body bytes, sending an HTTP chunk whenever kHttpChunk have accumulated.
    bool body(const uint8_t *d, size_t n) {
        buf_.insert(buf_.end(), d, d + n);
        return buf_.size() < kHttpChunk || flushChunk();
    }

    bool flushChunk() {
        if (buf_.empty()) return true;
        char head[24];
        int hl = snprintf(head, sizeof head, "%zx\r\n", buf_.size());
        Bytes out;
        out.reserve(hl + buf_.size() + 2);
        out.insert(out.end(), head, head + hl);
        out.insert(out.end(), buf_.begin(), buf_.end());
        out.push_back('\r'); out.push_back('\n');
        buf_.clear();
        return writeAll(h_, ep_.out, out.data(), out.size(), err_);
    }

    libusb_device_handle *h_ = nullptr;
    Endpoints ep_;
    bool initialised_ = false, claimed_ = false, color_ = true;
    std::string media_;
    std::string err_;
    Bytes buf_;
    std::unique_ptr<pwg::Encoder> encoder_;
};

Session *S(jlong h) { return reinterpret_cast<Session *>(h); }

}  // namespace

#define JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_crescentric_freeprint_otg_PrintSession_##name

JNI(jlong, nativeOpen)(JNIEnv *, jobject, jint fd) {
    auto *s = new Session();
    s->open(fd);
    return reinterpret_cast<jlong>(s);
}
JNI(jboolean, nativeIsOpen)(JNIEnv *, jobject, jlong h) { return S(h)->isOpen(); }
JNI(jstring, nativeError)(JNIEnv *env, jobject, jlong h) { return env->NewStringUTF(S(h)->error().c_str()); }
JNI(jboolean, nativeBeginJob)(JNIEnv *env, jobject, jlong h, jboolean color, jstring media, jint quality, jint copies) {
    const char *m = env->GetStringUTFChars(media, nullptr);
    bool ok = S(h)->beginJob(color, m, quality, copies);
    env->ReleaseStringUTFChars(media, m);
    return ok;
}
JNI(jboolean, nativeBeginPage)(JNIEnv *, jobject, jlong h, jint w, jint ht, jint dpi, jint ptW, jint ptH) {
    return S(h)->beginPage(w, ht, dpi, ptW, ptH);
}
JNI(jboolean, nativeAddBand)(JNIEnv *env, jobject, jlong h, jobject buffer, jint rows) {
    auto *p = static_cast<const uint8_t *>(env->GetDirectBufferAddress(buffer));
    return p != nullptr && S(h)->addBand(p, rows);
}
JNI(jboolean, nativeEndPage)(JNIEnv *, jobject, jlong h) { return S(h)->endPage(); }
JNI(jstring, nativeFinish)(JNIEnv *env, jobject, jlong h) { return env->NewStringUTF(S(h)->finish().c_str()); }
JNI(void, nativeClose)(JNIEnv *, jobject, jlong h) { delete S(h); }
