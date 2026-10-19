#!/usr/bin/env bash
# Prints the CHANGELOG.md section of one version (without its "## [x.y.z] - date" heading), ready to be a GitHub release body.
#   .github/scripts/release-notes.sh 2.0.1 [path/to/CHANGELOG.md]
set -euo pipefail

version="${1:?usage: release-notes.sh <version, without the v> [changelog]}"
file="${2:-CHANGELOG.md}"

notes="$(awk -v v="$version" '
  index($0, "## [" v "]") == 1 { found = 1; next }
  found && /^## \[/            { exit }
  found                        { print }
' "$file")"

# trim the blank lines at both ends
notes="$(printf '%s\n' "$notes" | awk 'NF { started = 1 } started { lines[++n] = $0 } END { while (n > 0 && lines[n] == "") n--; for (i = 1; i <= n; i++) print lines[i] }')"

if [ -z "$notes" ]; then
  echo "release-notes.sh: no entry for version $version in $file" >&2
  exit 1
fi
printf '%s\n' "$notes"
