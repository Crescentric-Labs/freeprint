# Publishing to F-Droid

This is the checklist and the notes for getting Free Print into the [F-Droid](https://f-droid.org) main repository, and for
keeping it there. F-Droid builds the app **from source** on its own servers and signs it with its own key, so nothing here is
uploaded: the work is making sure the source builds cleanly and describing the app.

## Status

The first request ([merge request 53306](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/53306), opened 2026-10-19) was
**closed by an F-Droid maintainer on 2026-10-20**: the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy) wants an app
to have been actively developed for at least three months, unless it already has some users, its author maintains other apps on
F-Droid, or it is made for an event. Free Print's first commit was on 2026-10-11, so three months are over around 2027-01-11. The
maintainer invited reopening the request once the criteria are met. Until then keep releasing as usual; everything below stays
valid. When reopening, refresh the metadata first (current version and full commit hash) and give `UpdateCheckMode` a tag pattern
so that beta tags are ignored: `UpdateCheckMode: Tags ^v[0-9.]+$` (checked with `fdroid lint` and `fdroid rewritemeta`).

## What the project already does for F-Droid

| F-Droid expects | State |
|---|---|
| A free-software licence | GPL-3.0 (`LICENSE`); the bundled libusb is LGPL-2.1-or-later and compatible |
| Everything built from source | Kotlin and C++ only. libusb 1.0.30 is vendored as source in `app/src/main/cpp/third_party/libusb/`; no prebuilt `.so`, `.jar` or `.aar` is committed (the Gradle wrapper jar is the one standard exception) |
| No proprietary dependencies | AndroidX, Jetpack Compose, Kotlin coroutines, all Apache-2.0. No Google Play services, no Firebase, no analytics, no ads |
| No tracking or network use | The manifest declares **no permissions**, in particular not `INTERNET` |
| No anti-features | None apply: no ads, no tracking, no non-free network services, no non-free assets or dependencies |
| No dependency blob in the APK | `dependenciesInfo { includeInApk = false }` in `app/build.gradle.kts` (AGP otherwise adds a Google-signed block that F-Droid rejects and that breaks reproducible builds) |
| A build that works from a clean checkout | `./gradlew assembleRelease` succeeds with no signing config and no local files other than the SDK path |
| Version in the build file | `versionName` and `versionCode` are plain literals in `app/build.gradle.kts`, so F-Droid can read them |
| Store texts and images | `fastlane/metadata/android/en-US/`: title, short and full description, per-version changelogs, icon, screenshots |
| A tag per release | Tag the release commit `vX.Y.Z` (see below) |

## Before the first submission

1. **Make the repository public** and confirm the URL in `README.md`, `app/.../AboutScreen.kt` (`SOURCE_URL`) and
   `docs/fdroid.md` is right.
2. **Run a clean release build** on a machine that has nothing else set up:
   ```sh
   git clone https://github.com/Crescentric-Labs/freeprint.git && cd freeprint
   ./gradlew clean assembleRelease
   ```
   The unsigned APK is at `app/build/outputs/apk/release/app-release-unsigned.apk`.
