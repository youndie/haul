#!/usr/bin/env bash
# B-03's acceptance for the image: it starts against a real PostgreSQL with its AOT cache accepted.
# And B-27's: it serves the browser bundle it carries at `/`, labelled so the browser streams the module.
# And B-34's: the module goes out precompressed — brotli to a browser's Accept-Encoding, gzip to a
# client that accepts only gzip — still labelled `application/wasm`, with `Vary: Accept-Encoding` and a
# year's `immutable` (its name is its content hash), while the page is `no-cache`; and the source map is
# neither in the distribution nor served.
# And B-36's: a storefront address — a product link reloaded or shared — answers the page, `no-cache`
# and precompressed like `/`, while a path that is no storefront address stays a 404.
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

scripts/image-build.sh "$IMAGE" || exit 1

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
page=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/")
module=$(cd server/build/install/server/web && ls -- *.wasm | head -1)
wasm=$(curl -s -o /dev/null -w '%{content_type}' "http://127.0.0.1:$PORT/$module")
# Headers of one request, lower-cased names: $1 = path, $2 = Accept-Encoding (empty: none sent).
headers() { curl -s -o /dev/null -D - ${2:+-H "Accept-Encoding: $2"} "http://127.0.0.1:$PORT/$1" | tr -d '\r' | tr 'A-Z' 'a-z'; }
header() { printf '%s\n' "$1" | sed -n "s/^$2: //p" | head -1; }
BROWSER='gzip, deflate, br, zstd'
h=$(headers "$module" "$BROWSER")
br_enc=$(header "$h" content-encoding); br_type=$(header "$h" content-type); br_vary=$(header "$h" vary)
br_cache=$(header "$h" cache-control)
gz_enc=$(header "$(headers "$module" gzip)" content-encoding)
page_cache=$(header "$(headers "" "$BROWSER")" cache-control)
map=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/composeApp.js.map")
ADDRESS=p/p-sony-wh-1000xm6
addr_status=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/$ADDRESS")
h=$(headers "$ADDRESS" "$BROWSER")
addr_type=$(header "$h" content-type); addr_cache=$(header "$h" cache-control); addr_enc=$(header "$h" content-encoding)
unknown=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/nowhere")
shipped_maps=$(find server/build/install/server/web -name '*.map' | wc -l | tr -d ' ')
echo "module $module to a browser: encoding=$br_enc type=$br_type vary=$br_vary cache=$br_cache;" \
  "to gzip only: encoding=$gz_enc; page cache=$page_cache; map=$map, maps in the distribution: $shipped_maps;" \
  "/$ADDRESS: $addr_status type=$addr_type cache=$addr_cache encoding=$addr_enc; /nowhere: $unknown"
bundle_ok=yes
[ "$br_enc" = br ] && [ "$gz_enc" = gzip ] && [ "${br_type%%;*}" = application/wasm ] \
  && printf '%s' "$br_vary" | grep -q accept-encoding \
  && [ "$br_cache" = "public, max-age=31536000, immutable" ] && [ "$page_cache" = no-cache ] \
  && [ "$map" = 404 ] && [ "$shipped_maps" = 0 ] \
  && [ "$addr_status" = 200 ] && [ "${addr_type%%;*}" = text/html ] && [ "$addr_cache" = no-cache ] \
  && [ "$addr_enc" = br ] && [ "$unknown" = 404 ] || bundle_ok=no

LOG=$(docker logs "$CID" 2>&1)
ours=$(printf '%s\n' "$LOG" | grep -E '\] io\.github\.youndie\.haul\.' | grep -c 'source:')
cached=$(printf '%s\n' "$LOG" | grep -E '\] io\.github\.youndie\.haul\.' | grep -c 'source: shared objects file')
echo "ready=$ready; io.github.youndie.haul classes from the cache: $cached of $ours; page=$page; wasm=$wasm"
printf '%s\n' "$LOG" | grep -E '\[(error|warning)\]\[aot' | head -5
if [ "$ready" != yes ] || [ "$cached" -eq 0 ] || [ "$cached" != "$ours" ] || [ "$page" != 200 ] \
  || [ "$wasm" != application/wasm ] || [ "$bundle_ok" != yes ]; then
  echo "--- the container's output:"; printf '%s\n' "$LOG" | grep -v 'source: ' | tail -20
  exit 1
fi
