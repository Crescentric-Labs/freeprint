# Security policy

## What the app can and cannot do

- Free Print declares **no Android permissions**. It cannot reach the internet, read your storage or see your contacts.
- It talks to exactly one kind of peripheral: a USB printer that you plug in and then explicitly allow in Android's own
  "Allow access to USB device" dialog.
- Files reach it only when you choose them, share them to it, or open them with it. They are read through Android's file picker
  or the sharing system, and temporary copies (scanned pages, shared text turned into a PDF) live in the app's private cache.
- It collects and sends nothing: no analytics, no crash reporting, no accounts.

## Reporting a vulnerability

If you find a problem that could let someone read data they should not, run code on the phone or the printer, or get around
the permission model, please report it privately rather than in a public issue:

- Use GitHub's **Report a vulnerability** (Security tab → Advisories) on the repository, or
- email the maintainer at the address on the GitHub profile.

Please include what you found, how to reproduce it, and the app version (About screen). You will get an answer within a
week or so; this is a spare-time project. Once a fix is released you are welcome to publish the details.

## Supported versions

Only the latest release gets fixes.
