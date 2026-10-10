#!/usr/bin/env bash
# B-31's image: the synthetic shoppers (`docker/shoppers.Dockerfile`, the e2e module's `main`), built from
# `:e2e:installDist`, and asked to start with no configuration — it must refuse, naming the first variable it
# needs, with exit 2. That proves the image runs its JVM and its main as the user it runs as, and that it
# says what is missing rather than walking nowhere. The walk itself is tested by the e2e
# (`SyntheticShopperTest`), against the composed stack.
#
#   scripts/shoppers-check.sh [image-tag]
set -u
cd "$(dirname "$0")/.."
IMAGE=${1:-haul/shoppers:check}
LOG=${SHOPPERS_BUILD_LOG:-/tmp/haul-shoppers-build.log}

# GRADLE_ARGS: anything the machine needs Gradle to be told (a worker limit on a shared box).
# shellcheck disable=SC2086
./gradlew :e2e:installDist --no-daemon --console=plain -q ${GRADLE_ARGS:-} || exit 1
docker build -f docker/shoppers.Dockerfile -t "$IMAGE" e2e/build/install/shoppers > "$LOG" 2>&1 \
  || { echo "image build failed:"; tail -40 "$LOG"; exit 1; }

out=$(docker run --rm "$IMAGE" 2>&1); status=$?
case "$status:$out" in
  "2:"*"refused: HAUL_SHOPPERS_ORIGIN is required"*) echo "the image starts and refuses an empty configuration: ok" ;;
  *) echo "the image answered $status, not the refusal: $out"; exit 1 ;;
esac
