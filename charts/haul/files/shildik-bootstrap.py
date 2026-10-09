"""The stand's realm, made to be what the values say, on every install and upgrade (B-27).

Run by the chart's hook Job (templates/shildik.yaml) against the release's own shildik, through its
management API — the one way in: the deploy account cannot exec into a pod, and the provider's image
is one binary with no shell. Every step converges rather than creates, so a second run changes nothing:

- the realm, closed to strangers (only the password method exists, and its people are made here);
- the storefront's public client with exactly one return page, re-created when it differs (a public
  client has no secret to lose, and the API cannot edit its return pages in place);
- the sample customers, by the ids the seed knows them by, with the password the deploy passes —
  or none of them when it passes none;
- and last, discovery on the provider's public port names the issuer the server is configured with:
  the check that the two halves of the configuration agree, before a shopper finds out they do not.

Nothing secret is printed: the bootstrap token and the password are only ever sent.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

MANAGEMENT = os.environ["SHILDIK_MANAGEMENT"].rstrip("/")
PUBLIC = os.environ["SHILDIK_PUBLIC"].rstrip("/")
ISSUER = os.environ["SHILDIK_ISSUER"].rstrip("/")
REALM = os.environ["SHILDIK_REALM"]
CLIENT = os.environ["SHILDIK_CLIENT_ID"]
REDIRECT = os.environ["SHILDIK_REDIRECT_URI"]
TOKEN = os.environ["SHILDIK_BOOTSTRAP_TOKEN"]
PASSWORD = os.environ.get("DEMO_PASSWORD", "")
PEOPLE = json.loads(os.environ.get("DEMO_PEOPLE") or "[]")
READY_WITHIN = 180


def call(method, url, body=None, admin=True):
    """(status, parsed JSON or text) — a refusal is an answer here, not an exception."""
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    if admin:
        request.add_header("Authorization", "Bearer " + TOKEN)
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            status, text = response.status, response.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode()
    try:
        return status, json.loads(text) if text else None
    except ValueError:
        return status, text


def must(status, answer, expected, what):
    if status not in expected:
        sys.exit(f"{what}: answered {status}: {answer}")


def wait_ready():
    deadline = time.monotonic() + READY_WITHIN
    while time.monotonic() < deadline:
        try:
            if call("GET", MANAGEMENT + "/ready", admin=False)[0] == 200:
                return
        except OSError:
            pass
        time.sleep(2)
    sys.exit(f"shildik's management port did not answer ready within {READY_WITHIN} s")


def realm():
    status, answer = call("POST", MANAGEMENT + "/admin/tenants", {"realm": REALM, "registrationOpen": False})
    must(status, answer, (201, 409), f"creating the realm {REALM}")
    if status == 201:
        closed = isinstance(answer, dict) and answer.get("registrationOpen") is False
        print(f"realm {REALM}: created, {'closed to strangers' if closed else 'OPEN to strangers'}")
    else:
        # Whether an existing realm is closed cannot be read back (the listing does not carry the flag)
        # nor changed; it was created closed by this hook, and an open one admits nobody here anyway —
        # the password method has no sign-up.
        print(f"realm {REALM}: exists")


def client():
    url = f"{MANAGEMENT}/admin/tenants/{REALM}/clients"
    status, clients = call("GET", url)
    must(status, clients, (200,), "listing the clients")
    found = next((c for c in clients if c.get("clientId") == CLIENT), None)
    if found is not None and found.get("public") and set(found.get("redirectUris", [])) == {REDIRECT}:
        print(f"client {CLIENT}: as wanted, returns to {REDIRECT}")
        return
    if found is not None:
        status, answer = call("DELETE", f"{url}/{CLIENT}")
        must(status, answer, (204,), f"deleting the client {CLIENT} that returned to {found.get('redirectUris')}")
    status, answer = call("POST", url, {"clientId": CLIENT, "public": True, "redirectUris": [REDIRECT]})
    must(status, answer, (201,), f"creating the client {CLIENT}")
    print(f"client {CLIENT}: {'re-created' if found else 'created'}, public, returns to {REDIRECT}")


def people():
    if not PASSWORD:
        print("demo people: no password given, none created or changed")
        return
    url = f"{MANAGEMENT}/admin/tenants/{REALM}/users"
    for person in PEOPLE:
        status, answer = call(
            "POST",
            url,
            {"id": person["id"], "email": person["email"], "name": person["name"], "emailVerified": True},
        )
        must(status, answer, (200, 201), f"importing {person['id']}")
        status, answer = call("PUT", f"{url}/{person['id']}/password", {"password": PASSWORD})
        must(status, answer, (204,), f"setting {person['id']}'s password")
        print(f"person {person['id']} ({person['email']}): present, password set")


def discovery():
    expected = f"{ISSUER}/realms/{REALM}"
    status, document = call("GET", f"{PUBLIC}/realms/{REALM}/.well-known/openid-configuration", admin=False)
    must(status, document, (200,), "discovery on the public port")
    if document.get("issuer") != expected:
        sys.exit(f"discovery names the issuer {document.get('issuer')}, the server is configured with {expected}")
    print(f"discovery: issuer {expected}")


if __name__ == "__main__":
    wait_ready()
    realm()
    client()
    people()
    discovery()
