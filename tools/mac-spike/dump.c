// Read-only USB descriptor dump + IEEE 1284 device ID for a given VID:PID.
#include <libusb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void str(libusb_device_handle *h, uint8_t idx, char *out, size_t n) {
    out[0] = 0;
    if (h && idx) libusb_get_string_descriptor_ascii(h, idx, (unsigned char *)out, (int)n);
}

int main(int argc, char **argv) {
    uint16_t vid = argc > 1 ? (uint16_t)strtol(argv[1], 0, 16) : 0x04a9;
    uint16_t pid = argc > 2 ? (uint16_t)strtol(argv[2], 0, 16) : 0x18bd;
    libusb_context *ctx;
    if (libusb_init(&ctx)) return 1;
    libusb_device **list;
    ssize_t cnt = libusb_get_device_list(ctx, &list);
    for (ssize_t i = 0; i < cnt; i++) {
        struct libusb_device_descriptor dd;
        libusb_get_device_descriptor(list[i], &dd);
        if (dd.idVendor != vid || dd.idProduct != pid) continue;
        libusb_device_handle *h = NULL;
        int rc = libusb_open(list[i], &h);
        printf("open: %s\n", rc ? libusb_error_name(rc) : "ok");
        char s[256];
        str(h, dd.iManufacturer, s, sizeof s); printf("manufacturer: %s\n", s);
        str(h, dd.iProduct, s, sizeof s); printf("product: %s\n", s);
        printf("bcdUSB %04x class %d configs %d\n", dd.bcdUSB, dd.bDeviceClass, dd.bNumConfigurations);
        for (int c = 0; c < dd.bNumConfigurations; c++) {
            struct libusb_config_descriptor *cfg;
            if (libusb_get_config_descriptor(list[i], c, &cfg)) continue;
            printf("config %d value %d interfaces %d\n", c, cfg->bConfigurationValue, cfg->bNumInterfaces);
            for (int k = 0; k < cfg->bNumInterfaces; k++) {
                const struct libusb_interface *itf = &cfg->interface[k];
                for (int a = 0; a < itf->num_altsetting; a++) {
                    const struct libusb_interface_descriptor *d = &itf->altsetting[a];
                    str(h, d->iInterface, s, sizeof s);
                    printf("  IF %d alt %d class %d sub %d proto %d eps %d name \"%s\"%s\n",
                           d->bInterfaceNumber, d->bAlternateSetting, d->bInterfaceClass,
                           d->bInterfaceSubClass, d->bInterfaceProtocol, d->bNumEndpoints, s,
                           (d->bInterfaceClass == 7 && d->bInterfaceSubClass == 1) ? "  <-- printer class" : "");
                    for (int e = 0; e < d->bNumEndpoints; e++) {
                        const struct libusb_endpoint_descriptor *ep = &d->endpoint[e];
                        const char *t[] = {"control", "isoc", "bulk", "interrupt"};
                        printf("      EP 0x%02x %s %s maxpkt %d\n", ep->bEndpointAddress,
                               (ep->bEndpointAddress & 0x80) ? "IN " : "OUT",
                               t[ep->bmAttributes & 3], ep->wMaxPacketSize);
                    }
                    if (d->extra_length) printf("      extra descriptors: %d bytes\n", d->extra_length);
                }
            }
            libusb_free_config_descriptor(cfg);
        }
        if (h) {
            // IEEE 1284 GET_DEVICE_ID, per printer-class interface/alt setting.
            for (int ifn = 0; ifn < 6; ifn++) {
                unsigned char buf[1024] = {0};
                int r = libusb_control_transfer(h, 0xA1, 0, 0, (uint16_t)(ifn << 8), buf, sizeof buf, 2000);
                if (r > 2) printf("GET_DEVICE_ID if%d: %.*s\n", ifn, r - 2, buf + 2);
                else printf("GET_DEVICE_ID if%d: %s\n", ifn, r < 0 ? libusb_error_name(r) : "empty");
            }
            libusb_close(h);
        }
    }
    libusb_free_device_list(list, 1);
    libusb_exit(ctx);
    return 0;
}
