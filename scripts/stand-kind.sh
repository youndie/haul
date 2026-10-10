#!/usr/bin/env bash
# B-27's proof that the chart installs in the stand's shape — the server, its PostgreSQL, the stand's own
# shildik and the hook that makes its realm — on a throwaway kind cluster, deleted afterwards.
#
# What it asks, from a pod inside the namespace (the way the server and the bootstrap hook see things):
# the server is ready and serves the page; `GET /api/v1/sign-in` names the realm's issuer and client;
# discovery answers at that issuer; Maya signs in through the storefront's client by the browser's flow
# (authorization code with PKCE, the provider's form posted) and her account page answers as Maya; the
# hook's script run a second time changes nothing; and the NetworkPolicies keep that pod away from the
# database and from the provider's management port.
#
# What it cannot ask: the public hosts, TLS and Traefik (no ingress here — `traefik.enabled=false`), so
# the issuer is the provider's Service, `http://<release>-shildik:8080`, which the server and the pod both
# reach. On the stand it is `https://<shildik.hostname>`, and the server reaches it through the ingress.
#
# With SHOPPERS=true it also runs the synthetic shoppers (B-31) as the stand would — `shoppers.enabled`, Sam
# signing in with the demo password from the release's Secret — on a fast clock (SPEED, default a day in ten
# seconds) and a short interval, waits for WALKS walks to finish (default 3), and asks the database what they
# left: the orders, the sagas, the returns and refunds. tracy and metrik are not in a kind cluster, so what
# they would show is read from the rows they would be about.
#
#   scripts/stand-kind.sh [image-tag]          SKIP_BUILD=true to use an image already built
#   SHOPPERS=true scripts/stand-kind.sh [image-tag] [shoppers-image-tag]
set -u
cd "$(dirname "$0")/.."
IMAGE=${1:-haul/server:stand-kind}
CLUSTER=${KIND_CLUSTER:-haul-stand-kind}
NS=haul
PY=python:3.13.16-alpine3.24
export KUBECONFIG=${TMPDIR:-/tmp}/$CLUSTER.kubeconfig

SHOPPERS_IMAGE=${2:-haul/shoppers:stand-kind}
if [ "${SKIP_BUILD:-}" != true ]; then scripts/image-build.sh "$IMAGE" || exit 1; fi
if [ "${SHOPPERS:-}" = true ] && [ "${SKIP_BUILD:-}" != true ]; then scripts/shoppers-check.sh "$SHOPPERS_IMAGE" || exit 1; fi

cleanup() { [ "${KEEP:-}" = true ] || kind delete cluster --name "$CLUSTER" >/dev/null 2>&1; rm -f "$KUBECONFIG"; }
trap cleanup EXIT
kind create cluster --name "$CLUSTER" --kubeconfig "$KUBECONFIG" --wait 120s >/dev/null || exit 1
# Only the image under test is loaded; the node pulls the published ones itself, as a cluster does
# (`kind load` of a pulled multi-platform image fails on a Docker that keeps images in containerd).
kind load docker-image --name "$CLUSTER" "$IMAGE" >/dev/null || exit 1
if [ "${SHOPPERS:-}" = true ]; then kind load docker-image --name "$CLUSTER" "$SHOPPERS_IMAGE" >/dev/null || exit 1; fi

