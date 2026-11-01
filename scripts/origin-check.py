#!/usr/bin/env python3
"""B-128's check: a guest's walk through the storefront makes no request off the store's origin.

Standard library only; the DevTools client, the Chromium launch and the page helpers are B-28's and B-112's
(scripts/measure-first-load.py, scripts/first-press-check.py). In one fresh headless Chromium, with a guest whose
cart has a line made over HTTP, it walks

    home, /c/electronics, the header's search field typed into with the suggest panel open, Enter (the results),
    a product, the cart, /checkout (a guest's: the sign-in prompt)

and records every request the page sends, by step. Anything sent over http(s) or ws(s) to another host is listed
and fails the check: a shopper's browser telling a third party what they opened is not something the store chose.

The request it was written for: Compose, in the browser, fetches a Noto face from fonts.gstatic.com for any code
point no bundled face has (`installFallbackFontDownloader`, in every ComposeViewport, with no switch). The suggest
panel's «↵» did that until B-128; `BundledGlyphsTest` holds the strings the code writes.

--control types «↵» (U+21B5, in none of the bundled faces) into the search field after the walk: the check must
then fail with a request to fonts.gstatic.com, which shows it can.

    scripts/origin-check.py URL OUT_DIR [--control]

One JSON line per request in OUT_DIR/requests.jsonl, a screenshot per step, and the requests off the origin on
stdout. The exit status is 0 only when there are none. CHROME_BIN names the Chromium (default: Playwright's).
"""

import argparse
import base64
import importlib.util
import json
import os
import shutil
import sys
import time
from urllib.parse import urlparse

_spec = importlib.util.spec_from_file_location(
    "first_press_check", os.path.join(os.path.dirname(os.path.abspath(__file__)), "first-press-check.py"))
_m = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_m)

# A seeded product (server/.../seed/SampleCatalog.kt), and what the search field is given to open the panel.
PRODUCT = "/p/p-sonora-sh-718n"
QUERY = "head"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("url")
    ap.add_argument("out")
    ap.add_argument("--control", action="store_true")
    ap.add_argument("--settle", type=float, default=4.0, help="seconds a step is given after it is drawn")
    args = ap.parse_args()
    base = args.url.rstrip("/")
    origin = urlparse(base).netloc
    os.makedirs(args.out, exist_ok=True)
    requests, step = [], ["start"]

    guest = _m.guest_with_cart(base)
    proc, profile, cdp = _m.launch(_m.chromium())
    cdp.listeners.append(lambda msg: msg.get("method") == "Network.requestWillBeSent" and requests.append(
        {"step": step[0], "url": msg["params"]["request"]["url"][:300]}))
    try:
        cdp.call("Network.enable")
        cdp.call("Network.setCacheDisabled", cacheDisabled=True)
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        cdp.call("Page.addScriptToEvaluateOnNewDocument", source=f"localStorage.setItem('haul.guest', {json.dumps(guest)})")

        def shot(name):
            png = cdp.call("Page.captureScreenshot", format="png")["data"]
            with open(os.path.join(args.out, f"{name}.png"), "wb") as f:
                f.write(base64.b64decode(png))

        def go(name, path):
            step[0] = name
            cdp.call("Page.navigate", url=base + path)
            shown = _m.wait_for(cdp, "search-field", 60)
            if not shown:
                raise SystemExit(f"{name}: the header's search field never drawn")
            time.sleep(args.settle)
            shot(name)
            return shown

        def press_field(shown):
            _m.click(cdp, *_m.centre(shown["node"]))
            time.sleep(0.6)

        def key(name, code, vk, text=None):
            down = {"text": text} if text else {}
            cdp.call("Input.dispatchKeyEvent", type="keyDown", key=name, code=code, windowsVirtualKeyCode=vk,
                     nativeVirtualKeyCode=vk, **down)
            cdp.call("Input.dispatchKeyEvent", type="keyUp", key=name, code=code, windowsVirtualKeyCode=vk,
                     nativeVirtualKeyCode=vk)

        go("home", "/")
        go("category", "/c/electronics")
        field = go("search", "/")
        step[0] = "suggest"
        press_field(field)
        _m.type_text(cdp, QUERY)
        if not _m.wait_for(cdp, "suggest-panel", 30):
            raise SystemExit("suggest: the panel never drawn")
        time.sleep(args.settle)
        shot("suggest")
        step[0] = "results"
        key("Enter", "Enter", 13, "\r")
        time.sleep(args.settle)
        shot("results")
        go("product", PRODUCT)
        go("cart", "/cart")
        go("checkout", "/checkout")
        if args.control:
            field = go("control", "/")
            press_field(field)
            cdp.call("Input.insertText", text="↵")
            time.sleep(args.settle + 2)
            shot("control")
        time.sleep(2.0)
    finally:
        cdp.listeners.clear()
        proc.kill()
        shutil.rmtree(profile, ignore_errors=True)

    with open(os.path.join(args.out, "requests.jsonl"), "w") as log:
        for r in requests:
            log.write(json.dumps(r) + "\n")
    off = [r for r in requests
           if urlparse(r["url"]).scheme in ("http", "https", "ws", "wss") and urlparse(r["url"]).netloc != origin]
    per_step = {}
    for r in requests:
        per_step[r["step"]] = per_step.get(r["step"], 0) + 1
    print(json.dumps({"requests": per_step, "offOrigin": off}, indent=1))
    print("PASS" if not off else "FAIL")
    sys.exit(0 if not off else 1)


if __name__ == "__main__":
    main()
