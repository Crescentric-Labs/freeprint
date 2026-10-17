// A small HTTP/1.1 client over IPP-over-USB, for talking to the printer's web endpoints other than the print
// operation: scanner capabilities, scan jobs and scanned pages (eSCL), and the admin page. One request at a time;
// the connection stays open between requests because the printer keeps it alive.
#include <jni.h>
#include <android/log.h>

#include "ipp_usb.h"

#include <cstdlib>
#include <string>

#define TAG "FreePrintNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

using namespace ippusb;

constexpr size_t kMaxBody = 128u * 1024 * 1024;   // refuse anything larger than a very big scan
constexpr int kReadSize = 65536;

std::string lower(std::string s) {
    for (char &c : s) if (c >= 'A' && c <= 'Z') c = char(c - 'A' + 'a');
    return s;
}

// Incremental decoder for "Transfer-Encoding: chunked" bodies. Feed it the bytes received so far; it remembers how far
// it got, so a large body is not parsed again from the start each time.
class ChunkedDecoder {
public:
    // Returns true when the final chunk (and its trailer) has been seen.
    bool feed(const Bytes &buf, size_t start) {
        const char crlf[] = "\r\n";
        while (true) {
            auto lf = std::search(buf.begin() + pos_ + start, buf.end(), crlf, crlf + 2);
            if (lf == buf.end()) return false;                       // chunk-size line incomplete
            size_t lineLen = size_t(lf - (buf.begin() + pos_ + start));
            std::string line(reinterpret_cast<const char *>(&buf[pos_ + start]), lineLen);
            size_t size = strtoull(line.c_str(), nullptr, 16);       // ignores any ";extension"
            size_t dataStart = pos_ + lineLen + 2;
            if (size == 0) {
                // Trailer: optional header lines, then a blank line. We only need to see the blank line.
                size_t p = dataStart;
                while (true) {
                    auto e = std::search(buf.begin() + start + p, buf.end(), crlf, crlf + 2);
                    if (e == buf.end()) return false;
                    size_t len = size_t(e - (buf.begin() + start + p));
                    p += len + 2;
                    if (len == 0) { done_ = true; return true; }
                }
            }
            if (start + dataStart + size + 2 > buf.size()) return false;   // chunk data incomplete
            out.insert(out.end(), buf.begin() + start + dataStart, buf.begin() + start + dataStart + size);
            pos_ = dataStart + size + 2;
            if (out.size() > kMaxBody) return true;                  // caller checks the size
        }
    }
    Bytes out;
    bool done() const { return done_; }
private:
    size_t pos_ = 0;
    bool done_ = false;
};

class HttpSession {
public:
    ~HttpSession() { close(); }

    // Claims the [which]-th IPP-over-USB interface (0 is the first one found).
    bool open(int fd, int which) {
        libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
        int r = libusb_init(nullptr);
        if (r != 0) return fail(std::string("libusb_init: ") + libusb_error_name(r));
        initialised_ = true;
        r = libusb_wrap_sys_device(nullptr, (intptr_t)fd, &h_);
        if (r != 0) return fail(std::string("libusb_wrap_sys_device: ") + libusb_error_name(r));
        std::vector<Endpoints> found = findIppInterfaces(libusb_get_device(h_));
        if (which < 0 || which >= (int)found.size()) return fail("no IPP-over-USB interface number " + std::to_string(which));
        ep_ = found[which];
        if (libusb_claim_interface(h_, ep_.interfaceNumber) != 0) return fail("could not claim the interface");
        claimed_ = true;
        if (libusb_set_interface_alt_setting(h_, ep_.interfaceNumber, ep_.altSetting) != 0) return fail("could not select the IPP alternate setting");
        return true;
    }

    void close() {
        if (claimed_) libusb_release_interface(h_, ep_.interfaceNumber);
        claimed_ = false;
        if (h_) libusb_close(h_);
        h_ = nullptr;
        if (initialised_) libusb_exit(nullptr);
        initialised_ = false;
    }

    // Sends one request and reads the whole response into status / headers / body. False on a transport failure.
    bool request(const std::string &method, const std::string &path, const std::string &contentType,
                 const uint8_t *body, size_t bodyLen, unsigned timeoutMs) {
        status = 0; headers.clear(); responseBody.clear(); err.clear();
        std::string head = method + " " + path + " HTTP/1.1\r\nHost: localhost\r\nAccept: */*\r\n";
        if (!contentType.empty()) head += "Content-Type: " + contentType + "\r\n";
        if (bodyLen > 0 || method == "POST" || method == "PUT") head += "Content-Length: " + std::to_string(bodyLen) + "\r\n";
        head += "\r\n";
        if (!writeAll(h_, ep_.out, reinterpret_cast<const uint8_t *>(head.data()), head.size(), err)) return false;
        if (bodyLen > 0 && !writeAll(h_, ep_.out, body, bodyLen, err)) return false;
        return readResponse(timeoutMs);
    }

    int status = 0;
    std::string headers;
    Bytes responseBody;
    std::string err;

private:
    bool fail(const std::string &m) { err += m + "\n"; LOGI("%s", m.c_str()); return false; }

