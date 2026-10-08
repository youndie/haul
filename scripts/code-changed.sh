#!/usr/bin/env bash
# Whether this change touches anything but documentation, for the code jobs of `check.yaml`: prints
# `code=true` or `code=false` in the form `$GITHUB_OUTPUT` takes.
#
# A branch that only moves documents — a backlog item filed, a draft synced — has nothing for Gradle,
# the image or the chart to say, and paid ten minutes of runners for it. The jobs still run and still
# report under their names, so the merge gate sees them succeed; only their steps are skipped.
#
# Everything else runs, and so does the default branch on every push: two changes that are each
# harmless alone can disagree once merged, and that is the moment worth checking. When there is
# nothing to compare against (a new branch whose base cannot be found), it runs everything too.
#
#   scripts/code-changed.sh >> "$GITHUB_OUTPUT"
set -euo pipefail
cd "$(dirname "$0")/.."

DOCS='^(docs/|backlog\.md$|README\.md$|CLAUDE\.md$)'
BASE=${GITHUB_BASE_REF:-main}

if [ "${GITHUB_EVENT_NAME:-}" != pull_request ] && [ "${GITHUB_REF:-}" = "refs/heads/$BASE" ]; then
  echo code=true
  exit 0
fi

if ! git fetch --quiet origin "$BASE" || ! changed=$(git diff --name-only "origin/$BASE...HEAD"); then
  echo code=true
  exit 0
fi

code=$(printf '%s\n' "$changed" | grep -vE "$DOCS" | grep -v '^$' || true)
if [ -z "$changed" ] || [ -n "$code" ]; then
  echo code=true
else
  echo code=false
fi
