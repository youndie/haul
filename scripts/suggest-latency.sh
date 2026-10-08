#!/usr/bin/env bash
# B-09's measurement: how long `GET /ui/search/suggest` takes on the seed (research, open question 1).
#
# The server runs from `installDist` on the JVM (no AOT cache) against PostgreSQL 18 in Docker, both on
# this machine over loopback, the catalog seeded on start. One curl process walks the URL list with a
# kept-alive connection, so the numbers are request times, not process start-up. The list is a
# shopper typing: every prefix from two characters of a few queries, a misspelling and a query that
# finds nothing. One pass warms the JVM and PostgreSQL's caches and is thrown away; the next PASSES
# passes are kept. The very first request after start-up is reported apart, as the cold number.
#
# Raw times go to $OUT (one line per request: query, seconds); the summary is printed.
#
#   scripts/suggest-latency.sh [passes]
set -u
cd "$(dirname "$0")/.."
PASSES=${1:-20}
PORT=${PORT:-18081}
PG_PORT=${PG_PORT:-55433}
OUT=${OUT:-/tmp/suggest-latency}
mkdir -p "$OUT"

./gradlew :server:installDist --no-daemon --console=plain -q || exit 1

PG=$(docker run -d -p "127.0.0.1:$PG_PORT:5432" -e POSTGRES_USER=haul -e POSTGRES_PASSWORD=haul -e POSTGRES_DB=haul postgres:18-alpine)
cleanup() { [ -n "${SERVER:-}" ] && kill "$SERVER" 2>/dev/null; docker rm -f "$PG" >/dev/null 2>&1; }
trap cleanup EXIT
for _ in $(seq 60); do docker exec "$PG" pg_isready -U haul -d haul >/dev/null 2>&1 && break; sleep 0.5; done
sleep 1

HAUL_DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/haul" HAUL_DB_USER=haul HAUL_DB_PASSWORD=haul HAUL_SEED=true \
  PORT="$PORT" server/build/install/server/bin/server > "$OUT/server.log" 2>&1 &
SERVER=$!
for _ in $(seq 240); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/readyz")" = 200 ] && break
  sleep 0.5
done

QUERIES=("running shoes" "headphones" "sony" "wireless earbuds" "yoga mats" "heaphones" "xqzt")
ARGS=()
LABELS=()
for q in "${QUERIES[@]}"; do
  for n in $(seq 2 ${#q}); do
    p=${q:0:$n}
    ARGS+=(-o /dev/null "http://127.0.0.1:$PORT/ui/search/suggest?q=$(printf '%s' "$p" | sed 's/ /%20/g')")
    LABELS+=("$p")
  done
done

# Cold: the first suggest after start-up, before anything else has run the search path.
cold=$(curl -s -w '%{time_total}' "${ARGS[@]:0:3}")

run() { curl -s -w '%{http_code} %{time_total}\n' "${ARGS[@]}"; }
run > "$OUT/warmup.txt"
: > "$OUT/raw.txt"
for i in $(seq "$PASSES"); do
  run | paste -d' ' <(printf '%s\n' "${LABELS[@]}" | sed 's/ /_/g') - >> "$OUT/raw.txt"
done

bad=$(awk '$2 != 200' "$OUT/raw.txt" | wc -l)
n=$(wc -l < "$OUT/raw.txt")
# Nearest-rank percentiles over the kept passes.
sort -n -k3 "$OUT/raw.txt" | awk -v n="$n" -v cold="$cold" -v bad="$bad" '
  function at(q) { i = int(q * n); if (i < q * n) i++; if (i < 1) i = 1; return t[i] }
  { t[NR] = $3 * 1000 }
  END {
    printf "n=%d (non-200: %d)  cold first request: %.1f ms\n", n, bad, cold * 1000
    printf "p50=%.2f ms  p90=%.2f ms  p99=%.2f ms  max=%.2f ms\n", at(0.50), at(0.90), at(0.99), t[n]
  }'
# The same per query length bucket: two to four characters, five and more.
awk '{ split($1, a, ""); len = length($1); b = (len <= 4) ? "2-4" : "5+"; print b, $3 * 1000 }' "$OUT/raw.txt" \
  | sort -k1,1 -k2,2n | awk '{ v[$1, ++c[$1]] = $2 } END { for (k in c) printf "%s chars: n=%d p50=%.2f ms p90=%.2f ms\n", k, c[k], v[k, int(c[k]*0.5)], v[k, int(c[k]*0.9)] }' 

# The two statements behind one suggestion, as PostgreSQL plans and times them on this seed — as the
# measured run found the tables, and again after an explicit ANALYZE.
DOC="to_tsvector('english', p.title || ' ' || p.brand || ' ' || coalesce(p.kind, ''))"
MATCHING="SELECT p.id, p.category_slug FROM products p WHERE $DOC @@ to_tsquery('english', 'running:* & sh:*') OR lower(p.title) LIKE '%running sh%' ORDER BY ts_rank($DOC, to_tsquery('english', 'running:* & sh:*')) DESC, p.reviews_count DESC, p.id"
TERMS="WITH terms(term, n) AS (SELECT lower(c.name), count(p.id) FROM categories c JOIN products p ON p.category_slug = c.slug GROUP BY lower(c.name) UNION ALL SELECT lower(p.brand), count(*) FROM products p GROUP BY lower(p.brand) UNION ALL SELECT lower(p.kind), count(*) FROM products p WHERE p.kind IS NOT NULL GROUP BY lower(p.kind)) SELECT term FROM terms GROUP BY term HAVING term LIKE 'running sh%' OR term LIKE '% running sh%' OR similarity(term, 'running sh') > 0.3 ORDER BY term LIKE 'running sh%' DESC, sum(n) DESC, similarity(term, 'running sh') DESC, term LIMIT 5"
psql() { docker exec "$PG" psql -U haul -d haul -c "$1"; }
{ psql "EXPLAIN (ANALYZE, BUFFERS) $MATCHING"; psql "EXPLAIN (ANALYZE, BUFFERS) $TERMS"; } > "$OUT/explain-as-run.txt"
psql "ANALYZE" >/dev/null
{ psql "EXPLAIN (ANALYZE, BUFFERS) $MATCHING"; psql "EXPLAIN (ANALYZE, BUFFERS) $TERMS"; } > "$OUT/explain-analyzed.txt"
{ psql "SELECT count(*) AS products FROM products"; psql "SELECT version()"; java -version 2>&1 | head -1; nproc; uptime; } > "$OUT/stand.txt"
echo "raw: $OUT"
