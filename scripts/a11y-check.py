#!/usr/bin/env python3
"""B-126's check: the browser's accessible mirror of the storefront follows the page after a popup, and every
button in it has a name.

Compose draws the page into a canvas and mirrors its semantics into the DOM for assistive technology: `div`s under
`#cmp_a11y_root` in the viewport's shadow root, a `role="button"` for each press, named by its `aria-label` (a
content description) or its text. Standard library only; the DevTools client and the Chromium launch are B-28's and
B-112's (scripts/measure-first-load.py, scripts/first-press-check.py). In one headless Chromium it

1. signs in, when --login names a shopper: the sign-in page (B-66), the provider's form, back to the store;
2. for every popup — «Catalog», the sort, the search field's picker, the account menu at 1440; the menu and the
   filter sheet at 390 — loads its page, presses the control that opens it (found in the mirror by its name),
   waits until the popup's nodes are in the mirror, closes it (Escape, a press off it, or its «×»), then follows a
   link of the page (pressed in the mirror too) and waits: the mirror must hold the new page — the header's
   controls, words that were not there before — and none of the popup's nodes;
3. on every page it loaded, reads the mirror and Chromium's accessibility tree (`Accessibility.getFullAXTree`) for
   buttons with no name.

    scripts/a11y-check.py URL OUT_DIR [--login EMAIL]     (the password from HAUL_A11Y_PASSWORD)

The account menu needs --login; without it that popup is skipped and says so. One JSON line per step in
OUT_DIR/steps.jsonl, a screenshot of every step that failed, the verdicts on stdout. The exit status is 0 only when
every popup's mirror followed the page and no button was unnamed. CHROME_BIN names the Chromium (default:
Playwright's).
"""

import argparse
import base64
import importlib.util
import json
import os
import shutil
import sys
import time
import urllib.request

_spec = importlib.util.spec_from_file_location(
    "first_press_check", os.path.join(os.path.dirname(os.path.abspath(__file__)), "first-press-check.py"))
_m = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_m)

WIDE = (1440, 900)
PHONE = (390, 844)

# The mirror's nodes: the id (a test tag), the role, the label, the node's own text, the centre.
MIRROR = r"""
(() => {
  const host = [...document.querySelectorAll('body *')].find(e => e.shadowRoot);
  const root = host && host.shadowRoot && host.shadowRoot.getElementById('cmp_a11y_root');
  if (!root) return JSON.stringify(null);
  return JSON.stringify([...root.querySelectorAll('div')].map(d => {
    const r = d.getBoundingClientRect();
    const own = [...d.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent).join('').trim();
    return { id: d.id, role: d.getAttribute('role'), label: (d.getAttribute('aria-label') || '').trim(),
             text: own, all: (d.innerText || '').trim(), x: r.x + r.width / 2, y: r.y + r.height / 2,
             w: r.width, h: r.height };
  }));
})()
"""

# The popups: where, at which width, the control that opens it, what in the mirror says it is open, how it is
# closed, and the link followed after.
POPUPS = [
    {"name": "catalog", "path": "/", "size": WIDE, "open": "Catalog", "shown": {"id": "link-menu"},
     "close": "escape", "follow": "Electronics"},
    {"name": "sort", "path": "/c/electronics", "size": WIDE, "open": {"prefix": "Sort:"}, "shown": {"id": "link-menu"},
     "close": "outside", "follow": "Haul"},
    {"name": "search picker", "path": "/", "size": WIDE, "open": {"id": "scope-picker"},
     "shown": {"id": "link-menu"}, "close": "escape", "follow": "Home & Kitchen"},
    {"name": "account menu", "path": "/", "size": WIDE, "open": "ACCOUNT", "shown": {"id": "link-menu"},
     "close": "escape", "follow": "Electronics", "signed_in": True},
    {"name": "phone menu", "path": "/", "size": PHONE, "open": "Menu", "shown": {"id": "header-menu"},
     "close": "escape", "follow": "Electronics"},
    {"name": "filter sheet", "path": "/c/electronics", "size": PHONE, "open": "Filters",
     "shown": {"label": "Close filters"}, "close": {"press": "Close filters"}, "follow": "Haul"},
]

