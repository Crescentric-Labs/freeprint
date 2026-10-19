cmake_minimum_required(VERSION 3.22.1)
project(freeprint_native C CXX)

# Reproducible builds: no build folder ends up in the debug info, wherever the project is built (set before any target is created)
add_compile_options(-ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=.)

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

# The GNU build ID is a hash computed over the debug info before the library is stripped, so it differed with the build folder;
# leave it out (Android does not need it) and the same source gives the same library anywhere.
target_link_options(freeprint_native PRIVATE -Wl,--build-id=none)
