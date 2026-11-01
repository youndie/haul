#!/usr/bin/env bash
# B-27's check of the chart, without a cluster: it lints, it renders with the defaults and in the
# stand's shape — every part switched on — asserts what that render must say, and makes every refusal
# it promises fire with its own reason.
#
# A refusal is half the chart's behaviour — a missing image tag, password or host stops the render
# instead of arriving as a green deploy — and a refusal nobody renders is one that has quietly stopped
# refusing. `helm lint` alone proves none of them: it reports a `fail` as [INFO] and passes.
#
# The stand's own values live beside its deploy, not here (B-27, Iteration 2); STAND below is its
# shape with placeholder secrets, and `scripts/stand-kind.sh` installs that shape on a throwaway cluster.
#
#   scripts/chart-check.sh
set -u
cd "$(dirname "$0")/.."
CHART=charts/haul
REQUIRED=(--set server.version=ci --set postgres.password=ci --set hostname=haul.example)
STAND=("${REQUIRED[@]}" --set server.seed=true --set-string server.fulfilmentSpeed=288
  --set shildik.enabled=true --set shildik.hostname=haul-id.example
  --set shildik.masterKeys=ci --set shildik.bootstrapToken=ci --set shildik.demoPassword=ci
  --set observability.metrik.endpoint=metrik:9999 --set observability.metrik.key=ci
  --set observability.tracy.endpoint=http://tracy:8080 --set observability.tracy.key=ci
  --set observability.katcher.endpoint=http://katcher:8080 --set observability.katcher.key=ci
  --set traefik.certResolver=cloudflare)
# The stand's shape with the synthetic shoppers on (B-31): the one value the deploy adds to turn them on.
SHOPPERS=("${STAND[@]}" --set shoppers.enabled=true)
failed=0

say() { printf '%-64s %s\n' "$1" "$2"; }

renders() { # name, args...
  local name=$1; shift
  if out=$(helm template haul "$CHART" "$@" 2>&1); then say "$name" ok; else say "$name" "FAILED"; echo "$out" | tail -5; failed=1; fi
}

refuses() { # name, expected text, args...
  local name=$1 expected=$2; shift 2
  local out; out=$(helm template haul "$CHART" "$@" 2>&1 >/dev/null)
  case "$out" in
    *"$expected"*) say "refuses: $name" ok ;;
    *) say "refuses: $name" "FAILED — rendered, or refused for another reason:"; echo "$out" | tail -3; failed=1 ;;
  esac
}

says() { # name, expected text — in the stand's render
  case "$STAND_RENDER" in
    *"$2"*) say "the stand's render: $1" ok ;;
    *) say "the stand's render: $1" "FAILED — no «$2»"; failed=1 ;;
  esac
}

helm lint "$CHART" "${REQUIRED[@]}" >/dev/null && say lint ok || { say lint FAILED; failed=1; }
helm lint "$CHART" "${STAND[@]}" >/dev/null && say "lint, the stand's shape" ok || { say "lint, the stand's shape" FAILED; failed=1; }

renders "defaults plus the required values" "${REQUIRED[@]}"
renders "defaults with no ingress" --set server.version=ci --set postgres.password=ci --set traefik.enabled=false
renders "the stand's shape" "${STAND[@]}"
renders "the stand's shape, speed as a number" "${STAND[@]}" --set server.fulfilmentSpeed=288
renders "the stand's shape, no sample customers" "${STAND[@]}" --set shildik.demoPassword=
renders "the stand's shape, no network policies" "${STAND[@]}" --set networkPolicy=false
renders "the stand's shape with the shoppers" "${SHOPPERS[@]}"
renders "the shoppers as both demo people, their own tag" "${SHOPPERS[@]}" --set 'shoppers.people={sam,maya}' \
  --set shoppers.version=v9

