#!/usr/bin/env python3
"""Generates app/src/main/res/values/strings.xml (English) and values-si/strings.xml (Sinhala) from one table,
so the two files always contain the same keys. Run from the repo root: python3 -I tools/gen_strings.py"""
import os
from xml.sax.saxutils import escape

# key: (English, Sinhala). Format arguments are positional (%1$s, %2$d). A literal percent is %%.
S = {
 "app_name": ("Free Print", "Free Print"),
 "app_subtitle": ("USB Mobile Direct Print", "USB මගින් සෘජුවම මුද්‍රණය"),
 "cd_app_icon": ("App icon", "යෙදුමේ නිරූපකය"),
 "cd_rescan": ("Rescan printer", "මුද්‍රණ යන්ත්‍රය නැවත සොයන්න"),
 "cd_diagnostics": ("Diagnostics", "රෝග විනිශ්චය"),
 "cd_language": ("Language", "භාෂාව"),
 "cd_refresh": ("Refresh", "නැවුම් කරන්න"),
 "cd_printer_hero": ("Printer illustration", "මුද්‍රණ යන්ත්‍ර රූපය"),
 "cd_clear_file": ("Clear file", "ගොනුව ඉවත් කරන්න"),
 "cd_sheet_preview": ("Live sheet preview", "පත්‍ර පෙරදසුන"),
 "cd_fullscreen": ("Full screen", "සම්පූර්ණ තිරය"),
 "cd_prev_sheet": ("Previous sheet", "කලින් පත්‍රය"),
 "cd_next_sheet": ("Next sheet", "ඊළඟ පත්‍රය"),
 "cd_close": ("Close", "වසන්න"),

 "printer_none_title": ("No USB printer connected", "USB මුද්‍රණ යන්ත්‍රයක් සම්බන්ධ කර නැත"),
 "printer_none_hint": ("Plug in your USB OTG adapter and turn on the printer.", "ඔබේ USB OTG ඇඩැප්ටරය සවි කර මුද්‍රණ යන්ත්‍රය ක්‍රියාත්මක කරන්න."),
 "scan": ("Scan", "සොයන්න"),
 "printer_fallback": ("USB printer", "USB මුද්‍රණ යන්ත්‍රය"),
 "printer_permission_needed": ("USB permission needed to communicate with printer.", "මුද්‍රණ යන්ත්‍රය සමඟ සන්නිවේදනය කිරීමට USB අවසරය අවශ්‍යයි."),
 "grant_permission": ("Allow USB access", "USB ප්‍රවේශයට ඉඩ දෙන්න"),
 "ready": ("Ready", "සූදානම්"),
 "printer_mode": ("USB OTG • Driverless IPP (600 DPI)", "USB OTG • ධාවකයක් නොමැති IPP (600 DPI)"),

 "hero_title": ("Ready to print", "මුද්‍රණයට සූදානම්"),
 "hero_body": ("Select any PDF or image to configure options and print directly to your USB printer.", "ඕනෑම PDF හෝ රූපයක් තෝරා විකල්ප සකසා ඔබේ USB මුද්‍රණ යන්ත්‍රයෙන් සෘජුවම මුද්‍රණය කරන්න."),
 "select_document": ("Select documents or images", "ලේඛන හෝ රූප තෝරන්න"),
 "add_files": ("Add files", "ගොනු එක් කරන්න"),
 "cd_move_up": ("Move up", "ඉහළට ගෙන යන්න"),
 "cd_move_down": ("Move down", "පහළට ගෙන යන්න"),
 "files_order_hint": ("Files print in the order shown. PDFs and images can be mixed.", "ගොනු පෙන්වා ඇති අනුපිළිවෙලට මුද්‍රණය වේ. PDF සහ රූප මිශ්‍ර කළ හැක."),
 "load_sample": ("Load sample multi-page PDF", "බහු-පිටු නියැදි PDF පූරණය කරන්න"),

 "doc_meta": ("%1$s • %2$s", "%1$s • %2$s"),
 "doc_fallback": ("Document", "ලේඛනය"),
 "change": ("Change", "වෙනස් කරන්න"),
 "preview_title": ("Live print preview", "මුද්‍රණ පෙරදසුන"),
 "mode_colour": ("Colour", "වර්ණ"),
 "mode_grayscale": ("Grayscale", "අළු පැහැති"),
 "sheet_x_of_y": ("Sheet %1$d of %2$d", "පත්‍රය %1$d / %2$d"),
 "preview_hint": ("Tap the sheet to enlarge. Swipe to change sheet.", "විශාල කිරීමට පත්‍රය තට්ටු කරන්න. පත්‍රය මාරු කිරීමට ස්වයිප් කරන්න."),
 "selection_summary": ("%1$d of %2$d pages selected • %3$s will be printed", "පිටු %2$d න් %1$d ක් තෝරා ඇත • %3$s මුද්‍රණය වේ"),

 "print_settings": ("Print settings", "මුද්‍රණ සැකසුම්"),
 "setting_paper": ("Paper size", "කඩදාසි ප්‍රමාණය"),
 "setting_colour": ("Colour", "වර්ණය"),
 "setting_quality": ("Print quality", "මුද්‍රණ ගුණාත්මකභාවය"),
 "setting_orientation": ("Orientation", "දිශානතිය"),
 "setting_pages": ("Pages to print", "මුද්‍රණය කළ යුතු පිටු"),
 "setting_per_sheet": ("Pages per sheet", "පත්‍රයකට පිටු ගණන"),
 "setting_sizing": ("Page size and scale", "පිටු ප්‍රමාණය සහ පරිමාණය"),
 "setting_margins": ("Margins", "මායිම්"),
 "setting_position": ("Position", "පිහිටීම"),

 "sub_check_range": ("Check the page range", "පිටු පරාසය පරීක්ෂා කරන්න"),
 "sub_all_pages_n": ("All pages (%1$d)", "සියලු පිටු (%1$d)"),
 "sub_all_pages": ("All pages", "සියලු පිටු"),
 "sub_pages_range": ("%1$s (%2$d of %3$d)", "%1$s (%3$d න් %2$d)"),
 "sub_pages_range_parity": ("%1$s, %2$s (%3$d of %4$d)", "%1$s, %2$s (%4$d න් %3$d)"),
 "parity_only_odd": ("odd pages only", "ඔත්තේ පිටු පමණි"),
 "parity_only_even": ("even pages only", "ඉරට්ටේ පිටු පමණි"),
 "sub_per_sheet_one": ("1 per sheet", "පත්‍රයකට 1"),
 "sub_per_sheet_n": ("%1$d per sheet, %2$s", "පත්‍රයකට %1$d, %2$s"),
 "sub_with_borders": ("with borders", "මායිම් සහිතව"),
 "sub_sizing": ("%1$s, %2$d%%", "%1$s, %2$d%%"),
 "sub_pos_centre": ("Centre", "මැද"),
 "sub_across": ("%1$d mm across", "තිරස්ව මි.මී. %1$d"),
 "sub_down": ("%1$d mm down", "සිරස්ව මි.මී. %1$d"),

 "dialog_done": ("Done", "අවසන්"),
 "colour_opt_colour": ("Colour", "වර්ණ"),
 "colour_opt_gray": ("Grayscale (black and white)", "අළු පැහැති (කළු සුදු)"),
 "orientation_hint": ("Auto follows the first page. Landscape turns the content a quarter turn counter-clockwise.", "ස්වයංක්‍රීය මුල් පිටුව අනුගමනය කරයි. තිරස් මාදිලියේදී අන්තර්ගතය වාමාවර්තව අංශක 90කින් කරකැවේ."),
 "per_sheet_one_opt": ("1 page per sheet", "පත්‍රයකට පිටුව 1"),
 "per_sheet_n_opt": ("%1$d pages per sheet", "පත්‍රයකට පිටු %1$d"),
 "page_order": ("Page order", "පිටු අනුපිළිවෙල"),
 "border_each": ("Border around each page", "සෑම පිටුවක් වටා මායිමක්"),
 "pages_which": ("Which pages", "කුමන පිටු"),
 "page_range_label": ("Page range (blank = all)", "පිටු පරාසය (හිස් නම් සියල්ල)"),
 "page_range_placeholder": ("e.g. 1-3, 5, 8-", "උදා: 1-3, 5, 8-"),
 "range_selected": ("%1$d of %2$d pages selected", "පිටු %2$d න් %1$d ක් තෝරා ඇත"),
 "scale_label": ("Scale", "පරිමාණය"),
 "pos_anchor": ("Anchor", "ස්ථාන ලක්ෂ්‍යය"),
 "pos_across": ("Across: %1$d mm", "තිරස්: මි.මී. %1$d"),
 "pos_down": ("Down: %1$d mm", "සිරස්: මි.මී. %1$d"),
 "pos_hint": ("The anchor sets where the page sits in the spare room; the sliders then nudge it.", "ස්ථාන ලක්ෂ්‍යය මගින් පිටුව ඉතිරි ඉඩෙහි පිහිටන තැන තීරණය කරයි; ස්ලයිඩර මගින් එය තව දුරටත් මාරු කළ හැක."),
 "margin_auto": ("Automatic", "ස්වයංක්‍රීය"),
 "margin_none": ("None", "නැත"),
 "margin_mm": ("%1$d mm", "මි.මී. %1$d"),
 "margin_custom": ("Margin: %1$d mm", "මායිම: මි.මී. %1$d"),
 "margin_hint": ("Empty space kept around the pages. Automatic means none for one page per sheet and 6 mm when several share a sheet. The printer cannot print right to the paper edge, so very small margins may be clipped.", "පිටු වටා හිස්ව තබන ඉඩ. ස්වයංක්‍රීය යනු පත්‍රයකට එක් පිටුවක් සඳහා නැත, පිටු කිහිපයක් එකට ඇති විට මි.මී. 6 කි. මුද්‍රණ යන්ත්‍රයට කඩදාසියේ කෙළවරට ඉතා ආසන්නව මුද්‍රණය කළ නොහැකි බැවින් ඉතා කුඩා මායිම් කැපී යා හැක."),
 "pos_reset": ("Reset to centre", "මැදට යළි සකසන්න"),

 "printing_preparing": ("Preparing print job…", "මුද්‍රණ කාර්යය සූදානම් කරමින්…"),
 "printing_sheet": ("Sending sheet %1$d of %2$d…", "පත්‍රය %1$d / %2$d යවමින්…"),
 "bar_paper_mode": ("%1$s • %2$s", "%1$s • %2$s"),
 "print_button": ("Print", "මුද්‍රණය"),
 "printing_button": ("Printing…", "මුද්‍රණය වෙමින්…"),
 "job_done": ("Sent to the printer.", "මුද්‍රණ යන්ත්‍රයට යවන ලදී."),
 "job_failed": ("Printing failed", "මුද්‍රණය අසාර්ථකයි"),
 "job_cancelled": ("Cancelled.", "අවලංගු කරන ලදී."),
 "err_open_file": ("Could not open the file", "ගොනුව විවෘත කළ නොහැකි විය"),

 "diag_title": ("Printer diagnostics and tools", "මුද්‍රණ යන්ත්‍ර රෝග විනිශ්චය සහ මෙවලම්"),
 "diag_desc": ("Hardware utilities for inspecting USB endpoints, IPP query responses, and raw test prints.", "USB අන්ත ලක්ෂ්‍ය, IPP ප්‍රතිචාර සහ පරීක්ෂණ මුද්‍රණ සඳහා දෘඩාංග උපයෝගිතා."),
 "diag_usb": ("USB interfaces", "USB අතුරුමුහුණත්"),
 "diag_ipp": ("IPP attributes", "IPP ගුණාංග"),
 "diag_test": ("Print test page", "පරීක්ෂණ පිටුව මුද්‍රණය"),
 "diag_sample": ("Load sample PDF", "නියැදි PDF පූරණය"),
 "diag_clear": ("Clear", "මකන්න"),
 "diag_prompt": ("Select an action above to query your connected USB printer.", "සම්බන්ධ කර ඇති USB මුද්‍රණ යන්ත්‍රය විමසීමට ඉහත ක්‍රියාවක් තෝරන්න."),
 "viewer_hint": ("Pinch or double-tap to zoom. Swipe to change sheet.", "විශාල කිරීමට පින්ච් කරන්න හෝ දෙවරක් තට්ටු කරන්න. පත්‍රය මාරු කිරීමට ස්වයිප් කරන්න."),

 "language_title": ("Language", "භාෂාව"),
 "language_system": ("System default", "පද්ධති පෙරනිමිය"),
 "language_en": ("English", "English"),
 "language_si": ("සිංහල", "සිංහල"),

 "quality_draft": ("Draft", "කෙටුම්පත"),
 "quality_normal": ("Normal", "සාමාන්‍ය"),
 "quality_high": ("High", "ඉහළ"),
 "fit_fit": ("Fit", "ගැළපෙන සේ"),
 "fit_fill": ("Fill (crop)", "පුරවන්න (කපා)"),
 "fit_actual": ("Actual size", "සැබෑ ප්‍රමාණය"),
 "orient_auto": ("Auto", "ස්වයංක්‍රීය"),
 "orient_portrait": ("Portrait", "සිරස් (Portrait)"),
 "orient_landscape": ("Landscape", "තිරස් (Landscape)"),
 "order_across": ("Across, then down", "තිරස්ව, ඉන්පසු පහළට"),
 "order_down": ("Down, then across", "පහළට, ඉන්පසු තිරස්ව"),
 "parity_all": ("All", "සියල්ල"),
 "parity_odd": ("Odd", "ඔත්තේ"),
 "parity_even": ("Even", "ඉරට්ටේ"),
 "anchor_top_left": ("Top left", "ඉහළ වම"),
 "anchor_top": ("Top", "ඉහළ"),
 "anchor_top_right": ("Top right", "ඉහළ දකුණ"),
 "anchor_left": ("Left", "වම"),
 "anchor_center": ("Centre", "මැද"),
 "anchor_right": ("Right", "දකුණ"),
 "anchor_bottom_left": ("Bottom left", "පහළ වම"),
 "anchor_bottom": ("Bottom", "පහළ"),
 "anchor_bottom_right": ("Bottom right", "පහළ දකුණ"),

 "err_bad_range": ("“%1$s” is not a valid page or range.", "“%1$s” වලංගු පිටුවක් හෝ පරාසයක් නොවේ."),
 "err_starts_at_one": ("Pages start at 1.", "පිටු ආරම්භ වන්නේ 1 සිටය."),
 "err_backwards": ("“%1$s” runs backwards.", "“%1$s” පසුපසට ගමන් කරයි (අගය මුල්ට වඩා කුඩාය)."),
 "err_past_end": ("Page %1$d is past the end (%2$d pages).", "පිටුව %1$d අවසානයට පසුවය (පිටු %2$d)."),
 "err_no_match": ("No pages match.", "ගැළපෙන පිටු නැත."),

 "svc_no_printer": ("The USB printer is not connected.", "USB මුද්‍රණ යන්ත්‍රය සම්බන්ධ කර නැත."),
 "svc_no_access": ("USB access was not allowed. Open Free Print and allow access to the printer.", "USB ප්‍රවේශයට ඉඩ දුන්නේ නැත. Free Print විවෘත කර මුද්‍රණ යන්ත්‍රයට ප්‍රවේශය ලබා දෙන්න."),
 "svc_doc_unreadable": ("The document could not be read.", "ලේඛනය කියවිය නොහැකි විය."),
 "svc_read_failed": ("Could not read the document: %1$s", "ලේඛනය කියවිය නොහැකි විය: %1$s"),
 "svc_failed": ("Printing failed.", "මුද්‍රණය අසාර්ථකයි."),
 "svc_printer_desc": ("USB, driverless (IPP)", "USB, ධාවකයක් නොමැති (IPP)"),
}

