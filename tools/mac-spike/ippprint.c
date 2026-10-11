// Spike: submit an IPP Print-Job (image/pwg-raster) over IPP-over-USB.
// usage: ippprint <interface> <file.pwg>
#include <libusb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int u16(unsigned char *p, int v) { p[0] = v >> 8; p[1] = v & 255; return 2; }
static int attr(unsigned char *p, int tag, const char *name, const void *val, int vl) {
    int n = 0, nl = (int)strlen(name);
    p[n++] = (unsigned char)tag; n += u16(p + n, nl); memcpy(p + n, name, nl); n += nl;
    n += u16(p + n, vl); memcpy(p + n, val, vl); n += vl; return n;
}
static int sattr(unsigned char *p, int tag, const char *name, const char *v) { return attr(p, tag, name, v, (int)strlen(v)); }
static int iattr(unsigned char *p, int tag, const char *name, int v) {
    unsigned char b[4] = {v >> 24, v >> 16, v >> 8, v}; return attr(p, tag, name, b, 4);
}

// Bulk write all bytes, in 16 KB pieces.
static int write_all(libusb_device_handle *h, uint8_t ep, const unsigned char *d, int len) {
    int off = 0;
    while (off < len) {
        int chunk = len - off > 16384 ? 16384 : len - off, sent = 0;
        int r = libusb_bulk_transfer(h, ep, (unsigned char *)d + off, chunk, &sent, 10000);
        if (r) { printf("write error: %s at %d\n", libusb_error_name(r), off); return r; }
        off += sent;
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 3) return 2;
    int ifn = atoi(argv[1]);
    FILE *f = fopen(argv[2], "rb"); if (!f) { puts("no file"); return 1; }
    fseek(f, 0, SEEK_END); long flen = ftell(f); rewind(f);
    unsigned char *doc = malloc(flen); fread(doc, 1, flen, f); fclose(f);

    libusb_context *ctx; libusb_init(&ctx);
    libusb_device_handle *h = libusb_open_device_with_vid_pid(ctx, 0x04a9, 0x18bd);
    if (!h) { puts("open failed"); return 1; }
    struct libusb_config_descriptor *cfg;
    libusb_get_config_descriptor(libusb_get_device(h), 0, &cfg);
    int alt = -1; uint8_t out = 0, in = 0;
    const struct libusb_interface *itf = &cfg->interface[ifn];
    for (int a = 0; a < itf->num_altsetting; a++) {
        const struct libusb_interface_descriptor *d = &itf->altsetting[a];
        if (d->bInterfaceClass == 7 && d->bInterfaceProtocol == 4) {
            alt = d->bAlternateSetting;
            for (int e = 0; e < d->bNumEndpoints; e++) {
                uint8_t ad = d->endpoint[e].bEndpointAddress; if (ad & 0x80) in = ad; else out = ad;
            }
        }
    }
    if (alt < 0 || libusb_claim_interface(h, ifn) || libusb_set_interface_alt_setting(h, ifn, alt)) { puts("claim failed"); return 1; }

    unsigned char ipp[1024]; int n = 0;
    ipp[n++] = 2; ipp[n++] = 0; ipp[n++] = 0; ipp[n++] = 2;       // IPP 2.0, Print-Job
    ipp[n++] = 0; ipp[n++] = 0; ipp[n++] = 0; ipp[n++] = 7;       // request id
    ipp[n++] = 0x01;
    n += sattr(ipp + n, 0x47, "attributes-charset", "utf-8");
    n += sattr(ipp + n, 0x48, "attributes-natural-language", "en");
    n += sattr(ipp + n, 0x45, "printer-uri", "ipp://localhost/ipp/print");
    n += sattr(ipp + n, 0x42, "job-name", "freeprint-spike");
    n += sattr(ipp + n, 0x49, "document-format", "image/pwg-raster");
    ipp[n++] = 0x02;                                               // job attributes
    n += sattr(ipp + n, 0x44, "media", "iso_a4_210x297mm");
    n += sattr(ipp + n, 0x44, "print-color-mode", "color");
    n += iattr(ipp + n, 0x23, "print-quality", 4);                 // normal
    n += iattr(ipp + n, 0x21, "copies", 1);
    ipp[n++] = 0x03;

    char hdr[256];
    int hl = snprintf(hdr, sizeof hdr,
        "POST /ipp/print HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/ipp\r\n"
        "Content-Length: %ld\r\n\r\n", n + flen);
    if (write_all(h, out, (unsigned char *)hdr, hl) || write_all(h, out, ipp, n) || write_all(h, out, doc, (int)flen)) return 1;
    printf("sent %d header + %d ipp + %ld document bytes\n", hl, n, flen);

    unsigned char buf[16384]; int total = 0, got = 0, r;
    for (int i = 0; i < 40 && total < (int)sizeof buf; i++) {
        r = libusb_bulk_transfer(h, in, buf + total, (int)sizeof buf - total, &got, 5000);
        if (r) { printf("read: %s\n", libusb_error_name(r)); break; }
        total += got;
        if (total >= 5 && memcmp(buf + total - 5, "0\r\n\r\n", 5) == 0) break;   // end of chunked body
    }
    printf("received %d bytes\n", total);
    char *body = memmem(buf, total, "\r\n\r\n", 4);
    printf("%.*s\n", (int)(body ? body - (char *)buf : total), buf);
    // chunked body: "<hex>\r\n<ipp...>"; IPP status-code is at bytes 2..3 of the IPP payload.
    if (body) {
        unsigned char *c = (unsigned char *)memchr(body + 4, '\n', total - (body + 4 - (char *)buf));
        if (c && c + 5 < buf + total)
            printf("IPP status 0x%02x%02x %s\n", c[3], c[4], (c[3] == 0 && c[4] <= 2) ? "(successful)" : "(ERROR)");
    }
    libusb_release_interface(h, ifn); libusb_close(h); libusb_exit(ctx);
    return 0;
}
