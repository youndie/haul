#!/usr/bin/env bash
# B-28's measurement: the first load of the storefront's production wasm bundle — sizes per file, raw,
# gzip and brotli, and the time to the first frame in headless Chromium under three network profiles.
# The measuring itself is scripts/measure-first-load.py; this script builds the two arms and runs it.
#
#   main   — the bundle as `main` builds it. Served as static files, with no server behind it, it draws
#            the storefront's error page, which never goes quiet (B-80): give it STOP_AFTER.
#   wired  — a throwaway copy of the tree whose root renders the Home body of the screenshot fixtures
#            (composeApp/src/desktopTest/resources/bodies/home_content.json) through haulRegistry():
#            every renderer reachable, real text drawn in the three fonts. Nothing of it is committed. The
#            patch swaps the one `App(...)` call in Main.kt for it (WIRED_ROOT below; WiredArmTest holds
#            Main.kt to that shape), and keeps the rest of the entry point, the static frame's removal included.
#
# Neither arm needs the server or PostgreSQL: the page is static files and the wired arm's body is
# compiled in. Outputs go to $OUT (raw runs as JSON lines, a summary per arm, a screenshot per run).
#
# Two more arms measure what the stand sends (B-34), from an image already built
# (`scripts/image-check.sh $IMAGE`); neither is in the default ARMS:
#
#   image           — the image itself, started beside a PostgreSQL on $PORT, sending the `.br`/`.gz`
#                     it carries; its `web/` is copied out for the size table.
#   image-identity  — the same `web/`, served uncompressed by this script: the stand before B-34.
#
#   scripts/measure-first-load.sh [rounds]
#
# GRADLE overrides the Gradle invocation, e.g. to cap memory on a shared box:
#   GRADLE='systemd-run --user --scope -p MemoryMax=5G -p MemorySwapMax=0 ./gradlew --max-workers=2
#           -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.daemon=false'
# ART (B-78) relays the image arm through the measurement's own proxy: `pass` as it is, `png` with the seed's
# pictures rasterised to PNG — the two ways of sending them, compared on the same image.
# ARMS picks the arms (default "main wired"); PROFILES narrows the network profiles (default all three,
# e.g. PROFILES=none,slow4g); CHROME_BIN the Chromium (default: Playwright's); STOP_AFTER ends each run
# that many seconds after the first frame — for the image arms, whose live storefront never goes quiet (B-80).
set -u
cd "$(dirname "$0")/.."
ROUNDS=${1:-7}
OUT=${OUT:-/tmp/b28-first-load}
WORK=${WORK:-/tmp/b28-wired-tree}
GRADLE=${GRADLE:-./gradlew}
ARMS=${ARMS:-main wired}
IMAGE=${IMAGE:-haul/server:check}
PORT=${PORT:-18134}
DIST=composeApp/build/dist/wasmJs/productionExecutable
mkdir -p "$OUT"
status=0

NET=haul-measure-$$
cleanup() { docker rm -f "$NET-app" "$NET-pg" >/dev/null 2>&1; docker network rm "$NET" >/dev/null 2>&1; }
trap cleanup EXIT

stand_up() { # the image beside a PostgreSQL, as scripts/image-check.sh starts it; its web/ into $OUT
  [ -d "$OUT/image-web" ] && return 0
  docker network create "$NET" >/dev/null
  docker run -d --network "$NET" --name "$NET-pg" -e POSTGRES_USER=haul -e POSTGRES_PASSWORD=haul \
    -e POSTGRES_DB=haul postgres:18-alpine >/dev/null
  for _ in $(seq 60); do docker exec "$NET-pg" pg_isready -U haul -d haul >/dev/null 2>&1 && break; sleep 0.5; done
  docker run -d --network "$NET" --name "$NET-app" -p "127.0.0.1:$PORT:8080" \
    -e HAUL_DB_URL="jdbc:postgresql://$NET-pg:5432/haul" -e HAUL_DB_USER=haul -e HAUL_DB_PASSWORD=haul \
    -e HAUL_SEED=true "$IMAGE" >/dev/null
  for _ in $(seq 120); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/readyz")" = 200 ] && break
    sleep 0.5
  done
  docker cp "$NET-app:/opt/app/web" "$OUT/image-web"
  docker image inspect --format '{{.Id}}' "$IMAGE" > "$OUT/image.id"
}

