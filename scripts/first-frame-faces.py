#!/usr/bin/env python3
"""B-150's check: on a cold load the store's first frame is drawn in its own faces, not in a fallback.

Standard library only (the WSL box has no node and no pip packages); the DevTools client and the Chromium
launch are B-28's (scripts/measure-first-load.py). For every load it

1. starts a fresh headless Chromium with a fresh profile, the cache disabled and the link throttled to
   --profile (DevTools' own numbers, as the measurement throttles it), at --width x --height;
2. takes a screenshot every --every seconds from the navigation until --after seconds past the static frame's
   removal (B-80), and beside each one reads whether the frame was still on the page just before and just after;
3. crops --clip from every screenshot — by default the logo, «Haul» in Bodoni Moda, which nothing on the page
   changes once it is drawn — and compares each store shot's crop, pixel for pixel, with the last shot's.

A store shot is one taken with the frame already gone; a shot during which the frame went is left out of the
comparison (it may show either). «Faces at the first frame» is the first store shot's crop equal to the last's:
drawn in a fallback face, the logo is another set of pixels until the files land and the page is redrawn.

Every screenshot goes to OUT_DIR (`load<n>-<index>-<ms>.png`), one JSON line per load to OUT_DIR/runs.jsonl,
and a line per load to stdout. The exit status is 0 only when every load drew its first store shot in the faces.
The control is a bundle that draws in a fallback first — `main` before B-150 — which this check must fail.

    scripts/first-frame-faces.py URL OUT_DIR [--loads 3] [--profile fast4g] [--every 0.15] [--after 4]
                                 [--width 1440] [--height 900] [--clip 40,48,150,72]

CHROME_BIN names the Chromium (default: Playwright's under ~/.cache/ms-playwright).
"""

import argparse
import base64
import importlib.util
import json
import os
import shutil
import struct
import time
import zlib

_spec = importlib.util.spec_from_file_location(
    "measure_first_load", os.path.join(os.path.dirname(os.path.abspath(__file__)), "measure-first-load.py"))
_m = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_m)
chromium, launch, PROFILES = _m.chromium, _m.launch, _m.PROFILES

FRAME = "!!document.getElementById('haul-frame')"


def pixels(png, upto):
    """The first [upto] rows of a PNG Chromium wrote (8-bit RGBA or RGB, not interlaced), each as bytes."""
    assert png[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, width, height, kind = 8, b"", 0, 0, 0
    while pos < len(png):
        n, tag = struct.unpack(">I4s", png[pos:pos + 8])
        body = png[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            width, height, depth, kind, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert depth == 8 and kind in (2, 6) and interlace == 0, f"unexpected PNG {depth}/{kind}/{interlace}"
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    bpp = 4 if kind == 6 else 3
    raw, stride, rows, prev = zlib.decompress(idat), width * bpp, [], bytearray(width * bpp)
    for y in range(min(upto, height)):
        f, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b, c = prev[i], prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 0xFF
            elif f == 2:
                line[i] = (line[i] + b) & 0xFF
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        rows.append(bytes(line))
        prev = line
    return rows, bpp


def crop(png, clip):
    x, y, w, h = clip
    rows, bpp = pixels(png, y + h)
    return b"".join(r[x * bpp:(x + w) * bpp] for r in rows[y:y + h])


def load_once(chrome, url, a, n):
    proc, profile_dir, cdp = launch(chrome)
    shots = []
    try:
        cdp.call("Network.enable")
        cdp.call("Page.enable")
        cdp.call("Network.setCacheDisabled", cacheDisabled=True)
        p = PROFILES[a.profile]
        cdp.call("Network.emulateNetworkConditions", offline=False, latency=p["latency"],
                 downloadThroughput=p["down"], uploadThroughput=p["up"])
        cdp.call("Emulation.setDeviceMetricsOverride", width=a.width, height=a.height, deviceScaleFactor=1,
                 mobile=False)

        def frame_on():
            try:
                return cdp.call("Runtime.evaluate", expression=FRAME, returnByValue=True)["result"].get("value")
            except RuntimeError:
                return None

        started = time.time()
        cdp.call("Page.navigate", url=url)
        removed_at = None
        while True:
            due = started + len(shots) * a.every
            time.sleep(max(0.0, due - time.time()))
            before = frame_on()
            png = base64.b64decode(cdp.call("Page.captureScreenshot", format="png")["data"])
            after = frame_on()
            t = time.time() - started
            name = f"load{n}-{len(shots):03d}-{int(t * 1000):05d}.png"
            with open(os.path.join(a.out, name), "wb") as f:
                f.write(png)
            kind = "frame" if after else ("store" if before is False else "swap")
            shots.append({"t": round(t, 3), "file": name, "kind": kind, "png": png})
            if kind == "store" and removed_at is None:
                removed_at = t
            if removed_at is not None and t - removed_at >= a.after:
                break
            if t > a.timeout:
                break
    finally:
        try:
            cdp.call("Browser.close", timeout=5)
        except Exception:
            pass
        try:
            proc.wait(10)
        except Exception:
            proc.kill()
        shutil.rmtree(profile_dir, ignore_errors=True)

    store = [s for s in shots if s["kind"] == "store"]
    if not store:
        return {"load": n, "verdict": "no store shot", "shots": [{k: v for k, v in s.items() if k != "png"} for s in shots]}
    last = crop(store[-1]["png"], a.clip)
    for s in store:
        s["faces"] = crop(s["png"], a.clip) == last
    fallback = [s for s in store if not s["faces"]]
    return {
        "load": n, "profile": a.profile,
        "verdict": "faces at the first frame" if store[0]["faces"] else "fallback at the first frame",
        "first_store_shot": store[0]["file"], "first_store_t": store[0]["t"],
        "store_shots": len(store), "fallback_shots": [s["file"] for s in fallback],
        "first_faces_t": next((s["t"] for s in store if s["faces"] and all(x["faces"] for x in store[store.index(s):])), None),
        "shots": [{k: v for k, v in s.items() if k != "png"} for s in shots],
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("url")
    ap.add_argument("out")
    ap.add_argument("--loads", type=int, default=3)
    ap.add_argument("--profile", default="fast4g", choices=sorted(PROFILES))
    ap.add_argument("--every", type=float, default=0.15)
    ap.add_argument("--after", type=float, default=4.0)
    ap.add_argument("--timeout", type=float, default=90.0)
    ap.add_argument("--width", type=int, default=1440)
    ap.add_argument("--height", type=int, default=900)
    ap.add_argument("--clip", default="40,48,150,72", help="x,y,w,h of the crop compared: the logo at 1440")
    a = ap.parse_args()
    a.clip = tuple(int(v) for v in a.clip.split(","))
    os.makedirs(a.out, exist_ok=True)
    chrome = chromium()
    ok = True
    with open(os.path.join(a.out, "runs.jsonl"), "w") as raw:
        for n in range(1, a.loads + 1):
            r = load_once(chrome, a.url, a, n)
            raw.write(json.dumps(r) + "\n")
            raw.flush()
            ok &= r["verdict"] == "faces at the first frame"
            print(f"load {n} {a.profile}: {r['verdict']}; first store shot {r.get('first_store_shot')} at "
                  f"{r.get('first_store_t')} s, {r.get('store_shots')} store shots, fallback in "
                  f"{len(r.get('fallback_shots') or [])} {r.get('fallback_shots') or ''}; faces from {r.get('first_faces_t')} s; "
                  f"{len(r['shots'])} shots, every {a.every} s", flush=True)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
