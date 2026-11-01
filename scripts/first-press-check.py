#!/usr/bin/env python3
"""B-112's check: on a freshly loaded page, the first press on a text field focuses it and the keys typed next land.

Standard library only (the WSL box has no node and no pip packages); the DevTools client and the Chromium
launch are B-28's (scripts/measure-first-load.py). For every field and every load it

1. starts a fresh headless Chromium with a fresh profile (nothing of an earlier load is in it) and, for the
   promo code, puts the id of a guest whose cart was made over HTTP into `localStorage` before the page runs;
2. loads the field's page and waits until the field's node is in the tree Compose mirrors into the DOM for
   accessibility (the field's test tag is the node's id) and the static frame (B-80) is gone — the field is
   laid out and its place known — then --settle seconds more;
3. presses the field once, as a mouse does: a move to its centre, a press, a release --hold seconds later;
4. after --wait seconds reads whether Compose made its hidden input (`.compose-backing-field` in the
   viewport's shadow root) and whether that input has the DOM focus: «focused»;
5. types --text as key presses and, after --land seconds, reads the field's text from the same node:
   «landed» is the typed text in the field.

One JSON line per load in OUT_DIR/runs.jsonl (with the pointer and focus events the page saw), a screenshot of
every load that failed, and a table per field. The exit status is 0 only when every load of every field
focused and landed.

    scripts/first-press-check.py URL OUT_DIR [--loads 10] [--fields search,price,promo]
                                 [--settle 1.0] [--wait 0.6] [--land 0.6] [--text 123]

The fields: `search`, the header's search field on the home page; `price`, the price «from» on a category
page; `promo`, the cart's promo code, a guest cart with one line made per load. A dialog's text box is not
among them: the dialogs are a customer's, so it needs a signed-in browser, and its first press always follows
the press that opened the dialog.

The conditions B-83 saw the failure in (the app's browser pane, hidden), one at a time:

    --no-move          no move before the press: the pane's click is pointerover + pointerdown, nothing else
    --hold S           seconds between press and release (the pane's: about 0.01)
    --blurred          another window takes the focus before the load: the page loads without it
    --frames-every S   the page's animation frames held back to one every S seconds, as a hidden page gets them
    --hidden           the page loads in a background tab, which draws nothing; a screenshot every second
                       makes it draw a frame, as an agent looking at it does
    --offset PX        the control: the press lands PX to the left of the field's centre (300 misses it), so
                       the check is seen to fail when the press does not reach the field

CHROME_BIN names the Chromium (default: Playwright's under ~/.cache/ms-playwright).
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
    "measure_first_load", os.path.join(os.path.dirname(os.path.abspath(__file__)), "measure-first-load.py"))
_m = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_m)
chromium, launch = _m.chromium, _m.launch

# The SKU a promo load puts in its cart: the seed's first product (server/.../seed).
CART_SKU = "p-sonora-sh-718n-0"

FIELDS = {
    "search": {"path": "/", "tag": "search-field"},
    "price": {"path": "/c/electronics", "tag": "price-from"},
    "promo": {"path": "/cart", "tag": "cart-promo-input", "cart": True},
}

# Holds the page's animation frames back to one every INTERVAL ms, as a hidden or background page gets them.
THROTTLE = r"""
(() => {
  const raf = window.requestAnimationFrame.bind(window);
  let queue = new Map(), next = 1;
  window.requestAnimationFrame = cb => { const id = next++; queue.set(id, cb); return id; };
  window.cancelAnimationFrame = id => { queue.delete(id); };
  setInterval(() => { const due = queue; queue = new Map(); raf(t => due.forEach(cb => cb(t))); }, INTERVAL);
})();
"""

# The pointer events the page saw, in order, for the record of a failed load.
EVENT_LOG = r"""
window.__events = [];
for (const t of ['pointerover', 'pointerenter', 'pointermove', 'pointerdown', 'pointerup', 'focus', 'blur']) {
  window.addEventListener(t, e => window.__events.push(t + '@' + Math.round(e.timeStamp)), true);
}
"""

# Reads the viewport's shadow root: Compose's hidden input, what has the focus, and a node of the mirrored tree.
PROBE = r"""
(tag) => {
  const host = [...document.querySelectorAll('body *')].find(e => e.shadowRoot);
  const root = host && host.shadowRoot;
  const node = root && root.getElementById(tag);
  const backing = root && root.querySelector('.compose-backing-field');
  const r = node && node.getBoundingClientRect();
  return JSON.stringify({
    mounted: !!root,
    hasFocus: document.hasFocus(),
    frame: !!document.getElementById('haul-frame'),
    node: node ? { x: r.x, y: r.y, w: r.width, h: r.height, text: node.innerText } : null,
    backing: !!backing,
    backingFocused: !!backing && root.activeElement === backing,
    active: root && root.activeElement ? root.activeElement.tagName + '.' + root.activeElement.className
      : document.activeElement ? document.activeElement.tagName : null,
  });
}
"""


def http(method, url, body=None, headers=None):
    req = urllib.request.Request(url, method=method, data=body and json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", **(headers or {})})
    with urllib.request.urlopen(req, timeout=10) as r:
        return r.status, r.read().decode()


def guest_with_cart(base):
    """A guest whose cart has one line, made over HTTP; its id is what the page finds in `localStorage`."""
    status, body = http("POST", f"{base}/api/v1/guests")
    assert status == 201, (status, body)
    guest = json.loads(body)["id"]
    status, body = http("PUT", f"{base}/api/v1/cart/lines/{CART_SKU}", {"quantity": 1}, {"X-Haul-Guest": guest})
    assert status == 200, (status, body[:300])
    return guest


def probe(cdp, tag):
    r = cdp.call("Runtime.evaluate", expression=f"({PROBE})({json.dumps(tag)})", returnByValue=True)
    return json.loads(r["result"]["value"])


def wait_for(cdp, tag, timeout, shoot=False):
    deadline = time.time() + timeout
    while time.time() < deadline:
        p = probe(cdp, tag)
        if p["node"] and p["node"]["w"] > 0 and not p["frame"]:
            return p
        if shoot:
            # A hidden page draws nothing on its own; a screenshot makes it draw one frame, as an agent looking does.
            cdp.call("Page.captureScreenshot", format="png")
            time.sleep(1.0)
        time.sleep(0.1)
    return None


def click(cdp, x, y, move=True, hold=0.05):
    if move:
        cdp.call("Input.dispatchMouseEvent", type="mouseMoved", x=x, y=y, button="none", pointerType="mouse")
    cdp.call("Input.dispatchMouseEvent", type="mousePressed", x=x, y=y, button="left", buttons=1, clickCount=1,
             pointerType="mouse")
    time.sleep(hold)
    cdp.call("Input.dispatchMouseEvent", type="mouseReleased", x=x, y=y, button="left", buttons=0, clickCount=1,
             pointerType="mouse")


def centre(node):
    return node["x"] + node["w"] / 2, node["y"] + node["h"] / 2


def type_text(cdp, text):
    for ch in text:
        code = f"Digit{ch}" if ch.isdigit() else f"Key{ch.upper()}"
        vk = ord(ch.upper())
        cdp.call("Input.dispatchKeyEvent", type="keyDown", key=ch, code=code, text=ch, unmodifiedText=ch,
                 windowsVirtualKeyCode=vk, nativeVirtualKeyCode=vk)
        cdp.call("Input.dispatchKeyEvent", type="keyUp", key=ch, code=code, windowsVirtualKeyCode=vk,
                 nativeVirtualKeyCode=vk)
        time.sleep(0.03)


def run_once(chrome, base, name, args, out, n):
    field = FIELDS[name]
    guest = guest_with_cart(base) if field.get("cart") else None
    proc, profile, cdp = launch(chrome)
    result = {"field": name, "load": n}
    try:
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        cdp.call("Page.addScriptToEvaluateOnNewDocument", source=EVENT_LOG)
        if args.frames_every:
            cdp.call("Page.addScriptToEvaluateOnNewDocument",
                     source=THROTTLE.replace("INTERVAL", str(int(args.frames_every * 1000))))
        if guest:
            cdp.call("Page.addScriptToEvaluateOnNewDocument",
                     source=f"localStorage.setItem('haul.guest', {json.dumps(guest)})")
        if args.blurred:
            cdp.call("Target.createTarget", url="about:blank", newWindow=True)
        started = time.time()
        cdp.call("Page.navigate", url=base + field["path"])
        if args.hidden:
            port = cdp.sock.getpeername()[1]
            other = json.loads(urllib.request.urlopen(urllib.request.Request(
                f"http://127.0.0.1:{port}/json/new?about:blank", method="PUT")).read())["id"]
            urllib.request.urlopen(f"http://127.0.0.1:{port}/json/activate/{other}").read()
        shown = wait_for(cdp, field["tag"], args.timeout, shoot=args.hidden)
        if not shown:
            result["error"] = f"«{field['tag']}» never shown"
            return result
        result["shownAfter"] = round(time.time() - started, 2)
        time.sleep(args.settle)
        before = probe(cdp, field["tag"])
        node = before["node"]
        result["hasFocusBeforePress"] = before["hasFocus"]
        x, y = centre(node)
        click(cdp, x - args.offset, y, move=not args.no_move, hold=args.hold)
        time.sleep(args.wait)
        after_press = probe(cdp, field["tag"])
        result["focused"] = after_press["backingFocused"]
        result["backing"] = after_press["backing"]
        result["activeAfterPress"] = after_press["active"]
        result["events"] = cdp.call("Runtime.evaluate", expression="window.__events.join(' ')",
                                    returnByValue=True)["result"].get("value")
        type_text(cdp, args.text)
        time.sleep(args.land)
        after_typing = probe(cdp, field["tag"])
        result["text"] = after_typing["node"]["text"] if after_typing["node"] else None
        result["landed"] = bool(result["text"]) and args.text in result["text"]
        if not (result["focused"] and result["landed"]):
            shot = cdp.call("Page.captureScreenshot", format="png")["data"]
            with open(os.path.join(out, f"{name}-{n:02d}.png"), "wb") as f:
                f.write(base64.b64decode(shot))
        return result
    finally:
        try:
            cdp.call("Browser.close", timeout=5)
        except Exception:
            pass
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()
        shutil.rmtree(profile, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("url")
    ap.add_argument("out")
    ap.add_argument("--loads", type=int, default=10)
    ap.add_argument("--fields", default="search,price,promo")
    ap.add_argument("--settle", type=float, default=1.0)
    ap.add_argument("--wait", type=float, default=0.6)
    ap.add_argument("--text", default="123")
    ap.add_argument("--land", type=float, default=0.6)
    ap.add_argument("--timeout", type=float, default=60)
    ap.add_argument("--no-move", action="store_true")
    ap.add_argument("--blurred", action="store_true")
    ap.add_argument("--hidden", action="store_true")
    ap.add_argument("--frames-every", type=float, default=0)
    ap.add_argument("--hold", type=float, default=0.05)
    ap.add_argument("--offset", type=float, default=0)
    args = ap.parse_args()
    base = args.url.rstrip("/")
    os.makedirs(args.out, exist_ok=True)
    chrome = chromium()
    names = args.fields.split(",")
    unknown = [n for n in names if n not in FIELDS]
    if unknown:
        raise SystemExit(f"unknown fields: {unknown}")
    rows = {n: [] for n in names}
    with open(os.path.join(args.out, "runs.jsonl"), "a") as log:
        for i in range(1, args.loads + 1):
            for name in names:  # interleaved, so a slow stretch of the box falls on every field alike
                r = run_once(chrome, base, name, args, args.out, i)
                rows[name].append(r)
                log.write(json.dumps(r) + "\n")
                log.flush()
                print(json.dumps(r), flush=True)
    ok = True
    print(f"\n{'field':10} {'loads':>5} {'focused':>8} {'landed':>7} {'errors':>6}")
    for name, rs in rows.items():
        focused = sum(1 for r in rs if r.get("focused"))
        landed = sum(1 for r in rs if r.get("landed"))
        errors = sum(1 for r in rs if "error" in r)
        print(f"{name:10} {len(rs):5} {focused:8} {landed:7} {errors:6}")
        ok = ok and focused == landed == len(rs)
    print("PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