build() { # $1 = tree, $2 = log
  (cd "$1" && $GRADLE :composeApp:wasmJsBrowserDistribution > "$2" 2>&1) || { echo "build failed: $2"; exit 1; }
}

for arm in $ARMS; do
  case $arm in
    main)
      tree=$PWD
      ;;
    wired)
      tree=$WORK
      rm -rf "$tree" && mkdir -p "$tree"
      tar --exclude=./.gradle --exclude=./.kotlin --exclude='*/build' --exclude=./.git -cf - . | tar -xf - -C "$tree"
      python3 - "$tree" <<'EOF'
import json, re, sys
tree = sys.argv[1]
body = open(f"{tree}/composeApp/src/desktopTest/resources/bodies/home_content.json").read()
literal = json.dumps(json.dumps(json.loads(body))).replace("$", "\\$")
pkg = f"{tree}/composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul"
open(f"{pkg}/MeasureWired.kt", "w").write('''package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.youndie.haul.registry.haulJson
import io.github.youndie.haul.registry.haulRegistry
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.rememberHaulFonts
import io.github.youndie.haul.ui.LocalHaulNow
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotScreen
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import kotlinx.serialization.PolymorphicSerializer
import kotlin.time.Instant

// B-28's wired arm, generated by scripts/measure-first-load.sh into a throwaway copy of the tree.

@Composable
internal fun WiredHome() {
    val body = remember { haulJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), HOME_BODY) }
    val registry = remember { haulRegistry() }
    val forms = remember { FormController(FormSchema(formId = "none", fields = emptyList())) }
    HaulThemeAtWidth(rememberHaulFonts(), Modifier.fillMaxSize().background(HaulColors.background)) {
        CompositionLocalProvider(LocalHaulNow provides Instant.parse("2025-10-07T19:47:23-04:00")) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                KompotScreen(body, registry, forms, KompotActionHandler { })
            }
        }
    }
}

private const val HOME_BODY: String = ''' + literal + '''
''')
main = f"{pkg}/Main.kt"
src = open(main).read()
# The storefront's root, called once in Main.kt with plain arguments: App(photos, transport, ...).
WIRED_ROOT = re.compile(r"\bApp\([^()]*\)")
calls = WIRED_ROOT.findall(src)
assert len(calls) == 1, f"Main.kt calls App( {len(calls)} times: update the wired arm's patch"
open(main, "w").write(WIRED_ROOT.sub("WiredHome()", src))
EOF
      ;;
    image | image-identity)
      stand_up
      sha256sum "$OUT/image-web"/*.wasm > "$OUT/$arm-wasm.sha256"
      if [ "$arm" = image ]; then how=(--url "http://127.0.0.1:$PORT/"); else how=(--identity); fi
      [ "$arm" = image ] && [ -n "${ART:-}" ] && how+=(--art "$ART")
      python3 scripts/measure-first-load.py "$OUT/image-web" "$OUT" --rounds "$ROUNDS" --label "$arm" \
        ${PROFILES:+--profiles "$PROFILES"} ${STOP_AFTER:+--stop-after "$STOP_AFTER"} "${how[@]}" > "$OUT/$arm.log" 2>&1 || status=1
      tail -n +1 "$OUT/$arm-summary.md" 2>/dev/null || cat "$OUT/$arm.log"
      continue
      ;;
    *) echo "unknown arm $arm"; exit 2 ;;
  esac
  build "$tree" "$OUT/$arm-build.log"
  sha256sum "$tree/$DIST"/*.wasm > "$OUT/$arm-wasm.sha256"
  python3 scripts/measure-first-load.py "$tree/$DIST" "$OUT" --rounds "$ROUNDS" --label "$arm" \
    ${PROFILES:+--profiles "$PROFILES"} ${STOP_AFTER:+--stop-after "$STOP_AFTER"} > "$OUT/$arm.log" 2>&1 || status=1
  tail -n +1 "$OUT/$arm-summary.md" 2>/dev/null || cat "$OUT/$arm.log"
done
exit $status
