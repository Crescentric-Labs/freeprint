cmake_minimum_required(VERSION 3.22.1)
project(freeprint_native C CXX)

set(LIBUSB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb)

add_library(usb STATIC
    ${LIBUSB_DIR}/libusb/core.c
    ${LIBUSB_DIR}/libusb/descriptor.c
    ${LIBUSB_DIR}/libusb/hotplug.c
    ${LIBUSB_DIR}/libusb/io.c
    ${LIBUSB_DIR}/libusb/strerror.c
    ${LIBUSB_DIR}/libusb/sync.c
    ${LIBUSB_DIR}/libusb/os/events_posix.c
    ${LIBUSB_DIR}/libusb/os/linux_netlink.c
    ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
    ${LIBUSB_DIR}/libusb/os/threads_posix.c)
target_include_directories(usb PUBLIC ${LIBUSB_DIR}/libusb PRIVATE ${LIBUSB_DIR}/android)
target_compile_options(usb PRIVATE -fvisibility=hidden -pthread -Wno-deprecated-declarations)

add_library(freeprint_native SHARED ippusb.cpp print_session.cpp http_session.cpp)
target_link_libraries(freeprint_native usb log)