# The pages read for unnamed buttons, beside the popups' own; the signed-in ones only with --login.
PAGES = [
    ("/", False), ("/c/electronics", False), ("/search?q=mug", False), ("/p/p-sony-wh-1000xm6", False),
    ("/cart", False), ("/account", True), ("/account/orders", True), ("/account/saved", True), ("/checkout", True),
]

# The SKU put in the signed-in shopper's cart, so that checkout draws (an empty one leads to the cart, B-94).
CART_SKU = _m.CART_SKU


def mirror(cdp):
    raw = cdp.call("Runtime.evaluate", expression=MIRROR, returnByValue=True)["result"]["value"]
    return json.loads(raw) or []


def matches(node, want):
    if isinstance(want, str):
        return node["label"] == want or node["text"] == want or node["all"] == want
    if "id" in want:
        return node["id"] == want["id"]
    if "label" in want:
        return node["label"] == want["label"]
    if "prefix" in want:
        return node["all"].startswith(want["prefix"]) and node["role"] == "button"
    return False


def find(cdp, want, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        found = [n for n in mirror(cdp) if matches(n, want) and n["w"] > 0]
        if found:
            return found[0]
        time.sleep(0.2)
    return None


def unnamed(cdp):
    """Buttons with no name, in the mirror and in Chromium's own accessibility tree."""
    nodes = mirror(cdp)
    in_mirror = [n for n in nodes if n["role"] == "button" and not n["label"] and not n["all"]]
    tree = cdp.call("Accessibility.getFullAXTree")["nodes"]
    in_tree = [n for n in tree if n.get("role", {}).get("value") == "button" and not n.get("ignored")
               and not (n.get("name", {}).get("value") or "").strip()]
    return [{k: n[k] for k in ("id", "x", "y", "w", "h")} for n in in_mirror], len(in_tree)


def size(cdp, wh):
    cdp.call("Emulation.setDeviceMetricsOverride", width=wh[0], height=wh[1], deviceScaleFactor=1, mobile=False)


def load(cdp, base, path):
    cdp.call("Page.navigate", url=base + path)
    # The page is drawn when the header's «Haul» is in the mirror and the static frame (B-80) is gone.
    deadline = time.time() + 60
    while time.time() < deadline:
        frame = cdp.call("Runtime.evaluate", expression="!!document.getElementById('haul-frame')",
                         returnByValue=True)["result"]["value"]
        if not frame and find(cdp, "Haul", 1):
            time.sleep(1.5)
            return True
        time.sleep(0.2)
    return False


def key(cdp, name, code, vk):
    for kind in ("keyDown", "keyUp"):
        cdp.call("Input.dispatchKeyEvent", type=kind, key=name, code=code, windowsVirtualKeyCode=vk,
                 nativeVirtualKeyCode=vk)


def press(cdp, node):
    _m.click(cdp, node["x"], node["y"])


def texts(nodes):
    return {n["all"] or n["label"] for n in nodes if n["all"] or n["label"]}


def sign_in(cdp, base, login, password):
    size(cdp, WIDE)
    if not load(cdp, base, "/sign-in"):
        return "the sign-in page never drew"
    buttons = [n for n in mirror(cdp) if n["role"] == "button" and n["all"] == "Sign in"]
    if not buttons:
        return "no «Sign in» on the sign-in page"
    press(cdp, buttons[-1])
    # The provider's form opens in a popup window (B-46): a page of its own to drive.
    port = cdp.sock.getpeername()[1]
    form = None
    deadline = time.time() + 30
    while time.time() < deadline and form is None:
        pages = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list"))
        form = next((p for p in pages if p["type"] == "page" and not p["url"].startswith(base)
                     and p["url"].startswith("http")), None)
        time.sleep(0.3)
    if form is None:
        return "the provider's form never opened"
    popup = _m._m.Cdp(form["webSocketDebuggerUrl"])
    popup.call("Runtime.enable")
    fill = f"""(() => {{
      const pass = document.querySelector('input[type=password]');
      if (!pass) return false;
      const user = pass.form.querySelector('input[type=email], input[name=username], input[name=email], input[type=text]');
      user.value = {json.dumps(login)}; pass.value = {json.dumps(password)};
      pass.form.submit();
      return true;
    }})()"""
    deadline = time.time() + 30
    while time.time() < deadline:
        try:
            if popup.call("Runtime.evaluate", expression=fill, returnByValue=True)["result"].get("value"):
                break
        except RuntimeError:
            pass
        time.sleep(0.5)
    else:
        return "the provider's form never showed a password field"
    # Signed in when the tab holds the tokens the popup handed it.
    deadline = time.time() + 60
    while time.time() < deadline:
        tokens = cdp.call("Runtime.evaluate", expression="sessionStorage.getItem('haul.tokens')",
                          returnByValue=True)["result"].get("value")
        if tokens:
            return None
        time.sleep(0.5)
    return "never came back signed in"


def walk_popup(cdp, base, popup, account, out, log):
    step = {"popup": popup["name"], "width": popup["size"][0]}
    size(cdp, popup["size"])
    if not load(cdp, base, popup["path"]):
        return {**step, "error": "the page never drew"}
    control = find(cdp, account if popup["open"] == "ACCOUNT" else popup["open"])
    if not control:
        return {**step, "error": f"no control «{popup['open']}» in the mirror"}
    before = texts(mirror(cdp))
    press(cdp, control)
    if not find(cdp, popup["shown"], 10):
        return {**step, "error": "the popup's nodes never showed in the mirror"}
    step["open"] = sorted(texts(mirror(cdp)))[:12]
    close = popup["close"]
    if close == "escape":
        key(cdp, "Escape", "Escape", 27)
    elif close == "outside":
        _m.click(cdp, 8, popup["size"][1] - 8)
    else:
        pressed = find(cdp, close["press"], 5)
        if not pressed:
            return {**step, "error": f"no «{close['press']}» to close it"}
        press(cdp, pressed)
    time.sleep(1.0)
    link = find(cdp, popup["follow"], 10)
    if not link:
        step["after_close"] = sorted(texts(mirror(cdp)))[:12]
        return {**step, "error": f"after closing, no «{popup['follow']}» in the mirror to follow"}
    at = cdp.call("Runtime.evaluate", expression="location.pathname + location.search", returnByValue=True)
    press(cdp, link)
    deadline = time.time() + 20
    while time.time() < deadline:
        now = cdp.call("Runtime.evaluate", expression="location.pathname + location.search",
                       returnByValue=True)["result"]["value"]
        if now != at["result"]["value"]:
            break
        time.sleep(0.2)
    time.sleep(2.0)
    after = mirror(cdp)
    shown_after = [n for n in after if matches(n, popup["shown"])]
    new = texts(after) - before
    step.update({"followed_to": cdp.call("Runtime.evaluate", expression="location.pathname + location.search",
                                         returnByValue=True)["result"]["value"],
                 "mirror_nodes": len(after), "new_words": sorted(new)[:8],
                 "popup_left": len(shown_after), "header": bool(find(cdp, "Haul", 1))})
    step["ok"] = step["header"] and not shown_after and len(new) > 0 and step["followed_to"] != at["result"]["value"]
    names, tree = unnamed(cdp)
    step.update({"unnamed": names, "unnamed_in_ax_tree": tree})
    if not step["ok"] or names or tree:
        shot(cdp, out, f"popup-{popup['name'].replace(' ', '-')}-{popup['size'][0]}")
    return step


def shot(cdp, out, name):
    png = cdp.call("Page.captureScreenshot", format="png")["data"]
    with open(os.path.join(out, name + ".png"), "wb") as f:
        f.write(base64.b64decode(png))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("url")
    ap.add_argument("out")
    ap.add_argument("--login")
    args = ap.parse_args()
    base = args.url.rstrip("/")
    shutil.rmtree(args.out, ignore_errors=True)
    os.makedirs(args.out)
    proc, profile, cdp = _m.launch(_m.chromium())
    steps = []
    try:
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        cdp.call("Accessibility.enable")
        account = None
        if args.login:
            failed = sign_in(cdp, base, args.login, os.environ.get("HAUL_A11Y_PASSWORD", ""))
            steps.append({"sign_in": failed or "ok"})
            if failed:
                print("sign-in:", failed)
                return 1
            load(cdp, base, "/")
            # The account's control is named by the customer's name: the header's own, read off the wire.
            header = json.loads(cdp.call("Runtime.evaluate", awaitPromise=True, returnByValue=True, expression=(
                "fetch('/ui/home', {headers: {Authorization: 'Bearer ' + "
                "JSON.parse(sessionStorage.getItem('haul.tokens')).accessToken}}).then(r => r.text())"))
                ["result"]["value"])
            account = header["children"][0].get("customerName")
            cdp.call("Runtime.evaluate", awaitPromise=True, returnByValue=True, expression=(
                f"fetch('/api/v1/cart/lines/{CART_SKU}', {{method: 'PUT', headers: {{'Content-Type': "
                "'application/json', Authorization: 'Bearer ' + JSON.parse(sessionStorage.getItem('haul.tokens'))"
                ".accessToken}, body: JSON.stringify({quantity: 1})}).then(r => r.status)"))
        for popup in POPUPS:
            if popup.get("signed_in") and not account:
                steps.append({"popup": popup["name"], "skipped": "needs --login"})
                continue
            steps.append(walk_popup(cdp, base, popup, account, args.out, steps))
        for path, signed_in in PAGES:
            if signed_in and not account:
                continue
            for wh in (WIDE, PHONE):
                size(cdp, wh)
                drawn = load(cdp, base, path)
                names, tree = unnamed(cdp) if drawn else ([], 0)
                at = cdp.call("Runtime.evaluate", expression="location.pathname + location.search",
                              returnByValue=True)["result"]["value"]
                step = {"page": path, "at": at, "width": wh[0], "drawn": drawn, "unnamed": names,
                        "unnamed_in_ax_tree": tree, "buttons": len([n for n in mirror(cdp) if n["role"] == "button"])}
                if not drawn or names or tree:
                    shot(cdp, args.out, f"page-{path.strip('/').replace('/', '-').replace('?', '-') or 'home'}-{wh[0]}")
                steps.append(step)
    finally:
        proc.kill()
        shutil.rmtree(profile, ignore_errors=True)
    with open(os.path.join(args.out, "steps.jsonl"), "w") as f:
        for s in steps:
            f.write(json.dumps(s) + "\n")
    bad = 0
    for s in steps:
        if "popup" in s:
            if "skipped" in s:
                verdict = "skipped: " + s["skipped"]
            elif "error" in s:
                verdict, bad = "FAIL: " + s["error"], bad + 1
            else:
                fine = s["ok"] and not s["unnamed"] and not s["unnamed_in_ax_tree"]
                bad += 0 if fine else 1
                verdict = ("ok" if s["ok"] else "FAIL: the mirror did not follow the page") + \
                    f" — {s['mirror_nodes']} nodes on {s['followed_to']}, popup nodes left {s['popup_left']}, " \
                    f"unnamed {len(s['unnamed'])}/{s['unnamed_in_ax_tree']}"
            print(f"popup {s['popup']} @{s.get('width', '')}: {verdict}")
        elif "page" in s:
            fine = s["drawn"] and not s["unnamed"] and not s["unnamed_in_ax_tree"]
            bad += 0 if fine else 1
            print(f"page {s['page']} @{s['width']} (drawn at {s['at']}, {s['buttons']} buttons): " + ("ok" if fine else
                  f"FAIL: drawn={s['drawn']} unnamed {s['unnamed']} (ax tree {s['unnamed_in_ax_tree']})"))
    print("VERDICT:", "ok" if bad == 0 else f"{bad} failing")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
