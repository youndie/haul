#!/usr/bin/env bash
# The server's image, built the one way every check uses: the distribution (`:server:installDist`,
# which carries the browser bundle), then `docker/Dockerfile` with the distribution as its context — the
# training stage writes the AOT cache against its own PostgreSQL (research §1.5, risk 4).
#
# `scripts/image-check.sh` (B-03) checks the image this builds; `scripts/e2e.sh` (B-26) walks the
# storefront against it. One recipe, so the image the whole path runs on is the image that ships.
#
#   scripts/image-build.sh [image-tag]
set -u
cd "$(dirname "$0")/.."
IMAGE=${1:-haul/server:check}
LOG=${IMAGE_BUILD_LOG:-/tmp/haul-image-build.log}

# GRADLE_ARGS: anything the machine needs Gradle to be told (a worker limit on a shared box).
# shellcheck disable=SC2086
./gradlew :server:installDist --no-daemon --console=plain -q ${GRADLE_ARGS:-} || exit 1
docker build -f docker/Dockerfile -t "$IMAGE" server/build/install/server > "$LOG" 2>&1 \
  || { echo "image build failed:"; tail -40 "$LOG"; exit 1; }
