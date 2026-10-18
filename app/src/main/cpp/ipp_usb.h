// Shared helpers for talking IPP over USB bulk endpoints (HTTP framing, IPP encoding,
// interface discovery). Used by the one-shot spike calls and the streaming session.
#pragma once

#include <libusb.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace ippusb {

constexpr int kIppClass = 7;
constexpr int kIppSubClass = 1;
constexpr int kIppProtocol = 4;
constexpr int kUsbChunk = 16384;     // bulk write size; a multiple of the 512-byte max packet

using Bytes = std::vector<uint8_t>;

inline void put16(Bytes &b, int v) { b.push_back(v >> 8); b.push_back(v & 255); }
inline void put32(Bytes &b, int v) { put16(b, v >> 16); put16(b, v & 0xffff); }

inline void attr(Bytes &b, int tag, const char *name, const void *val, int len) {
    b.push_back(tag);
    put16(b, (int)strlen(name));
    b.insert(b.end(), name, name + strlen(name));
    put16(b, len);
    const uint8_t *p = static_cast<const uint8_t *>(val);
    b.insert(b.end(), p, p + len);
}
inline void sattr(Bytes &b, int tag, const char *name, const char *v) { attr(b, tag, name, v, (int)strlen(v)); }
inline void iattr(Bytes &b, int tag, const char *name, int v) {
    uint8_t x[4] = {uint8_t(v >> 24), uint8_t(v >> 16), uint8_t(v >> 8), uint8_t(v)};
    attr(b, tag, name, x, 4);
}

// IPP operation header plus the three mandatory operation attributes.
inline Bytes ippHeader(int opcode, int requestId) {
    Bytes b;
    b.push_back(2); b.push_back(0);        // IPP 2.0
    put16(b, opcode);
    put32(b, requestId);
    b.push_back(0x01);                     // operation-attributes-tag
    sattr(b, 0x47, "attributes-charset", "utf-8");
    sattr(b, 0x48, "attributes-natural-language", "en");
    sattr(b, 0x45, "printer-uri", "ipp://localhost/ipp/print");
    return b;
}

struct Endpoints {
    int interfaceNumber = -1;
    int altSetting = -1;
    uint8_t out = 0;
    uint8_t in = 0;
};

// All IPP-over-USB interface/alt-setting pairs on the device, in descriptor order.
inline std::vector<Endpoints> findIppInterfaces(libusb_device *dev) {
    std::vector<Endpoints> found;
    libusb_config_descriptor *cfg = nullptr;
    if (libusb_get_active_config_descriptor(dev, &cfg) != 0) return found;
    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        const libusb_interface &itf = cfg->interface[i];
        for (int a = 0; a < itf.num_altsetting; a++) {
            const libusb_interface_descriptor &d = itf.altsetting[a];
            if (d.bInterfaceClass != kIppClass || d.bInterfaceSubClass != kIppSubClass ||
                d.bInterfaceProtocol != kIppProtocol)
                continue;
            Endpoints e;
            e.interfaceNumber = d.bInterfaceNumber;
            e.altSetting = d.bAlternateSetting;
            for (int k = 0; k < d.bNumEndpoints; k++) {
                if ((d.endpoint[k].bmAttributes & 3) != LIBUSB_TRANSFER_TYPE_BULK) continue;
                uint8_t addr = d.endpoint[k].bEndpointAddress;
                if (addr & 0x80) e.in = addr; else e.out = addr;
            }
            if (e.in && e.out) found.push_back(e);
        }
    }
    libusb_free_config_descriptor(cfg);
    return found;
}

// Bulk-writes all bytes in kUsbChunk pieces. On failure appends the reason to `err`.
inline bool writeAll(libusb_device_handle *h, uint8_t ep, const uint8_t *d, size_t len,
                     std::string &err, unsigned timeoutMs = 60000) {
    size_t off = 0;
    while (off < len) {
        int chunk = (int)std::min<size_t>(kUsbChunk, len - off), sent = 0;
        int r = libusb_bulk_transfer(h, ep, const_cast<uint8_t *>(d) + off, chunk, &sent, timeoutMs);
        if (r != 0) {
            err += std::string("write error: ") + libusb_error_name(r) + " at offset " + std::to_string(off) + "\n";
            return false;
        }
        off += sent;
    }
    return true;
}

// Reads until the chunked-transfer terminator "0\r\n\r\n". The printer keeps the connection
// open after replying, so waiting for a timeout would be wrong. A printer that is still working on a long job
// may take a while to answer, so quiet periods are tolerated until `waitMs` has passed in total.
inline Bytes readResponse(libusb_device_handle *h, uint8_t ep, std::string &err, unsigned waitMs = 15000) {
    Bytes resp(32768);
    size_t total = 0;
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(waitMs);
    while (total < resp.size()) {
        int got = 0;
        int r = libusb_bulk_transfer(h, ep, resp.data() + total, (int)(resp.size() - total), &got, 1000);
        total += got;
        if (r == LIBUSB_ERROR_TIMEOUT) {
            if (std::chrono::steady_clock::now() < deadline) continue;   // still printing; keep waiting
        }
        if (r != 0 && !(r == LIBUSB_ERROR_TIMEOUT && got > 0)) {
            err += std::string("read: ") + libusb_error_name(r) + "\n";
            break;
        }
        if (total >= 5 && memcmp(resp.data() + total - 5, "0\r\n\r\n", 5) == 0) break;
    }
    resp.resize(total);
    return resp;
}

// Printable rendering of a response, non-printable bytes shown as '.'.
inline std::string render(const Bytes &resp) {
    std::string s;
    for (uint8_t c : resp) s += (c >= 32 && c < 127) || c == '\n' ? (char)c : (c == '\r' ? ' ' : '.');
    return s;
}

// IPP status code from a chunked HTTP response, or -1 if it can't be located.
inline int ippStatus(const Bytes &resp) {
    const char sep[] = "\r\n\r\n";
    auto it = std::search(resp.begin(), resp.end(), sep, sep + 4);
    if (it == resp.end()) return -1;
    auto lf = std::find(it + 4, resp.end(), '\n');          // end of the chunk-size line
    if (lf == resp.end() || resp.end() - lf < 5) return -1;
    return (lf[3] << 8) | lf[4];
}

}  // namespace ippusb
