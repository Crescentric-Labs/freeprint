# Releasing

How a version gets from `CHANGELOG.md` to a tagged GitHub release with a signed APK, by hand or by GitHub Actions. For getting
into F-Droid see [fdroid.md](fdroid.md); F-Droid builds and signs by itself from the same tags.

## The pieces

| Piece | What it does |
|---|---|
| `CHANGELOG.md` | The source of truth for release notes. The app shows it too (About → Changelog) |
| `.github/scripts/release-notes.sh X.Y.Z` | Prints one version's section of the changelog |
| `tools/release.sh vX.Y.Z` | Builds the tag, signs the APK, creates or updates the GitHub release (run on your computer) |
| `.github/workflows/ci.yml` | On every push and pull request: strings in step, unit tests, debug and release builds, a changelog entry exists |
| `.github/workflows/release.yml` | On every `vX.Y.Z` tag: builds, signs and publishes the release (needs the four secrets below) |
| `~/.freeprint-release/` | Your signing key and passwords. **Outside the repository, never committed** |

## Making a release

1. **Write the changelog.** While you work, add one plain line per user-visible change under `## [Unreleased]`. To release, rename
   that heading `## [X.Y.Z] - YYYY-MM-DD` and add a new empty `## [Unreleased]` above it.
2. **Bump the version** in `app/build.gradle.kts`: `versionName = "X.Y.Z"`, and `versionCode` plus one.
3. **Add the store changelog** `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 bytes at most).
4. **Check**: `./gradlew testDebugUnitTest assembleRelease`. CI repeats this and also checks that the version has a changelog entry.
5. **Commit and tag** the release commit, then push both:
   ```sh
   git tag -a vX.Y.Z -m "Free Print X.Y.Z"
   git push origin main vX.Y.Z
   ```
6. **Publish.** Either let GitHub Actions do it (needs the secrets, below), or run it yourself:
   ```sh
   tools/release.sh vX.Y.Z            # use --dry-run first to see what it would upload
   ```
   Both give the release the title `Free Print X.Y.Z`, the changelog section as its notes, the signed `FreePrint-X.Y.Z.apk`, a
   `.sha256` file, and mark 0.x versions as pre-releases. Running either again on the same tag updates the release instead of
   making a second one.

## The signing key

APKs are signed with a release key kept in `~/.freeprint-release/freeprint-release.jks`, with its alias and passwords in
`credentials.env` next to it. **Back both up somewhere safe.** If the key is lost, nobody can install an update over an
existing install: Android requires the same signature, so everyone would have to uninstall first. If the key leaks, anyone
can publish "updates" that Android accepts. It is never committed (`.gitignore` does not need to know about it because it is not
inside the repository).

To make a new key:

```sh
keytool -genkeypair -keystore ~/.freeprint-release/freeprint-release.jks -alias freeprint \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Free Print, OU=Crescentric, O=Crescentric, C=LK"
```

and put these four lines in `~/.freeprint-release/credentials.env` (readable only by you, `chmod 600`):

```sh
KEYSTORE=/Users/you/.freeprint-release/freeprint-release.jks
KEY_ALIAS=freeprint
KEYSTORE_PASSWORD=...
KEY_PASSWORD=...
```

**F-Droid signs with its own key**, so an APK installed from F-Droid cannot be updated from a GitHub release or the other way round
without uninstalling. Pick one source per phone.

Check any APK's signature with
`$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs FreePrint-X.Y.Z.apk`. Every release of this project shows the same
certificate fingerprint.

## Turning on automatic releases (GitHub Actions)

The release workflow needs four **repository secrets**: Settings → Secrets and variables → Actions → New repository secret, or with
the GitHub CLI from the folder that holds your key:

```sh
cd ~/.freeprint-release
base64 -i freeprint-release.jks | tr -d '\n' | gh secret set KEYSTORE_BASE64
grep '^KEY_ALIAS=' credentials.env         | cut -d= -f2- | gh secret set KEY_ALIAS
grep '^KEYSTORE_PASSWORD=' credentials.env | cut -d= -f2- | gh secret set KEYSTORE_PASSWORD
grep '^KEY_PASSWORD=' credentials.env      | cut -d= -f2- | gh secret set KEY_PASSWORD
```

Secrets are write-only on GitHub: you cannot read them back, so keep your own backup. Without them the workflow still builds and
tests the tag but publishes nothing, and says so in the run. You can also run it by hand on an existing tag from the Actions tab
(**Release → Run workflow**, enter the tag).

Only tags from before the workflow existed (`v0.1.0`, `v1.0.0`, `v2.0.0`) cannot trigger it, because the workflow file is not in
them; they were published with `tools/release.sh`.

## Branches and betas

- `main` is what has been released. `develop` is where finished features meet before a release. Work happens on
  `feature/<name>` branches, which are merged into `develop`.
- Every push to `develop` runs the **Beta** workflow (`.github/workflows/beta.yml`): tests, a release build and, if
  `versionName` is a beta such as `2.1.0-beta.1` that has not been published yet, a signed APK published as a GitHub
  **pre-release** tagged `beta-2.1.0-beta.1`. Betas are never "latest", so the normal download button is not affected.
- To publish another beta: raise the beta number in `app/build.gradle.kts`, add a `## [2.1.0-beta.2] - date` section to
  `CHANGELOG.md`, merge into `develop`. Published releases cannot be changed, so a new beta always needs a new number.
- To ship: on `develop` set the plain version (`2.1.0`), move the changelog entries under it, merge `develop` into `main`, then
  tag `v2.1.0` as usual.

## When something goes wrong

| Symptom | Cause and fix |
|---|---|
| The release workflow says the tag and `versionName` differ | You tagged before bumping the version. Delete the tag (`git tag -d vX.Y.Z && git push origin :refs/tags/vX.Y.Z`), fix, tag again |
| `release-notes.sh: no entry for version X.Y.Z` | The changelog heading is not `## [X.Y.Z] - date`, or it was not moved out of `[Unreleased]` |
| `assembleRelease` fails on `lintVitalAnalyzeRelease` | An old tag from before the generated-asset task was fixed; `tools/release.sh` retries without that check by itself |
| Users cannot install an update | The APK was signed with a different key than the installed one |