    bool readResponse(unsigned timeoutMs) {
        Bytes buf;
        size_t headerEnd = 0;                                       // index just past the blank line
        bool haveHeaders = false;
        bool chunked = false;
        long long contentLength = -1;
        ChunkedDecoder decoder;
        uint8_t chunk[kReadSize];

        while (true) {
            // Done already?
            if (haveHeaders) {
                if (status == 204 || status == 304 || (status >= 100 && status < 200 && status != 100)) return true;
                if (chunked) { if (decoder.feed(buf, headerEnd)) { responseBody = std::move(decoder.out); return responseBody.size() <= kMaxBody || fail("response too large"); } }
                else if (contentLength >= 0 && (long long)(buf.size() - headerEnd) >= contentLength) {
                    responseBody.assign(buf.begin() + headerEnd, buf.begin() + headerEnd + contentLength);
                    return true;
                } else if (contentLength < 0 && !chunked) return true;   // no body announced
            }
            int got = 0;
            int r = libusb_bulk_transfer(h_, ep_.in, chunk, kReadSize, &got, timeoutMs);
            // A timeout that still delivered some bytes is kept; anything else is a failure.
            if (r != 0 && !(r == LIBUSB_ERROR_TIMEOUT && got > 0)) return fail(std::string("read: ") + libusb_error_name(r));
            buf.insert(buf.end(), chunk, chunk + got);
            if (buf.size() > kMaxBody + 65536) return fail("response too large");

            if (!haveHeaders) {
                const char sep[] = "\r\n\r\n";
                auto it = std::search(buf.begin(), buf.end(), sep, sep + 4);
                if (it == buf.end()) continue;
                headerEnd = size_t(it - buf.begin()) + 4;
                headers.assign(buf.begin(), buf.begin() + headerEnd - 4);
                size_t sp = headers.find(' ');
                status = sp == std::string::npos ? 0 : atoi(headers.c_str() + sp + 1);
                if (status == 100) {                                // "100 Continue": drop it and wait for the real reply
                    buf.erase(buf.begin(), buf.begin() + headerEnd);
                    headers.clear();
                    continue;
                }
                std::string low = lower(headers);
                chunked = low.find("transfer-encoding: chunked") != std::string::npos;
                size_t cl = low.find("content-length:");
                if (cl != std::string::npos) contentLength = atoll(low.c_str() + cl + 15);
                haveHeaders = true;
            }
        }
    }

    libusb_device_handle *h_ = nullptr;
    Endpoints ep_;
    bool initialised_ = false, claimed_ = false;
};

HttpSession *S(jlong h) { return reinterpret_cast<HttpSession *>(h); }

}  // namespace

#define JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_crescentric_freeprint_otg_HttpSession_##name

JNI(jlong, nativeOpen)(JNIEnv *, jobject, jint fd, jint which) {
    auto *s = new HttpSession();
    bool ok = s->open(fd, which);
    if (!ok) LOGI("HttpSession open failed: %s", s->err.c_str());
    return reinterpret_cast<jlong>(s);
}

JNI(jstring, nativeError)(JNIEnv *env, jobject, jlong h) { return env->NewStringUTF(S(h)->err.c_str()); }

JNI(jboolean, nativeRequest)(JNIEnv *env, jobject, jlong h, jstring method, jstring path, jstring contentType,
                             jbyteArray body, jint timeoutMs) {
    auto str = [&](jstring j) {
        const char *c = env->GetStringUTFChars(j, nullptr);
        std::string s(c);
        env->ReleaseStringUTFChars(j, c);
        return s;
    };
    Bytes payload;
    if (body != nullptr) {
        jsize n = env->GetArrayLength(body);
        payload.resize(n);
        env->GetByteArrayRegion(body, 0, n, reinterpret_cast<jbyte *>(payload.data()));
    }
    HttpSession *s = S(h);
    bool ok = s->request(str(method), str(path), contentType ? str(contentType) : "", payload.data(), payload.size(), (unsigned)timeoutMs);
    LOGI("HTTP %s %s -> %d (%zu body bytes)%s%s", str(method).c_str(), str(path).c_str(), s->status, s->responseBody.size(),
         ok ? "" : " FAILED: ", ok ? "" : s->err.c_str());
    return ok;
}

JNI(jint, nativeStatus)(JNIEnv *, jobject, jlong h) { return S(h)->status; }
JNI(jstring, nativeHeaders)(JNIEnv *env, jobject, jlong h) { return env->NewStringUTF(S(h)->headers.c_str()); }
JNI(jbyteArray, nativeBody)(JNIEnv *env, jobject, jlong h) {
    const Bytes &b = S(h)->responseBody;
    jbyteArray out = env->NewByteArray((jsize)b.size());
    env->SetByteArrayRegion(out, 0, (jsize)b.size(), reinterpret_cast<const jbyte *>(b.data()));
    return out;
}
JNI(void, nativeClose)(JNIEnv *, jobject, jlong h) { delete S(h); }