# plurals: key: (english one, english other, sinhala one, sinhala other); every string takes the count as %1$d
P = {
 "pages_count": ("%1$d page", "%1$d pages", "පිටු %1$d", "පිටු %1$d"),
 "sheets_count": ("%1$d sheet", "%1$d sheets", "පත්‍ර %1$d", "පත්‍ර %1$d"),
}

def q(text):
    """Escape for an Android string resource."""
    return escape(text).replace("'", "\\'").replace('"', '\\"').replace("…", "…")

def write(path, idx, header):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- " + header + " -->", "<resources>"]
    for k, v in S.items():
        lines.append(f'    <string name="{k}">{q(v[idx])}</string>')
    for k, v in P.items():
        lines.append(f'    <plurals name="{k}">')
        lines.append(f'        <item quantity="one">{q(v[idx * 2])}</item>')
        lines.append(f'        <item quantity="other">{q(v[idx * 2 + 1])}</item>')
        lines.append("    </plurals>")
    lines.append("</resources>")
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")

root = "app/src/main/res"
write(f"{root}/values/strings.xml", 0, "Generated by tools/gen_strings.py: edit the table there, not this file.")
write(f"{root}/values-si/strings.xml", 1, "Generated by tools/gen_strings.py: edit the table there, not this file. Sinhala.")
print(len(S), "strings,", len(P), "plurals")
