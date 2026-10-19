#!/usr/bin/env bash
# Builds, signs and publishes one release: the APK built from a tag, with that version's CHANGELOG section as the notes.
#
#   tools/release.sh v2.0.1            build the tag, sign it, create (or update) the GitHub release
#   tools/release.sh v2.0.1 --dry-run  do everything except talk to GitHub; the files are left in build/releases/
#
# What it needs: JDK 17, the Android SDK (ANDROID_HOME) with build-tools 35.0.0, the GitHub CLI (`gh`, logged in) and a signing
# key. The key lives OUTSIDE the repository, in ~/.freeprint-release/credentials.env:
#   KEYSTORE=/path/to/freeprint-release.jks
#   KEY_ALIAS=freeprint
#   KEYSTORE_PASSWORD=...
#   KEY_PASSWORD=...
# The tag is built in a temporary git worktree, so the working tree you are in is not touched. Notes always come from the
# CHANGELOG.md you are standing in, so older tags (written before the changelog existed) still get proper notes.
set -euo pipefail

tag="${1:?usage: tools/release.sh vX.Y.Z [--dry-run]}"
dry=false; [ "${2:-}" = "--dry-run" ] && dry=true
version="${tag#v}"
root="$(git rev-parse --show-toplevel)"
cred="${FREEPRINT_CREDENTIALS:-$HOME/.freeprint-release/credentials.env}"
bt="${ANDROID_HOME:?set ANDROID_HOME}/build-tools/${BUILD_TOOLS:-35.0.0}"
out="$root/build/releases"
repo="$(git -C "$root" remote get-url origin | sed -E 's#(git@github.com:|https://github.com/)##; s#\.git$##')"

git -C "$root" rev-parse -q --verify "refs/tags/$tag" >/dev/null || { echo "tag $tag does not exist"; exit 1; }
[ -f "$cred" ] || { echo "signing credentials not found at $cred"; exit 1; }
# shellcheck disable=SC1090
source "$cred"
notes_body="$("$root/.github/scripts/release-notes.sh" "$version" "$root/CHANGELOG.md")"

work="$(mktemp -d)"
cleanup() { git -C "$root" worktree remove --force "$work/src" >/dev/null 2>&1 || true; rm -rf "$work"; }
trap cleanup EXIT

echo "==> building $tag"
git -C "$root" worktree add -q --detach "$work/src" "$tag"
skipped_lint=false
if ! ( cd "$work/src" && ./gradlew assembleRelease -q --console=plain ); then
  # Tags from before the generated-asset task was fixed fail Android's "lint vital" release check for a build-script reason that
  # has nothing to do with the app. Build them without that one check, and say so in the notes.
  echo "==> retrying without the lintVitalRelease check"
  ( cd "$work/src" && ./gradlew assembleRelease -x lintVitalRelease -q --console=plain )
  skipped_lint=true
fi
unsigned="$(ls "$work"/src/app/build/outputs/apk/release/*-unsigned.apk)"

echo "==> signing"
mkdir -p "$out"
apk="$out/FreePrint-$version.apk"
"$bt/zipalign" -p -f 4 "$unsigned" "$work/aligned.apk"
KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" KEY_PASSWORD="$KEY_PASSWORD" "$bt/apksigner" sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --alignment-preserved --out "$apk" "$work/aligned.apk"
# Only the v2 and v3 signature blocks, no META-INF signature files, and the zip layout left as the build made it (--alignment-preserved,
# otherwise apksigner pads entries): a rebuild, such as F-Droid's, must match the unsigned build exactly
if unzip -l "$apk" | grep -q 'META-INF/.*\.\(SF\|RSA\)'; then echo "the APK carries v1 signature files"; exit 1; fi
# GitHub releases cannot be changed once published, so prove the signature can be matched to the unsigned build before publishing
# (this is the check F-Droid makes). Needs `pip install apksigcopier` and apksigner on PATH.
command -v apksigcopier >/dev/null || { echo "install apksigcopier first: pip install apksigcopier"; exit 1; }
PATH="$bt:$PATH" apksigcopier compare "$apk" --unsigned "$unsigned" >/dev/null 2>&1 \
  || { echo "the signed APK does not match the unsigned build; not publishing"; exit 1; }
echo "==> signature matches the unsigned build (reproducible-build check passed)"
"$bt/apksigner" verify --print-certs "$apk" | sed -n '1,3p'
( cd "$out" && shasum -a 256 "FreePrint-$version.apk" > "FreePrint-$version.apk.sha256" )
sha="$(cut -d' ' -f1 "$out/FreePrint-$version.apk.sha256")"
size="$(du -h "$apk" | cut -f1 | tr -d " ")"

# What the build says about itself, so the notes are checked against the real APK
aapt_info="$("$bt/aapt2" dump badging "$apk" 2>/dev/null | sed -n "s/^package: name='\([^']*\)' versionCode='\([^']*\)' versionName='\([^']*\)'.*/\1 \2 \3/p")"
read -r app_id version_code version_name <<<"$aapt_info"
[ "$version_name" = "$version" ] || [ "$version_name" = "$version-spike" ] || { echo "APK says version $version_name, the tag says $version"; exit 1; }

notes="$out/notes-$version.md"
{
  printf '%s\n\n' "$notes_body"
  printf -- '---\n\n'
  printf '**Install:** download `FreePrint-%s.apk` below and open it on an Android 8.0+ phone with a 64-bit ARM processor. Allow installing from your browser or file manager when Android asks.\n\n' "$version"
  printf '| | |\n|---|---|\n'
  printf '| Package | `%s` |\n| Version | %s (versionCode %s) |\n| Size | %s |\n| SHA-256 | `%s` |\n\n' "$app_id" "$version_name" "$version_code" "$size" "$sha"
  $skipped_lint && printf 'Built from the tag as it was, without Android'"'"'s "lint vital" release check, which that older build script could not pass.\n\n'
  printf 'Check the download with `shasum -a 256 FreePrint-%s.apk` against the `.sha256` file. The APK is signed with the project'"'"'s release key; the full history of changes is in [CHANGELOG.md](https://github.com/%s/blob/main/CHANGELOG.md).\n' "$version" "$repo"
} > "$notes"

if $dry; then echo "==> dry run: files are in $out"; ls -la "$out" | grep "$version"; exit 0; fi

flags=(); case "$version" in 0.*) flags+=(--prerelease);; esac
git -C "$root" ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1 || { echo "==> pushing tag $tag"; git -C "$root" push -q origin "$tag"; }
if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
  echo "==> updating release $tag"
  gh release edit "$tag" --repo "$repo" --title "Free Print $version" --notes-file "$notes" ${flags[@]+"${flags[@]}"}
  gh release upload "$tag" --repo "$repo" --clobber "$apk" "$apk.sha256"
else
  echo "==> creating release $tag"
  gh release create "$tag" --repo "$repo" --title "Free Print $version" --notes-file "$notes" ${flags[@]+"${flags[@]}"} "$apk" "$apk.sha256"
fi
echo "==> done: https://github.com/$repo/releases/tag/$tag"
