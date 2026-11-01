"""The chart's realm hook (charts/haul/files/shildik-bootstrap.py) against a management API of its own (B-178).

The stand's upgrade to a client with a second return address — the desktop app's loopback one — is a set
that differs from the one the stand registered, so the hook deletes the client and creates it again. This
runs the hook the way the cluster does, three times over one fake provider that keeps its clients, and
says what each run asked for:

1. the stand as it is: `haul-web` returning to the browser's page alone → one DELETE, one POST with both;
2. the next upgrade, nothing changed → neither: the client is as wanted;
3. a new release with no client yet → one POST, no DELETE.

A fake, not shildik: what is checked is the hook's convergence, which needs no provider. That shildik
accepts the loopback address on any port is the desktop's sign-in test's, against the provider's image.

    python3 scripts/bootstrap-check.py
"""

import json
import os
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOOK = os.path.join(os.path.dirname(__file__), "..", "charts", "haul", "files", "shildik-bootstrap.py")
REALM = "haul"
CLIENT = "haul-web"
WEB = "https://haul.example/signed-in.html"
DESKTOP = "http://127.0.0.1/signed-in"
ISSUER = "https://id.example"

clients = {}
calls = []


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def answer(self, status, body=None):
        data = b"" if body is None else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/ready":
            return self.answer(200)
        if self.path == f"/admin/tenants/{REALM}/clients":
            return self.answer(200, list(clients.values()))
        if self.path == f"/realms/{REALM}/.well-known/openid-configuration":
            return self.answer(200, {"issuer": f"{ISSUER}/realms/{REALM}"})
        self.answer(404)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path == "/admin/tenants":
            return self.answer(409)
        if self.path == f"/admin/tenants/{REALM}/clients":
            calls.append(("POST", sorted(body["redirectUris"])))
            clients[body["clientId"]] = body
            return self.answer(201, body)
        self.answer(404)

    def do_DELETE(self):
        prefix = f"/admin/tenants/{REALM}/clients/"
        if self.path.startswith(prefix):
            calls.append(("DELETE", self.path[len(prefix):]))
            clients.pop(self.path[len(prefix):], None)
            return self.answer(204)
        self.answer(404)


def run(server):
    origin = f"http://127.0.0.1:{server.server_address[1]}"
    env = dict(
        os.environ,
        SHILDIK_MANAGEMENT=origin,
        SHILDIK_PUBLIC=origin,
        SHILDIK_ISSUER=ISSUER,
        SHILDIK_REALM=REALM,
        SHILDIK_CLIENT_ID=CLIENT,
        SHILDIK_REDIRECT_URIS=json.dumps([WEB, DESKTOP]),
        SHILDIK_BOOTSTRAP_TOKEN="check",
    )
    env.pop("DEMO_PASSWORD", None)
    calls.clear()
    result = subprocess.run([sys.executable, "-I", "-B", HOOK], env=env, capture_output=True, text=True, timeout=60)
    if result.returncode != 0:
        sys.exit(f"the hook failed: {result.stdout}{result.stderr}")
    return list(calls)


def main():
    server = ThreadingHTTPServer(("127.0.0.1", 0), Provider)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    failed = False

    def expect(name, got, wanted):
        nonlocal failed
        ok = got == wanted
        failed = failed or not ok
        print(f"{name:<64} {'ok' if ok else f'FAILED — asked for {got}'}")

    clients[CLIENT] = {"clientId": CLIENT, "public": True, "redirectUris": [WEB]}
    both = ("POST", sorted([WEB, DESKTOP]))
    expect("the hook: the stand's client gains the desktop's address, once", run(server), [("DELETE", CLIENT), both])
    expect("the hook: the next upgrade changes nothing", run(server), [])
    clients.clear()
    expect("the hook: a new release's client is created with both", run(server), [both])
    server.shutdown()
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
