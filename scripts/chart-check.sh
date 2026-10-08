#!/usr/bin/env bash
# B-27's check of the chart, without a cluster: it lints, it renders with the defaults and with the
# stand's values, and every refusal it promises fires with its own reason.
#
# A refusal is half the chart's behaviour — a missing image tag, password or host stops the render
# instead of arriving as a green deploy — and a refusal nobody renders is one that has quietly stopped
# refusing. `helm lint` alone proves none of them: it reports a `fail` as [INFO] and passes.
#
#   scripts/chart-check.sh
set -u
cd "$(dirname "$0")/.."
CHART=charts/haul
STAND="-f $CHART/values-stand.yaml"
KEYS="--set observability.tracy.key=ci --set observability.katcher.key=ci"
failed=0

say() { printf '%-58s %s\n' "$1" "$2"; }

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

helm lint "$CHART" --set server.version=ci --set postgres.password=ci --set hostname=ci.example >/dev/null \
  && say lint ok || { say lint FAILED; failed=1; }

renders "defaults plus the required values" --set server.version=ci --set postgres.password=ci --set hostname=ci.example
renders "defaults with no ingress" --set server.version=ci --set postgres.password=ci --set traefik.enabled=false
# shellcheck disable=SC2086
renders "the stand's values" $STAND --set server.version=ci --set postgres.password=ci $KEYS

refuses "no image tag" "server.version is required" --set postgres.password=ci --set hostname=ci.example
refuses "no database password" "postgres.password is required" --set server.version=ci --set hostname=ci.example
refuses "no host with the ingress on" "hostname is required" --set server.version=ci --set postgres.password=ci
refuses "an endpoint without its key" "observability.metrik.key is empty" --set server.version=ci \
  --set postgres.password=ci --set hostname=ci.example --set observability.metrik.endpoint=metrik:9999
refuses "a key without its endpoint" "observability.katcher.endpoint is empty" --set server.version=ci \
  --set postgres.password=ci --set hostname=ci.example --set observability.katcher.key=ci
# shellcheck disable=SC2086
refuses "the stand without its agent keys" "observability.tracy.key is empty" $STAND --set server.version=ci \
  --set postgres.password=ci

exit $failed
