# How the Samsung ML-2160 support was written (clean-room record)

Free Print is GPL-3.0-only. The Linux driver "splix", which also supports this printer, is GPL-2.0-only, so none of its code
can be used here. The support for the Samsung ML-2160 (`print/SamsungQpdl.kt`) was therefore written in two separate steps:

1. **Format description** ([printer-formats/samsung-qpdl.md](printer-formats/samsung-qpdl.md)). Written by one party who had read
   the splix source. It states only facts about the printer's input format (byte layouts, field values, what the printer does
   when it decodes), in the author's own words, with no program code. Items it could not confirm are marked "(verify)".
2. **Implementation.** Written by a separate agent session that was given only that description (plus the Free Print repository
   for coding conventions) and was forbidden to read, fetch or recall any existing driver for the format. Its own report lists
   the files it read; it states it consulted nothing else. Its compressor, encoder structure and tests are its own design.
3. **Checking.** The encoder's output is checked by its own unit tests (a decoder written from the description) and, in beta,
   against the real printer. Where the real printer disagrees with the description, the description is corrected first and
   the code follows from it.

What cannot be proven: the implementing agent is the same kind of model as the one that wrote the description, so it cannot be
shown to have no memory of existing drivers. Anyone who doubts the result should review `SamsungQpdl.kt` against splix.

Reverse engineering for interoperability (making a device work with software it was not made for) is widely allowed, but the law
differs by country; this note is a record of the method, not legal advice.
