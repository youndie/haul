#!/usr/bin/env python3
"""B-126's check: the browser's accessible mirror of the storefront follows the page after a popup, and every
button and every field in it has a name (B-131 added the fields, the cart button and the review dialog; B-151 the
states, the cards' «+», what is under a sheet or a dialog, and Escape on the Plus trial's dialog).

Compose draws the page into a canvas and mirrors its semantics into the DOM for assistive technology: `div`s under
`#cmp_a11y_root` in the viewport's shadow root, a `role="button"` for each press, named by its `aria-label` (a
content description) or its text, and a `role="textbox"` for each field, named by its `aria-label` alone. The
header's cart button must be named as the cart: on a phone it draws the count alone, which names it «1». Standard
library only; the DevTools client and the Chromium launch are B-28's and B-112's (scripts/measure-first-load.py,
scripts/first-press-check.py). In one headless Chromium it

1. signs in, when --login names a shopper: the sign-in page (B-66), the provider's form, back to the store;
2. for every popup — «Catalog», the sort, the search field's picker, the account menu at 1440; the menu and the
   filter sheet at 390 — loads its page, presses the control that opens it (found in the mirror by its name),
   waits until the popup's nodes are in the mirror, closes it (Escape, a press off it, or its «×»), then follows a
   link of the page (pressed in the mirror too) and waits: the mirror must hold the new page — the header's
   controls, words that were not there before — and none of the popup's nodes;
3. on every page it loaded, reads the mirror and Chromium's accessibility tree (`Accessibility.getFullAXTree`) for
   buttons and fields with no name;
4. signed in, opens the review dialog at both widths — the product's reviews tab scrolled to «Write a review», which
   is pressed — and reads the page with the dialog over it the same way. A guest's «Write a review» signs in instead;
5. while a sheet or a dialog is open (the phone's menu, the filter sheet, the review dialog, the Plus trial's), reads
   what of the page is still in the mirror outside it (`#over-page`): nothing may be, and no button may hold the
   dialog's «×» — the scrim was one button with the whole dialog in it (B-151);
6. on the cart, reads each line's box and «Select all» — named «Select <product>, ticked» / «…, not ticked», «Select
   all, ticked» — presses a line's box and reads its name turn; at checkout (signed in) the points switch, «…, on» /
   «…, off», the same way; on the home page and a category, every card's «+» named «Add <product> to cart»;
7. signed in, presses «Try 30 days free» on the home page for a shopper who is not a member, and Escape: the dialog
   must close (it closed on no key before B-151). A member has no such press, and the step says so.

    scripts/a11y-check.py URL OUT_DIR [--login EMAIL]     (the password from HAUL_A11Y_PASSWORD)

The account menu, the review dialog, checkout and the Plus trial need --login; without it they are skipped and say
so, and a guest's cart is given one line over HTTP before the walk. A shopper with nothing saved (`sam` on a fresh
stack) gets the empty Saved list, its picture a press. One JSON line per step in
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
             w: r.width, h: r.height, over: !!d.closest('#over-page'),
             holds: !!d.querySelector('#dialog-close, #plus-trial-close') };
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
     "close": "escape", "follow": "Electronics", "sheet": True},
    {"name": "filter sheet", "path": "/c/electronics", "size": PHONE, "open": "Filters",
     "shown": {"label": "Close filters"}, "close": {"press": "Close filters"}, "follow": "Haul", "sheet": True},
]

# The pages read for unnamed buttons, beside the popups' own; the signed-in ones only with --login.
PAGES = [
    ("/", False), ("/c/electronics", False), ("/search?q=mug", False), ("/p/p-sony-wh-1000xm6", False),
    ("/cart", False), ("/account", True), ("/account/orders", True), ("/account/saved", True), ("/checkout", True),
]

# The product whose reviews tab the review dialog is opened from (a signed-in shopper who has not reviewed it).
REVIEWED = "/p/p-sony-wh-1000xm6?tab=reviews"

# The header's cart button (its test tag) and the word its name must have.
CART = ("cart-button", "Cart")

# The SKU put in the signed-in shopper's cart, so that checkout draws (an empty one leads to the cart, B-94).
CART_SKU = _m.CART_SKU

# B-151: the box of a cart line, «Select all», checkout's points switch and a card's «+», by their ids (test tags).
LINE_BOX, SELECT_ALL, POINTS, CARD_ADD = "cart-line-box:", "cart-select-all", "checkout-points", "card-add"
TICK_WORDS, SWITCH_WORDS = ("ticked", "partly ticked", "not ticked"), ("on", "off")

# What says a sheet or a dialog is open, and the press that opens the Plus trial's dialog.
OVER_PAGE, TRIAL = "over-page", "Try 30 days free"


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
    """Buttons and fields with no name, in the mirror and in Chromium's own accessibility tree, and a cart button
    whose name does not say it is the cart."""
    nodes = mirror(cdp)
    in_mirror = [n for n in nodes if n["role"] == "button" and not n["label"] and not n["all"]]
    fields = [n for n in nodes if n["role"] == "textbox" and not n["label"]]
    cart = [n for n in nodes if n["id"] == CART[0] and CART[1] not in n["label"] + " " + n["all"]]
    tree = cdp.call("Accessibility.getFullAXTree")["nodes"]
    in_tree = [n for n in tree if n.get("role", {}).get("value") in ("button", "textbox") and not n.get("ignored")
               and not (n.get("name", {}).get("value") or "").strip()]
    found = [{**{k: n[k] for k in ("id", "x", "y", "w", "h")}, "role": n["role"]} for n in in_mirror + fields]
    found += [{"id": n["id"], "named": n["label"] or n["all"], "role": "cart"} for n in cart]
    return found, len(in_tree)


def reach(cdp, want, height, timeout=20):
    """The node [want] once it is in the window: the page is scrolled down to it, a wheel turn at a time."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        node = find(cdp, want, 1)
        if node and node["w"] > 0 and 0 < node["y"] < height - 20:
            return node
        cdp.call("Input.dispatchMouseEvent", type="mouseWheel", x=200, y=height // 2, deltaX=0, deltaY=300)
        time.sleep(0.3)
    return None


def under_overlay(cdp):
    """What of the page is in the mirror while a sheet or a dialog is open: every node with a role or words that is
    not inside it, and every button that holds a dialog's «×»."""
    nodes = mirror(cdp)
    under = [n for n in nodes if not n["over"] and n["id"] != OVER_PAGE and (n["role"] or n["label"] or n["text"])]
    holding = [n for n in nodes if n["role"] == "button" and n["holds"]]
    return ([n["label"] or n["all"][:40] or n["role"] for n in under][:12], len(under),
            [(n["label"] or n["all"])[:60] for n in holding])


def said(node, words):
    """The state a box's or a switch's name ends with, or None: «Select all, partly ticked» says «partly ticked»."""
    for word in sorted(words, key=len, reverse=True):
        if node["label"].endswith(", " + word) or (", " + word + ". ") in node["label"]:
            return word
    return None


def walk_states(cdp, base, wh, signed_in, out):
    """The cart's boxes and checkout's switch: each says what it does and whether it is on, and a press turns it."""
    steps = []
    size(cdp, wh)
    step = {"states": "cart", "width": wh[0]}
    if not load(cdp, base, "/cart"):
        return [{**step, "error": "the cart never drew"}]
    boxes = [n for n in mirror(cdp) if n["id"].startswith(LINE_BOX) and n["w"] > 0]
    every = [n for n in mirror(cdp) if n["id"] == SELECT_ALL]
    wrong = [n["label"] or n["all"] for n in boxes if not n["label"].startswith("Select ") or not said(n, TICK_WORDS)]
    wrong += [n["label"] or n["all"] for n in every if not n["label"].startswith("Select all, ") or not said(n, TICK_WORDS)]
    step.update({"boxes": [n["label"] for n in boxes], "select_all": [n["label"] for n in every], "wrong": wrong})
    if not boxes or not every:
        step["error"] = "no line's box or no «Select all» in the mirror"
    elif not wrong:
        before = said(boxes[0], TICK_WORDS)
        press(cdp, boxes[0])
        deadline, after = time.time() + 15, None
        while time.time() < deadline:
            again = find(cdp, {"id": boxes[0]["id"]}, 1)
            after = again and said(again, TICK_WORDS)
            if after and after != before:
                break
            time.sleep(0.3)
        step["pressed"] = f"{before} -> {after}"
        if after == before or not after:
            step["error"] = "the box's name did not turn when it was pressed"
        else:
            # Pressed back, so the next walk finds the cart as it was.
            press(cdp, find(cdp, {"id": boxes[0]["id"]}, 5))
            time.sleep(1.5)
    if step.get("error") or wrong:
        shot(cdp, out, f"states-cart-{wh[0]}")
    steps.append(step)
    if not signed_in:
        return steps
    step = {"states": "checkout", "width": wh[0]}
    if not load(cdp, base, "/checkout"):
        return steps + [{**step, "error": "checkout never drew"}]
    switch = reach(cdp, {"id": POINTS}, wh[1])
    if not switch:
        step["skipped"] = "no points switch at checkout (no balance to use)"
        return steps + [step]
    before = said(switch, SWITCH_WORDS)
    step.update({"switch": switch["label"], "wrong": [] if before else [switch["label"] or switch["all"]]})
    if before:
        press(cdp, switch)
        deadline, after = time.time() + 15, None
        while time.time() < deadline:
            again = find(cdp, {"id": POINTS}, 1)
            after = again and said(again, SWITCH_WORDS)
            if after and after != before:
                break
            time.sleep(0.3)
        step["pressed"] = f"{before} -> {after}"
        if after == before or not after:
            step["error"] = "the switch's name did not turn when it was pressed"
        else:
            press(cdp, find(cdp, {"id": POINTS}, 5))
            time.sleep(1.5)
    if step.get("error") or step["wrong"]:
        shot(cdp, out, f"states-checkout-{wh[0]}")
    return steps + [step]


def walk_pluses(cdp, base, path, wh, out):
    """Every card's «+» named by its product: «Add <product> to cart», and not one name for all of them."""
    size(cdp, wh)
    step = {"pluses": path, "width": wh[0]}
    if not load(cdp, base, path):
        return {**step, "error": "the page never drew"}
    # By its id, or by the one name every «+» had before B-151.
    labels = [n["label"] for n in mirror(cdp) if n["id"] == CARD_ADD or n["label"] == "Add to cart"]
    wrong = [label for label in labels if not (label.startswith("Add ") and label.endswith(" to cart"))
             or label == "Add to cart"]
    step.update({"count": len(labels), "distinct": len(set(labels)), "examples": labels[:3], "wrong": wrong})
    if len(labels) < 2 or len(set(labels)) < 2:
        step["error"] = f"{len(labels)} cards' «+», {len(set(labels))} names"
    if step.get("error") or wrong:
        shot(cdp, out, f"pluses-{path.strip('/').replace('/', '-') or 'home'}-{wh[0]}")
    return step


def walk_trial(cdp, base, wh, out):
    """The Plus trial's dialog: only itself in the mirror while it is open, and closed by Escape."""
    size(cdp, wh)
    step = {"dialog": "plus trial", "width": wh[0]}
    if not load(cdp, base, "/"):
        return {**step, "error": "the page never drew"}
    opener = reach(cdp, TRIAL, wh[1])
    if not opener:
        return {**step, "skipped": "no «Try 30 days free» on the home page (a member already)"}
    press(cdp, opener)
    if not find(cdp, {"id": "plus-trial-close"}, 10):
        shot(cdp, out, f"dialog-trial-{wh[0]}")
        return {**step, "error": "the dialog never showed in the mirror"}
    time.sleep(1.0)
    names, tree = unnamed(cdp)
    step["under"], step["under_count"], step["holding"] = under_overlay(cdp)
    step.update({"unnamed": names, "unnamed_in_ax_tree": tree})
    key(cdp, "Escape", "Escape", 27)
    deadline = time.time() + 10
    while time.time() < deadline and find(cdp, {"id": "plus-trial-close"}, 1):
        time.sleep(0.2)
    step["escape_closed"] = not find(cdp, {"id": "plus-trial-close"}, 1) and bool(find(cdp, TRIAL, 5))
    if names or tree or step["under_count"] or step["holding"] or not step["escape_closed"]:
        shot(cdp, out, f"dialog-trial-{wh[0]}")
    return step


def walk_dialog(cdp, base, wh, out):
    """The review dialog over the reviews tab: opened as a shopper opens it, then read for names."""
    step = {"dialog": "review", "width": wh[0]}
    size(cdp, wh)
    if not load(cdp, base, REVIEWED):
        return {**step, "error": "the page never drew"}
    opener = reach(cdp, {"id": "write-review"}, wh[1])
    if not opener:
        return {**step, "error": "no «Write a review» in the window"}
    press(cdp, opener)
    if not find(cdp, {"id": "dialog-close"}, 10):
        shot(cdp, out, f"dialog-review-{wh[0]}")
        return {**step, "error": "the dialog never showed in the mirror"}
    time.sleep(1.0)
    names, tree = unnamed(cdp)
    nodes = mirror(cdp)
    step["under"], step["under_count"], step["holding"] = under_overlay(cdp)
    step.update({"unnamed": names, "unnamed_in_ax_tree": tree,
                 "buttons": len([n for n in nodes if n["role"] == "button"]),
                 "fields": len([n for n in nodes if n["role"] == "textbox"])})
    if names or tree or step["under_count"] or step["holding"]:
        shot(cdp, out, f"dialog-review-{wh[0]}")
    return step


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
    # What the popup holds is read too: the filter sheet's price fields are there only while it is open (B-131).
    step["unnamed_open"], step["unnamed_open_in_ax_tree"] = unnamed(cdp)
    # A sheet leaves only itself in the mirror (B-151); a menu leaves the page as it is.
    if popup.get("sheet"):
        step["under"], step["under_count"], _ = under_overlay(cdp)
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
    if step["unnamed_open"] or step["unnamed_open_in_ax_tree"] or step.get("under_count"):
        step["ok"] = False
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
        if not args.login:
            # A guest whose cart has a line, so that the cart draws its boxes (B-151).
            guest = _m.guest_with_cart(base)
            cdp.call("Page.addScriptToEvaluateOnNewDocument",
                     source=f"localStorage.setItem('haul.guest', {json.dumps(guest)})")
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
        for wh in (WIDE, PHONE):
            if not account:
                steps.append({"dialog": "review", "width": wh[0], "skipped": "needs --login"})
                steps.append({"dialog": "plus trial", "width": wh[0], "skipped": "needs --login"})
            else:
                steps.append(walk_dialog(cdp, base, wh, args.out))
                steps.append(walk_trial(cdp, base, wh, args.out))
            steps.extend(walk_states(cdp, base, wh, bool(account), args.out))
            for path in ("/", "/c/electronics"):
                steps.append(walk_pluses(cdp, base, path, wh, args.out))
        for path, signed_in in PAGES:
            if signed_in and not account:
                continue
            for wh in (WIDE, PHONE):
                size(cdp, wh)
                drawn = load(cdp, base, path)
                names, tree = unnamed(cdp) if drawn else ([], 0)
                at = cdp.call("Runtime.evaluate", expression="location.pathname + location.search",
                              returnByValue=True)["result"]["value"]
                nodes = mirror(cdp)
                step = {"page": path, "at": at, "width": wh[0], "drawn": drawn, "unnamed": names,
                        "unnamed_in_ax_tree": tree, "buttons": len([n for n in nodes if n["role"] == "button"]),
                        "fields": len([n for n in nodes if n["role"] == "textbox"])}
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
                verdict = ("ok" if s["ok"] else "FAIL: the mirror did not follow the page, the popup held a "
                           "press or field with no name, or the page stayed in the mirror under a sheet") + \
                    f" — {s['mirror_nodes']} nodes on {s['followed_to']}, popup nodes left {s['popup_left']}, " \
                    f"unnamed while open {s['unnamed_open']}/{s['unnamed_open_in_ax_tree']}, " \
                    f"under it {s.get('under_count', '-')} {s.get('under', '')}, " \
                    f"after {len(s['unnamed'])}/{s['unnamed_in_ax_tree']}"
            print(f"popup {s['popup']} @{s.get('width', '')}: {verdict}")
        elif "dialog" in s:
            if "skipped" in s:
                verdict = "skipped: " + s["skipped"]
            elif "error" in s:
                verdict, bad = "FAIL: " + s["error"], bad + 1
            else:
                fine = not s["unnamed"] and not s["unnamed_in_ax_tree"] and not s["under_count"] and \
                    not s["holding"] and s.get("escape_closed", True)
                bad += 0 if fine else 1
                counts = f"{s['buttons']} buttons, {s['fields']} fields, " if "buttons" in s else ""
                escape = f", Escape closed it: {s['escape_closed']}" if "escape_closed" in s else ""
                verdict = f"{counts}under it {s['under_count']}, holding «×» {len(s['holding'])}{escape}: " + \
                    ("ok" if fine else f"FAIL: unnamed {s['unnamed']} (ax tree {s['unnamed_in_ax_tree']}), under it "
                     f"{s['under']}, holding {s['holding']}")
            print(f"dialog {s['dialog']} @{s['width']}: {verdict}")
        elif "states" in s or "pluses" in s:
            what = f"states {s['states']}" if "states" in s else f"pluses {s['pluses']}"
            if "skipped" in s:
                verdict = "skipped: " + s["skipped"]
            else:
                fine = "error" not in s and not s.get("wrong")
                bad += 0 if fine else 1
                shown = {k: s[k] for k in ("boxes", "select_all", "switch", "pressed", "count", "distinct", "examples")
                         if k in s}
                verdict = ("ok" if fine else f"FAIL: {s.get('error', '')} wrong {s.get('wrong')}") + f" — {shown}"
            print(f"{what} @{s['width']}: {verdict}")
        elif "page" in s:
            fine = s["drawn"] and not s["unnamed"] and not s["unnamed_in_ax_tree"]
            bad += 0 if fine else 1
            verdict = "ok" if fine else \
                f"FAIL: drawn={s['drawn']} unnamed {s['unnamed']} (ax tree {s['unnamed_in_ax_tree']})"
            print(f"page {s['page']} @{s['width']} (drawn at {s['at']}, {s['buttons']} buttons, "
                  f"{s['fields']} fields): {verdict}")
    print("VERDICT:", "ok" if bad == 0 else f"{bad} failing")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