# B-174: one replica by default, and the stand's shape with two — the count the chart is checked against both ways.
renders "the stand's shape, two replicas" "${STAND[@]}" --set server.replicas=2
replicas_of() { helm template haul "$CHART" "$@" -s templates/server.yaml 2>&1 | grep -E '^  replicas: '; }
[ "$(replicas_of "${STAND[@]}")" = "  replicas: 1" ] && [ "$(replicas_of "${STAND[@]}" --set server.replicas=2)" = "  replicas: 2" ] \
  && say "the server's replicas: 1 by default, 2 when asked" ok \
  || { say "the server's replicas: 1 by default, 2 when asked" FAILED; failed=1; }
renders "the stand's shape with the shoppers, two replicas" "${SHOPPERS[@]}" --set server.replicas=2

STAND_RENDER=$(helm template haul "$CHART" "${STAND[@]}" 2>&1)
says "the server's issuer is the realm's" 'value: "https://haul-id.example/realms/haul"'
says "the server's client" 'value: "haul-web"'
says "the provider's issuer is its host" 'value: "https://haul-id.example"'
says "the client returns to the storefront" 'value: "https://haul.example/signed-in.html"'
says "the speed reaches the server" 'value: "288"'
says "the realm is made by a hook" 'helm.sh/hook: post-install,post-upgrade'
says "the provider has its own host" 'Host(`haul-id.example`)'
policies=$(printf '%s\n' "$STAND_RENDER" | grep -c '^kind: NetworkPolicy')
[ "$policies" = 2 ] && say "the stand's render: two network policies" ok \
  || { say "the stand's render: two network policies" "FAILED — $policies"; failed=1; }
says "the management port admits the hook only" 'app.kubernetes.io/name: shildik-bootstrap'
# B-162: the demo shoppers — off by default (the bootstrap token is the owner's to hand to the server); on, through
# the provider's management port and its bootstrap token.
DEMO=(--set server.demoShoppers=true)
DEMO_RENDER=$(helm template haul "$CHART" "${STAND[@]}" "${DEMO[@]}" 2>&1)
says_demo() { # name, expected text — in the stand's render with the demo on
  case "$DEMO_RENDER" in
    *"$2"*) say "the demo's render: $1" ok ;;
    *) say "the demo's render: $1" "FAILED — no «$2»"; failed=1 ;;
  esac
}
says_demo "the server makes demo shoppers through the management port" 'value: "http://haul-shildik:9000"'
says_demo "the demo's token is the provider's bootstrap token" "$(printf 'name: HAUL_DEMO_MANAGEMENT_TOKEN\n              valueFrom:\n                secretKeyRef:\n                  name: haul-shildik\n                  key: bootstrap-token')"
says_demo "the management port admits the server" "$(printf 'app.kubernetes.io/name: shildik-bootstrap\n        - podSelector:\n            matchLabels:\n              app.kubernetes.io/instance: haul\n              app.kubernetes.io/name: haul-server')"
server_admitted() { helm template haul "$CHART" "${STAND[@]}" "$@" -s templates/networkpolicy.yaml 2>&1 | grep -c 'name: haul-server'; }
if [ "$(server_admitted --set server.demoShoppers=true)" = 2 ] && [ "$(server_admitted)" = 1 ] \
  && ! printf '%s\n' "$STAND_RENDER" | grep -q HAUL_DEMO_; then
  say "no demo by default: no demo variables, no management port for the server" ok
else
  say "no demo by default: no demo variables, no management port for the server" FAILED; failed=1
fi
case "$(helm template haul "$CHART" "${REQUIRED[@]}" --set server.demoShoppers=true 2>&1)" in
  *HAUL_DEMO_*) say "no provider: no demo shoppers" "FAILED — a demo variable is rendered"; failed=1 ;;
  *) say "no provider: no demo shoppers" ok ;;
esac
renders "the stand's shape, a demo limit" "${STAND[@]}" "${DEMO[@]}" --set server.demoDailyLimit=50
refuses "a demo limit that is not a positive number" "server.demoDailyLimit=many is not a positive number" \
  "${STAND[@]}" "${DEMO[@]}" --set server.demoDailyLimit=many
