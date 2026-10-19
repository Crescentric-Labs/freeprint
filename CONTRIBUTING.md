# Contributing to Free Print

Thank you for helping. Free Print is a small project run by one person, so the most useful things you can send are, in order:

1. **A report from your printer.** It is the one thing the author cannot do alone. See
   [Printer reports](#printer-reports).
2. **A bug report** with clear steps, the phone and Android version, and what you expected.
3. **A translation fix** (the Sinhala text has not been reviewed by a native speaker) or a **new language**.
4. **A code change**, small and focused.

By taking part you agree to be kind and to keep discussion about the work. Harassment of any kind is not acceptable and will
get a comment removed or a person blocked.

## Printer reports

The app was developed on a Canon PIXMA G2020. Whether another printer works depends on whether it exposes the IPP-over-USB
interface and accepts 600 dpi PWG raster. To report one:

1. Plug the printer in, open Free Print, go to **About → Diagnostics**.
2. Run **Interfaces** and **IPP attributes**, and copy the output.
3. Print the test page and say what came out.
4. Open an issue with the **Printer report** template and paste the output.

Please remove anything private (serial numbers are in the output: your call).

## Building and testing

See the [developer setup in the README](README.md#developer-setup) and [docs/dev-setup.md](docs/dev-setup.md). In short:

```sh
./gradlew testDebugUnitTest assembleDebug
```

Please keep the build green and the tests passing. Pure logic (layout, page ranges, planning, parsing) lives away from Android
types so it can be unit tested on the JVM; new logic of that kind should come with tests.

## Code style

- Kotlin with the official style (`kotlin.code.style=official`). Match the code around you: comments explain *why*, are short
  and plain, and are not repeated in every function.
- All text a person reads goes in string resources, through `tools/gen_strings.py`. Do not edit
  `res/values/strings.xml` or `res/values-si/strings.xml` by hand; edit the table in the script and run
  `python3 -I tools/gen_strings.py`.
- No new permissions. The app has none today, and that is a promise to its users. Anything that needs the network
  needs a discussion first.
- No new dependency without a reason, and it must be free software that F-Droid can build from source.

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat(print): ...`, `fix(scan): ...`, `docs: ...`,
  `test(...)`, `build: ...`, `refactor(...)`, `i18n: ...`. Say what changed and why in a short line.
- Small, reviewable commits. Do not mix a refactor with a behaviour change.
- Add a line to `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for anything a user would notice.
- Describe how you tested it. If you could not test on a printer, say so; that is fine and normal.

## Translations

1. Copy the English column of the table in `tools/gen_strings.py` into a new column for your language and add its folder to the
   generator's output list (see how `values-si` is written).
2. Add the language to `app/src/main/res/xml/locales_config.xml` and to the language dialog's list (`AppLocale.choices`).
3. Run the generator, build, and look at every screen: translated text is often longer than English.

## Security

Please do not open a public issue for a security problem. See [SECURITY.md](SECURITY.md).

## Licence of contributions

Free Print is under the GNU General Public License v3.0. By sending a contribution you agree it can be distributed under that
licence.

Be kind: everyone taking part is expected to follow the [code of conduct](CODE_OF_CONDUCT.md).
