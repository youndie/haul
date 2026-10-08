#!/usr/bin/env bash
# B-03's acceptance for the image: it starts against a real PostgreSQL with its AOT cache accepted.
#
# `-XX:AOTMode=on` makes the JVM refuse to start when it cannot use the cache, so «ready» already
# means «accepted»; the class-load log then shows the server's own classes coming from the cache, which
# is the half `on` alone does not prove (a cache can be valid and hold nothing of ours).
#
#   scripts/image-check.sh [image-tag]
set -u
cd "$(dirname "$0")/.."
IMAGE=${1:-haul/server:check}
NET=haul-check-$$
PORT=${PORT:-18080}

./gradlew :server:installDist --no-daemon --console=plain -q || exit 1
docker build -f docker/Dockerfile -t "$IMAGE" server/build/install/server > /tmp/haul-image-build.log 2>&1 \
  || { echo "image build failed:"; tail -40 /tmp/haul-image-build.log; exit 1; }

docker network create "$NET" >/dev/null
PG=$(docker run -d --network "$NET" --name "$NET-pg" -e POSTGRES_USER=haul -e POSTGRES_PASSWORD=haul -e POSTGRES_DB=haul postgres:18-alpine)
cleanup() { docker rm -f "${CID:-}" "$PG" >/dev/null 2>&1; docker network rm "$NET" >/dev/null 2>&1; }
trap cleanup EXIT
for _ in $(seq 60); do docker exec "$PG" pg_isready -U haul -d haul >/dev/null 2>&1 && break; sleep 0.5; done

CID=$(docker run -d --network "$NET" -p "$PORT:8080" \
  -e HAUL_DB_URL="jdbc:postgresql://$NET-pg:5432/haul" -e HAUL_DB_USER=haul -e HAUL_DB_PASSWORD=haul -e HAUL_SEED=true \
  -e JAVA_OPTS="-XX:AOTMode=on -Xlog:class+load=info" "$IMAGE")
ready=no
for _ in $(seq 120); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/readyz")" = 200 ] && { ready=yes; break; }
  sleep 0.5
done
LOG=$(docker logs "$CID" 2>&1)
ours=$(printf '%s\n' "$LOG" | grep -E '\] io\.github\.youndie\.haul\.' | grep -c 'source:')
cached=$(printf '%s\n' "$LOG" | grep -E '\] io\.github\.youndie\.haul\.' | grep -c 'source: shared objects file')
echo "ready=$ready; io.github.youndie.haul classes from the cache: $cached of $ours"
printf '%s\n' "$LOG" | grep -E '\[(error|warning)\]\[aot' | head -5
if [ "$ready" != yes ] || [ "$cached" -eq 0 ] || [ "$cached" != "$ours" ]; then
  echo "--- the container's output:"; printf '%s\n' "$LOG" | grep -v 'source: ' | tail -20
  exit 1
fi