case "$(helm template haul "$CHART" "${STAND[@]}" -s templates/ingress.yaml)" in
  *"port: 9000"*) say "the management port is not routed" "FAILED — an IngressRoute names 9000"; failed=1 ;;
  *) say "the management port is not routed" ok ;;
esac

# The shoppers (B-31): off unless asked, and when on, signed in by reference to the release's Secret only.
case "$STAND_RENDER" in
  *haul-shoppers*) say "the stand's render: no shoppers unless enabled" "FAILED — a shoppers Deployment"; failed=1 ;;
  *) say "the stand's render: no shoppers unless enabled" ok ;;
esac
SHOPPERS_RENDER=$(helm template haul "$CHART" "${SHOPPERS[@]}" -s templates/shoppers.yaml 2>&1)
shoppers_say() { # name, expected text — in the shoppers' render
  case "$SHOPPERS_RENDER" in
    *"$2"*) say "the shoppers' render: $1" ok ;;
    *) say "the shoppers' render: $1" "FAILED — no «$2»"; failed=1 ;;
  esac
}
shoppers_say "their image is the server's tag" 'image: "ghcr.io/youndie/haul-shoppers:ci"'
shoppers_say "they walk the server's Service" 'value: "http://haul"'
shoppers_say "they sign in as Sam by default" 'value: "sam@example.com"'
shoppers_say "they return to the storefront's page" 'value: "https://haul.example/signed-in.html"'
shoppers_say "the password is the Secret's demo password" "$(printf 'name: haul-shildik\n                  key: demo-password')"
# B-89: one walk in ten pays with the declining card, with a key the release derives and hands over by reference.
shoppers_say "one walk in ten declines by default" "$(printf 'name: HAUL_SHOPPERS_DECLINE_EVERY\n              value: \"10\"')"
shoppers_say "the test card's key is the Secret's" "$(printf 'name: HAUL_SHOPPERS_TEST_CARD_KEY\n              valueFrom:\n                secretKeyRef:\n                  name: haul-secrets\n                  key: test-card-key')"
SHOPPERS_SERVER=$(helm template haul "$CHART" "${SHOPPERS[@]}" -s templates/server.yaml -s templates/secret.yaml 2>&1)
case "$SHOPPERS_SERVER" in
  *"name: HAUL_TEST_CARD_KEY"*"key: test-card-key"*"test-card-key: \""*) say "the shoppers' render: the server holds the same key" ok ;;
  *) say "the shoppers' render: the server holds the same key" "FAILED"; failed=1 ;;
esac
key_of() { helm template haul "$CHART" "$@" -s templates/secret.yaml 2>&1 | grep 'test-card-key:'; }
[ -n "$(key_of "${SHOPPERS[@]}")" ] && [ "$(key_of "${SHOPPERS[@]}")" = "$(key_of "${SHOPPERS[@]}")" ] \
  && [ "$(key_of "${SHOPPERS[@]}")" != "$(key_of "${SHOPPERS[@]}" --set shildik.masterKeys=other)" ] \
  && say "the test card's key: stable, and the master keys' own" ok \
  || { say "the test card's key: stable, and the master keys' own" FAILED; failed=1; }
case "$(printf '%s\n' "$SHOPPERS_SERVER" | grep 'test-card-key:')" in
  *'"ci"'*) say "the test card's key is not the master keys" "FAILED"; failed=1 ;;
  *) say "the test card's key is not the master keys" ok ;;
esac
case "$(helm template haul "$CHART" "${STAND[@]}" -s templates/server.yaml -s templates/secret.yaml 2>&1)" in
  *HAUL_TEST_CARD_KEY*|*"key: test-card-key"*|*"  test-card-key: "*) say "no shoppers: the test card is nobody's" "FAILED — a key is rendered"; failed=1 ;;
  *) say "no shoppers: the test card is nobody's" ok ;;