3. **Check the screenshots** in `fastlane/metadata/android/en-US/images/phoneScreenshots/` still match the app.
4. **Check the licence story**: the About screen shows the GPL notice and the libusb credit and lets people read both texts.
5. **Tag the release** and push the tag (`git tag v2.0.2 && git push origin v2.0.2`).
6. **Open a merge request** against [fdroiddata](https://gitlab.com/fdroid/fdroiddata) adding
   `metadata/crescentric.freeprint.otg.yml` (see below). Follow the
   [inclusion how-to](https://f-droid.org/docs/Inclusion_How-To/); the reviewers will run their scanner and a test build and
   may ask for small changes.

## The metadata file

The file F-Droid needs is kept here: [`docs/fdroid/crescentric.freeprint.otg.yml`](fdroid/crescentric.freeprint.otg.yml). It has
been checked with F-Droid's own tools (`fdroid lint` is clean and `fdroid rewritemeta` leaves it unchanged), and
`fdroid build` rebuilt tag `v2.0.5` from it on the author's Mac and its reference-binary check verified the result against the GitHub APK.

Notes on it:

- `ndk: r27c` is the NDK this project is built with (`27.2.12479018`); about 560 apps in F-Droid's repository use it.
- The licence is `GPL-3.0-only` because the project's `LICENSE` and About screen say "version 3" without "or later". If you want
  "or later", say so in the notices (and in the About text) first and change this line.
- `UpdateCheckMode: Tags` plus `AutoUpdateMode: Version` makes F-Droid pick up each new `vX.Y.Z` tag by itself, as long as
  `versionName`/`versionCode` in that tag match. After a release only the changelog and the tag are needed.
- The `sudo` lines install JDK 17 on the build server, as many Gradle apps do.
- Add `AntiFeatures` only if one ever applies. None does today.

## Submitting it (a merge request on GitLab)

This needs your own account on [gitlab.com](https://gitlab.com); nobody else can do it for you.

1. **Fork** [gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) with the Fork button.
2. **Clone your fork** and make a branch named after the app:
   ```sh
   git clone --depth 1 https://gitlab.com/<your-user>/fdroiddata.git && cd fdroiddata
   git checkout -b crescentric.freeprint.otg
   ```
3. **Add the file** and check it with F-Droid's tool (`pip install fdroidserver` or `pipx install fdroidserver`):
   ```sh
   cp /path/to/freeprint/docs/fdroid/crescentric.freeprint.otg.yml metadata/
   fdroid lint crescentric.freeprint.otg
   fdroid rewritemeta crescentric.freeprint.otg     # should change nothing
   ```
4. **Commit and push**:
   ```sh
   git add metadata/crescentric.freeprint.otg.yml
   git commit -m "New app: Free Print"
   git push -u origin crescentric.freeprint.otg
   ```
5. **Open the merge request** from your fork's page (GitLab offers a button after the push). Title it `New app: Free Print` and use
   the checklist in the description (below). Tick the boxes that are true, and leave the rest.
6. **Answer the reviewers.** They run a scanner and a test build and may ask for small changes (a different Java setup, a category,
   a licence detail). Push fixes to the same branch. Reviews usually take days to a few weeks.

Suggested merge request description:

```
Adds Free Print (crescentric.freeprint.otg).

Free Print prints PDFs, pictures and text to a USB printer over OTG, and scans from its glass, with no drivers and no network.
It is also an Android print service. The app declares no permissions at all.

- Source: https://github.com/Crescentric-Labs/freeprint (GPL-3.0-only, libusb 1.0.30 LGPL-2.1+ vendored as source)
- No ads, no tracking, no network access, no non-free dependencies or assets
- Built from source with Gradle; native code with CMake and NDK r27c (arm64-v8a only)
- dependenciesInfo is disabled in the APK
- Reproducible builds: Binaries and AllowedAPKSigningKeys are set; the build verifies against the published APK
- Store texts, changelogs, icon and screenshots are in the app repository under fastlane/metadata
- fdroid lint is clean; fdroid build succeeds locally for v2.0.5 and verifies against the GitHub release APK (reproducible build)
```

After it is merged F-Droid builds, signs and publishes the app (the first listing can take a few days), and later versions need
only a new tag.

## Releasing a new version

1. Put the changes under `## [Unreleased]` in `CHANGELOG.md` as you work, one plain line each.
2. To release: rename that heading to `## [X.Y.Z] - YYYY-MM-DD`, add a fresh empty `## [Unreleased]` above it.
3. In `app/build.gradle.kts` set `versionName = "X.Y.Z"` and raise `versionCode` by one.
4. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 bytes at most, a few bullet lines).
5. Run `./gradlew testDebugUnitTest assembleRelease`, fix anything red, and refresh the screenshots if the UI changed.
6. Commit, then `git tag vX.Y.Z` and push the branch and the tag.
7. F-Droid notices the tag on its next check (usually within a day or two) and builds it. Nothing else to do.

The in-app changelog (About, Changelog) reads `CHANGELOG.md` directly: the build copies it into the app's assets, so the
file and the app cannot drift apart.

## Signing and reproducible builds

Free Print uses [reproducible builds](https://f-droid.org/docs/Reproducible_Builds/), so F-Droid publishes *this project's*
signed APK and people can update between GitHub and F-Droid without reinstalling. The metadata has a `Binaries:` URL (the
GitHub release APK) and `AllowedAPKSigningKeys:` (the release certificate's SHA-256). F-Droid builds the tag itself, copies
the signature from the GitHub APK onto its build and publishes it only if the result verifies.

What makes that work, and must stay so:

- The release APK is signed with v2 and v3 only, with `--alignment-preserved` (see `tools/release.sh`); `release.sh` runs
  `apksigcopier compare` against the unsigned build and refuses to publish if they differ.
- Native code builds with `-ffile-prefix-map` and `--build-id=none`; AGP's `vcsInfo` is off.
- GitHub releases are immutable: a bad APK cannot be replaced, only superseded by a new version (2.0.4 is such a case).
- The keystore lives outside the repository (`~/.freeprint-release/`) and is never committed.

## Things that commonly delay a submission

- A `build/` folder, `local.properties`, a keystore or an `.apk` committed by accident (this project's `.gitignore` covers them).
- A dependency or plugin that fetches something at build time from a non-standard repository: only `google()` and
  `mavenCentral()` are used here.
- Store text that mentions a non-free service or a price.
- A store description claiming support the app cannot back up: Free Print says exactly which printer it was tested on.