secret() { head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'; }
MASTER=$(secret); BOOT=$(secret); DEMO=$(secret)
ISSUER=http://haul-shildik:8080
if [ "${SHOPPERS:-}" = true ]; then SPEED=${SPEED:-8640}; else SPEED=${SPEED:-288}; fi
VALUES=(--set "server.image=${IMAGE%:*}" --set "server.version=${IMAGE##*:}" --set server.seed=true
  --set-string "server.fulfilmentSpeed=$SPEED" --set hostname=haul.example --set traefik.enabled=false
  --set-literal "postgres.password=$(secret)"
  --set shildik.enabled=true --set shildik.hostname=haul-id.example --set "shildik.issuer=$ISSUER"
  --set-literal "shildik.masterKeys=$MASTER" --set-literal "shildik.bootstrapToken=$BOOT"
  --set-literal "shildik.demoPassword=$DEMO")
if [ "${SHOPPERS:-}" = true ]; then
  VALUES+=(--set shoppers.enabled=true --set "shoppers.image=${SHOPPERS_IMAGE%:*}"
    --set "shoppers.version=${SHOPPERS_IMAGE##*:}" --set shoppers.interval=15 --set shoppers.poll=2
    --set shoppers.wait=300)
fi

# Installed, then upgraded with the same values — the second deploy the stand gets on every change. The
# hook's Job is deleted once it succeeds, so its log is followed while helm runs.
for phase in install upgrade; do
  HOOK_LOG=$(mktemp)
  ( until kubectl -n "$NS" logs -f job/haul-shildik-bootstrap 2>/dev/null; do sleep 1; done ) > "$HOOK_LOG" &
  follower=$!
  started=$(date +%s)
  helm upgrade --install haul charts/haul -n "$NS" --create-namespace "${VALUES[@]}" --wait --timeout 6m >/dev/null \
    || { kubectl -n "$NS" get pods; cat "$HOOK_LOG"; kill "$follower"; exit 1; }
  kill "$follower" 2>/dev/null; wait "$follower" 2>/dev/null
  echo "--- $phase: done in $(($(date +%s) - started)) s; the hook said:"; cat "$HOOK_LOG"; rm -f "$HOOK_LOG"
done
kubectl -n "$NS" get pods --no-headers

# A pod in the namespace with the given labels runs a Python script from stdin.
in_pod() { # name, labels, script file, env...
  local name=$1 labels=$2 script=$3; shift 3
  local env=(); for e in "$@"; do env+=(--env "$e"); done
  kubectl -n "$NS" run "$name" -i --rm --quiet --restart=Never --image="$PY" \
    --labels="$labels" "${env[@]}" --command -- python3 -B - < "$script"
}

echo "--- the hook's script, run by hand once more:"
in_pod b27-boot "app.kubernetes.io/instance=haul,app.kubernetes.io/name=shildik-bootstrap" \
  charts/haul/files/shildik-bootstrap.py \
  SHILDIK_MANAGEMENT=http://haul-shildik:9000 SHILDIK_PUBLIC=$ISSUER SHILDIK_ISSUER=$ISSUER SHILDIK_REALM=haul \
  SHILDIK_CLIENT_ID=haul-web SHILDIK_REDIRECT_URI=https://haul.example/signed-in.html \
  "SHILDIK_BOOTSTRAP_TOKEN=$BOOT" "DEMO_PASSWORD=$DEMO" \
  'DEMO_PEOPLE=[{"id":"maya","name":"Maya Kowalski","email":"maya@example.com"},{"id":"sam","name":"Sam Ortiz","email":"sam@example.com"}]' \
  || exit 1

echo "--- the stand, asked from inside the namespace:"
CHECK=$(mktemp)
cat > "$CHECK" <<'PY'
import base64, hashlib, json, os, re, secrets, socket, sys, urllib.parse, urllib.request, urllib.error

SERVER, ISSUER, PASSWORD = "http://haul", os.environ["ISSUER"] + "/realms/haul", os.environ["DEMO"]
REDIRECT = "https://haul.example/signed-in.html"
failed = []

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None

http = urllib.request.build_opener(NoRedirect)

def send(url, data=None, headers={}):
    request = urllib.request.Request(url, data=data, headers=headers)
    try:
        with http.open(request, timeout=10) as r: return r.status, r.headers, r.read().decode()
    except urllib.error.HTTPError as e: return e.code, e.headers, e.read().decode()

def check(name, ok, detail=""):
    print(f"{name:<58} {'ok' if ok else 'FAILED'} {detail}")
    if not ok: failed.append(name)

for path in ("/readyz", "/", "/version"):
    status, _, body = send(SERVER + path)
    check(f"GET {path}", status == 200, body if path == "/version" else "")
status, _, body = send(SERVER + "/api/v1/sign-in")
settings = json.loads(body) if status == 200 else {}
check("GET /api/v1/sign-in names the realm and the client", settings.get("issuer") == ISSUER
      and settings.get("clientId") == "haul-web", f"{status} {settings.get('issuer')} {settings.get('clientId')}")
status, _, body = send(ISSUER + "/.well-known/openid-configuration")
discovery = json.loads(body) if status == 200 else {}
check("discovery at the issuer", discovery.get("issuer") == ISSUER, f"{status}")

# Maya signs in the way the browser does: the provider's page, its form, the code, the exchange.
verifier = secrets.token_urlsafe(32)
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
state = secrets.token_urlsafe(16)
query = urllib.parse.urlencode({"client_id": "haul-web", "redirect_uri": REDIRECT, "response_type": "code",
    "scope": settings.get("scope", "openid"), "state": state, "nonce": secrets.token_urlsafe(16),
    "code_challenge": challenge, "code_challenge_method": "S256"})
status, _, page = send(discovery["authorization_endpoint"] + "?" + query)
action = re.search(r'<form method="post" action="([^"]+)"', page)
parked = re.search(r'name="state" value="([^"]+)"', page)
check("the provider's sign-in page", status == 200 and action is not None and parked is not None, f"{status}")
origin = os.environ["ISSUER"]
form = urllib.parse.urlencode({"state": parked.group(1), "login": "maya@example.com", "password": PASSWORD}).encode()
status, headers, _ = send(origin + action.group(1).replace("&amp;", "&"), form,
    {"Content-Type": "application/x-www-form-urlencoded", "Origin": origin})
location = headers.get("Location", "")
check("Maya's password signs her in, back to the storefront", status == 302 and location.startswith(REDIRECT),
      f"{status}")
code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query).get("code", [""])[0]
status, _, body = send(discovery["token_endpoint"], urllib.parse.urlencode({"grant_type": "authorization_code",
    "code": code, "redirect_uri": REDIRECT, "client_id": "haul-web", "code_verifier": verifier}).encode(),
    {"Content-Type": "application/x-www-form-urlencoded"})
