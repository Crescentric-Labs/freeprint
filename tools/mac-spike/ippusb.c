// Spike: IPP-over-USB. Select alt setting with class 7/1/4, send HTTP+IPP Get-Printer-Attributes, print reply.
#include <libusb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int put_attr(unsigned char *p, int tag, const char *name, const char *val) {
    int n = 0, nl = (int)strlen(name), vl = (int)strlen(val);
    p[n++] = (unsigned char)tag;
    p[n++] = nl >> 8; p[n++] = nl & 255; memcpy(p + n, name, nl); n += nl;
    p[n++] = vl >> 8; p[n++] = vl & 255; memcpy(p + n, val, vl); n += vl;
    return n;
}

int main(int argc, char **argv) {
    int ifn = argc > 1 ? atoi(argv[1]) : 2;
    libusb_context *ctx; libusb_init(&ctx);
    libusb_device_handle *h = libusb_open_device_with_vid_pid(ctx, 0x04a9, 0x18bd);
    if (!h) { puts("open failed"); return 1; }
    struct libusb_config_descriptor *cfg;
    libusb_get_config_descriptor(libusb_get_device(h), 0, &cfg);
    int alt = -1; uint8_t out = 0, in = 0;
    const struct libusb_interface *itf = &cfg->interface[ifn];
    for (int a = 0; a < itf->num_altsetting; a++) {
        const struct libusb_interface_descriptor *d = &itf->altsetting[a];
        if (d->bInterfaceClass == 7 && d->bInterfaceSubClass == 1 && d->bInterfaceProtocol == 4) {
            alt = d->bAlternateSetting;
            for (int e = 0; e < d->bNumEndpoints; e++) {
                uint8_t ad = d->endpoint[e].bEndpointAddress;
                if (ad & 0x80) in = ad; else out = ad;
            }
        }
    }
    if (alt < 0) { puts("no IPP alt setting"); return 1; }
    printf("if %d alt %d out 0x%02x in 0x%02x\n", ifn, alt, out, in);
    int r = libusb_claim_interface(h, ifn);
    printf("claim: %s\n", r ? libusb_error_name(r) : "ok");
    if (r) return 1;
    r = libusb_set_interface_alt_setting(h, ifn, alt);
    printf("set alt: %s\n", r ? libusb_error_name(r) : "ok");

    unsigned char ipp[512]; int n = 0;
    ipp[n++] = 2; ipp[n++] = 0;             // IPP 2.0
    ipp[n++] = 0; ipp[n++] = 0x0b;          // Get-Printer-Attributes
    ipp[n++] = 0; ipp[n++] = 0; ipp[n++] = 0; ipp[n++] = 1; // request id
    ipp[n++] = 0x01;                        // operation attributes
    n += put_attr(ipp + n, 0x47, "attributes-charset", "utf-8");
    n += put_attr(ipp + n, 0x48, "attributes-natural-language", "en");
    n += put_attr(ipp + n, 0x45, "printer-uri", "ipp://localhost/ipp/print");
    n += put_attr(ipp + n, 0x44, "requested-attributes", "printer-make-and-model");
    ipp[n++] = 0x03;                        // end of attributes

    char hdr[256];
    int hl = snprintf(hdr, sizeof hdr,
        "POST /ipp/print HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/ipp\r\n"
        "Content-Length: %d\r\nConnection: close\r\n\r\n", n);
    unsigned char *req = malloc(hl + n); memcpy(req, hdr, hl); memcpy(req + hl, ipp, n);
    int sent = 0;
    r = libusb_bulk_transfer(h, out, req, hl + n, &sent, 5000);
    printf("write: %s (%d bytes)\n", r ? libusb_error_name(r) : "ok", sent);

    unsigned char buf[8192]; int total = 0, got = 0;
    for (int i = 0; i < 20; i++) {
        r = libusb_bulk_transfer(h, in, buf + total, (int)sizeof buf - total, &got, 3000);
        if (r) { printf("read: %s\n", libusb_error_name(r)); break; }
        total += got;
        if (got == 0) break;
    }
    printf("received %d bytes\n----\n", total);
    for (int i = 0; i < total; i++) putchar(buf[i] >= 32 && buf[i] < 127 || buf[i] == '\n' ? buf[i] : (buf[i] == '\r' ? ' ' : '.'));
    putchar('\n');
    libusb_release_interface(h, ifn);
    libusb_close(h); libusb_exit(ctx);
    return 0;
}
