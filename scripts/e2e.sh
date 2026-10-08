#!/usr/bin/env bash
# B-26's acceptance: one run that browses, buys, receives and returns, over HTTP, against the server's
# image with PostgreSQL and shildik beside it. The image is built the one way the image job builds it
# (`scripts/image-build.sh`); the stack is started by the test itself, with Testcontainers
# (`e2e/src/test/kotlin/.../ComposedStack.kt`), so there is no compose file to keep in step.
#
# Needs Docker on Linux: the server's container shares the host's network, so the issuer of the realm is
# the same address for the server and for the test (ComposedStack's KDoc says why).
#
#   scripts/e2e.sh [image-tag]
set -u
cd "$(dirname "$0")/.."
IMAGE=${1:-haul/server:e2e}

if [ "${E2E_SKIP_BUILD:-}" != true ]; then
  scripts/image-build.sh "$IMAGE" || exit 1
fi
# shellcheck disable=SC2086
./gradlew :e2e:test "-Phaul.e2e.image=$IMAGE" --no-daemon --console=plain ${GRADLE_ARGS:-}