token = json.loads(body).get("access_token", "") if status == 200 else ""
check("the code exchanged for her tokens", bool(token), f"{status}")
status, _, body = send(SERVER + "/ui/account", headers={"Authorization": "Bearer " + token})
check("her account page, as Maya", status == 200 and "Maya" in body, f"{status}")
status, _, _ = send(SERVER + "/ui/account", headers={"Authorization": "Bearer " + token[:-4] + "AAAA"})
check("a forged token is refused", status == 401, f"{status}")

def reachable(host, port):
    try:
        socket.create_connection((host, port), timeout=5).close(); return True
    except OSError:
        return False

check("the provider's public port is open to this pod", reachable("haul-shildik", 8080))
check("the management port is closed to this pod", not reachable("haul-shildik", 9000))
check("the database is closed to this pod", not reachable("haul-postgres", 5432))
sys.exit(1 if failed else 0)
PY
in_pod b27-check "app.kubernetes.io/name=b27-check" "$CHECK" "ISSUER=$ISSUER" "DEMO=$DEMO"; result=$?
rm -f "$CHECK"

if [ "${SHOPPERS:-}" = true ] && [ "$result" = 0 ]; then
  want=${WALKS:-3}
  echo "--- the synthetic shoppers, until $want walks have finished (fulfilment speed $SPEED):"
  deadline=$(($(date +%s) + 600))
  until [ "$(kubectl -n "$NS" logs deploy/haul-shoppers 2>/dev/null | grep -cE '^walk [0-9]+ as ')" -ge "$want" ]; do
    if [ "$(date +%s)" -ge "$deadline" ]; then echo "fewer than $want walks finished within ten minutes"; result=1; break; fi
    sleep 5
  done
  kubectl -n "$NS" logs deploy/haul-shoppers
  failures=$(kubectl -n "$NS" logs deploy/haul-shoppers | grep -c ' failed')
  echo "walks that failed: $failures"; [ "$failures" = 0 ] || result=1
  echo "--- what they left in the database:"
  kubectl -n "$NS" exec deploy/haul-postgres -- psql -U haul -d haul -c "
    SELECT 'orders ' || status AS what, count(*) FROM orders GROUP BY status
    UNION ALL SELECT 'sagas ' || type || ' ' || status, count(*) FROM petiches GROUP BY type, status
    UNION ALL SELECT 'returns', count(*) FROM returns
    UNION ALL SELECT 'refunds', count(*) FROM payment_refunds
    UNION ALL SELECT 'orders of ' || customer_id, count(*) FROM orders GROUP BY customer_id
    ORDER BY 1"
fi
restarts=$(kubectl -n "$NS" get pods -o jsonpath='{range .items[*]}{.status.containerStatuses[*].restartCount}{" "}{end}')
echo "restarts per pod: $restarts"
exit $result