esac
NONE_DECLINE=$(helm template haul "$CHART" "${SHOPPERS[@]}" --set shoppers.declineEvery=0 2>&1)
case "$NONE_DECLINE" in
  *HAUL_TEST_CARD_KEY*|*"key: test-card-key"*|*"  test-card-key: "*) say "no declining walks: the test card is nobody's" "FAILED — a key is rendered"; failed=1 ;;
  *'value: "0"'*) say "no declining walks: the test card is nobody's" ok ;;
  *) say "no declining walks: the test card is nobody's" "FAILED — no render"; echo "$NONE_DECLINE" | tail -3; failed=1 ;;
esac
password_value=$(printf '%s\n' "$SHOPPERS_RENDER" | grep -A1 'name: HAUL_SHOPPERS_PASSWORD' | tail -1)
case "$password_value" in
  *valueFrom:*) say "the shoppers' render: no literal password" ok ;;
  *) say "the shoppers' render: no literal password" "FAILED — «$password_value»"; failed=1 ;;
esac

refuses "no image tag" "server.version is required" --set postgres.password=ci --set hostname=ci.example
refuses "no database password" "postgres.password is required" --set server.version=ci --set hostname=ci.example
refuses "no host with the ingress on" "hostname is required" --set server.version=ci --set postgres.password=ci
refuses "an endpoint without its key" "observability.metrik.key is empty" "${REQUIRED[@]}" \
  --set observability.metrik.endpoint=metrik:9999
refuses "a key without its endpoint" "observability.katcher.endpoint is empty" "${REQUIRED[@]}" \
  --set observability.katcher.key=ci
refuses "the stand without an agent's key" "observability.tracy.key is empty" "${STAND[@]}" \
  --set observability.tracy.key=
refuses "no replica at all" "server.replicas is 0 and must be at least 1" "${REQUIRED[@]}" --set server.replicas=0
refuses "more replicas than the database serves" "needs 104 connections during a rollout" "${REQUIRED[@]}" \
  --set server.replicas=7
refuses "a speed that is not a number" "server.fulfilmentSpeed=fast is not a positive number" "${REQUIRED[@]}" \
  --set server.fulfilmentSpeed=fast
refuses "a speed of zero" "server.fulfilmentSpeed=0 is not a positive number" "${REQUIRED[@]}" \
  --set-string server.fulfilmentSpeed=0
refuses "the provider without its master keys" "shildik.masterKeys is required" "${STAND[@]}" --set shildik.masterKeys=
refuses "the provider without its bootstrap token" "shildik.bootstrapToken is required" "${STAND[@]}" \
  --set shildik.bootstrapToken=
refuses "the provider without its host" "shildik.hostname is required" "${STAND[@]}" --set shildik.hostname=
refuses "the provider without the storefront's host" "registers https://<hostname>/signed-in.html" "${STAND[@]}" \
  --set hostname= --set traefik.enabled=false

refuses "shoppers without the provider" "shoppers.enabled needs shildik.enabled" "${REQUIRED[@]}" \
  --set shoppers.enabled=true
refuses "shoppers without the demo password" "shoppers.enabled needs shildik.demoPassword" "${SHOPPERS[@]}" \
  --set shildik.demoPassword=
refuses "shoppers as somebody not in the realm" "shoppers.people names erin" "${SHOPPERS[@]}" \
  --set 'shoppers.people={sam,erin}'
refuses "shoppers as nobody" "shoppers.people is empty" "${SHOPPERS[@]}" --set 'shoppers.people=null'
refuses "shoppers at a rate of zero" "shoppers.interval=0 is not a positive whole number" "${SHOPPERS[@]}" \
  --set shoppers.interval=0
refuses "a share of declining walks that is not a whole number" "shoppers.declineEvery=often is not a whole number" \
  "${SHOPPERS[@]}" --set shoppers.declineEvery=often

# The hook's script is rendered into a ConfigMap and first run in a cluster: a syntax error would
# surface there, as a failed release. Compiled here instead, where one costs a second.
if command -v python3 >/dev/null; then
  if python3 -c 'import ast,sys; ast.parse(open(sys.argv[1]).read())' "$CHART/files/shildik-bootstrap.py"; then
    say "the bootstrap script parses" ok
  else say "the bootstrap script parses" FAILED; failed=1; fi
fi

exit $failed
