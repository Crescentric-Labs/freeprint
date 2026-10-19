# Changelog

All notable changes to Free Print are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the version numbers follow
[Semantic Versioning](https://semver.org/). `versionCode` goes up by one with every release.

The app shows this file under **About → Changelog**, so keep entries short, in plain words, one line each, and written for
the people who use the app. Put new work under `## [Unreleased]` and move it into a numbered release when you ship.

## [Unreleased]

## [2.0.2] - 2026-10-20

### Changed
- A text file shared to Free Print (a `.txt`, `.md`, `.csv` and so on) now opens as a PDF named after the file, such as "Garden-meeting-notes", instead of "Shared text".
- The project now lives in the Crescentric-Labs organisation on GitHub, and the source link on the About screen points there.

## [2.0.1] - 2026-10-19

### Added
- Share to Free Print: PDFs, pictures and plain text from any app's share sheet open straight on the Print tab.
- Open with Free Print: PDFs, pictures and text files from a file manager, a PDF viewer, a gallery or a download.
- Shared text (a note, a message, an address, a link) and text files are turned into a printable A4 PDF, with the subject as a heading.
- A new launcher icon: the printer on a plain deep-blue field, with no card or outline of its own.
- The printer artwork in the top bar and on the About page now sits on its own, without a background tile.
- A changelog inside the app: **About → Changelog** unfolds every release, with the version as a pill and its date on the right.

### Changed
- The **Load sample PDF** button is gone from Diagnostics, together with the bundled sample files.

### Fixed
- The app crashed at start-up when it was opened from a share or "open with". The splash screen's exit animation assumed an icon that Android does not always provide.
- The splash screen no longer cuts off the top of the paper or the corners of the printer.
- A PDF opened by its file address (such as a scan sent to "Print a copy") was read as a picture; the file type now comes from the extension when Android gives none.

## [2.0.0] - 2026-10-18

### Added
- A new home screen: a floating dock switches between **Print** and **Scan**, each showing its own cards. Swipe sideways to change tab.
- A back arrow in front of the app icon puts the app back to how it started once a document or scanned pages are open.
- **Copies**: print up to 99 collated copies, also with two-sided printing (odd page counts get a blank back so later copies stay in step).
- **Pause** a running job between sheets, and resume it.
- **Optimize for print**: whitens paper, evens out shadows and uneven light, and deepens ink. Works per page, on pictures, PDF pages and scanned pages, and can be undone.
- Drag files by their handle to change the print order.
- The bottom bar shows the total number of sides that will print, and the settings in effect as small pills (paper, colour, quality, pages per sheet, orientation, two-sided, page range).
- Settings are tiles in a two-column grid, with a note under each print quality.
- More paper sizes: A6, B5, Executive, 5 x 7 in and 8 x 10 in.
- Page-number pills under the page range, to pick pages with a tap, and a Select all button.
- Restore pages also covers pages dropped from the page selection, not only ones removed from the strip.
- Save a scan as a **PDF** or as **pictures**, either page by page as scanned or laid out on paper sheets with the print settings (paper, orientation, pages per sheet, size and scale, margins, position, colour).
- After a failed front-sides pass of two-sided printing you can go on to print the back sides anyway, since the printer may have printed the fronts.
- A splash screen, and matching light and dark window colours so nothing flashes white between screens.
- A ready scanner is shown with the same "Ready" badge as the printer, and a refresh button replaces the old "Rescan printer" button.
- Start screens for both Print and Scan.

### Changed
- Home, print and scan share one scaffold; the separate scanner page is gone.
- File types are shown as "PDF document" and "JPEG image" instead of technical names.
- The refresh button left the top bar; About moved to the right.
- Previews are drawn ahead for the next and previous sheet, so swiping onto an optimized page no longer waits.

### Fixed
- Long jobs no longer end with "The printer rejected the job" after printing fine: the app now waits up to five minutes for the printer's reply instead of fifteen seconds.
- The bottom bar no longer jumps up and drops into place when a document is chosen.
- The printer card keeps its shape when the printer name is long, and no longer shows a box behind it.

## [1.0.0] - 2026-10-18

### Added
- An **About** screen with the version, the GPL notice, the libusb credit, the full licence texts and a Diagnostics button.
- Two-sided page strip actions: restore deleted pages, with the strip scrolling only when you move to another page.
- A light theme for the full-screen viewer, with an accent colour that stays readable.

### Changed
- The application ID and package are now `crescentric.freeprint.otg`.

## [0.1.0] - 2026-10-11

First working version. Its application ID was `crystaldigits.freeprint.otg`; from 1.0.0 it is `crescentric.freeprint.otg`, so this build and later ones install side by side rather than as updates of each other.

### Added
- Driverless printing to a USB printer: IPP over USB with PWG raster, through a bundled libusb. Tested on a Canon PIXMA G2020 with a Samsung Galaxy S25 Ultra.
- Print PDFs and pictures, several at once and mixed, with a live preview, a thumbnail strip, swiping between sheets and a zoomable full-screen viewer.
- Choose pages (ranges such as `1-3, 5, 8-10`, odd or even), 1 to 8 pages per sheet, fit, fill or actual size with scaling, margins, position and orientation.
- Paper sizes A4, Letter, A5, Legal and 4 x 6 in; draft, normal and high quality; colour or grayscale.
- Manual two-sided printing for printers that cannot do it themselves: long or short edge, reverse order for the backs, and an animated guide for turning the stack.
- Delete pages from the strip and bring them back.
- A system print service, so any app can print to the printer from Android's print dialog.
- Scanning over eSCL through the same USB link: scan several pages, save a PDF, share it, or print a copy.
- English and Sinhala, a language switch inside the app and Android's per-app language setting.
- The printer being plugged in or out is noticed straight away.
- Released under the GNU General Public License v3.0.
