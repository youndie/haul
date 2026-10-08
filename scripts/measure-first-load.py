#!/usr/bin/env python3
"""B-28's measurement: how big the storefront's first load is and how long it takes to the first frame.

Standard library only (the WSL box has no node and no pip packages); brotli comes from the
libbrotlienc that ships next to Chromium. Given a production distribution directory, it

1. serves the directory over loopback, compressing each response with what the browser asks for
   (brotli q11, else gzip -9, else nothing) - or with nothing, under --identity;
2. for every round and network profile, starts a fresh headless Chromium with a fresh profile,
   disables the cache, throttles through CDP `Network.emulateNetworkConditions`, injects the probe
   and loads the page;
3. writes one JSON line per run (raw), a size table per file the page requested and a summary.

The probe: the WebGL (1 and 2) `clear` and `draw*` calls are wrapped before any page script runs.
The first call is the first time Compose draws; the first requestAnimationFrame after it is the
frame that carries it to the screen. "First frame" is that rAF's time since navigation start;
"settled" is the same for the last GL frame before the page has been quiet for QUIET seconds.

Controls in the same log: a run with every .wasm blocked must report no frame, and the profiles
must come out in their known order with Slow 4G above its bandwidth floor. A run with every font
blocked shows what the first frame looks like before the fonts arrive (its screenshot).

    scripts/measure-first-load.py DIST_DIR OUT_DIR [--rounds 7] [--label main] [--identity] [--url URL]

--url measures a server that is already running (B-34: the image, which sends the `.br`/`.gz` it
carries) instead of serving DIST_DIR; DIST_DIR is then only read for the size table, and a `.br` or
`.gz` found beside a file there is reported as that file's compressed size, since it is what is sent.

CHROME_BIN names the Chromium (default: Playwright's under ~/.cache/ms-playwright).
"""

import argparse
import base64
import ctypes
import ctypes.util
import glob
import gzip
import http.server
import json
import os
import shutil
import socket
import socketserver
import statistics
import struct
import subprocess
import tempfile
import threading
import time
import urllib.request

# DevTools' own presets (front_end/core/sdk/NetworkManager.ts): bytes per second and ms.
PROFILES = {
    "none": {"latency": 0, "down": -1, "up": -1},
    "fast4g": {"latency": 60 * 2.75, "down": 9 * 1000 * 1000 / 8 * 0.9, "up": 1.5 * 1000 * 1000 / 8 * 0.9},
    "slow4g": {"latency": 150 * 3.75, "down": 1.6 * 1000 * 1000 / 8 * 0.9, "up": 750 * 1000 / 8 * 0.9},
}
QUIET = 3.0
INCOMPRESSIBLE = (".png", ".jpg", ".jpeg", ".webp", ".woff2", ".gz", ".br")

PROBE = r"""
(() => {
  const P = window.__probe = { calls: 0, firstGl: null, lastGl: null, frames: [], contexts: [] };
  const getContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (type, ...rest) {
    const ctx = getContext.call(this, type, ...rest);
    if (ctx) P.contexts.push([type, performance.now()]);
    return ctx;
  };
  let pending = false;
  const mark = () => {
    const t = performance.now();
    if (P.firstGl === null) P.firstGl = t;
    P.lastGl = t;
    P.calls++;
    if (!pending) {
      pending = true;
      requestAnimationFrame(() => { pending = false; P.frames.push(performance.now()); });
    }
  };
  for (const C of [window.WebGLRenderingContext, window.WebGL2RenderingContext]) {
    if (!C) continue;
    for (const m of ["clear", "drawArrays", "drawElements", "drawArraysInstanced",
                     "drawElementsInstanced", "drawRangeElements"]) {
      const f = C.prototype[m];
      if (f) C.prototype[m] = function (...a) { mark(); return f.apply(this, a); };
    }
  }
})();
"""

COLLECT = r"""
JSON.stringify({
  probe: window.__probe || null,
  nav: performance.getEntriesByType("navigation").map(e => ({
    responseEnd: e.responseEnd, dcl: e.domContentLoadedEventEnd, load: e.loadEventEnd,
    transferSize: e.transferSize }))[0] || null,
  resources: performance.getEntriesByType("resource").map(e => ({
    name: e.name, start: e.startTime, responseEnd: e.responseEnd, transferSize: e.transferSize,
    encodedBodySize: e.encodedBodySize, decodedBodySize: e.decodedBodySize })),
  canvas: Array.from(document.querySelectorAll("canvas")).map(c => [c.width, c.height]),
})
"""


# ---------------------------------------------------------------- compression


def brotli_lib(chrome):
    candidates = [os.environ.get("BROTLI_LIB"), ctypes.util.find_library("brotlienc")]
    candidates += glob.glob(os.path.join(os.path.dirname(chrome), "libbrotlienc.so*"))
    for c in candidates:
        if c:
            try:
                lib = ctypes.CDLL(c)
                lib.BrotliEncoderMaxCompressedSize.restype = ctypes.c_size_t
                lib.BrotliEncoderMaxCompressedSize.argtypes = [ctypes.c_size_t]
                lib.BrotliEncoderCompress.restype = ctypes.c_int
                lib.BrotliEncoderCompress.argtypes = [
                    ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_size_t, ctypes.c_char_p,
                    ctypes.POINTER(ctypes.c_size_t), ctypes.c_char_p,
                ]
                return lib, c
            except OSError:
                continue
    raise SystemExit("no libbrotlienc found: set BROTLI_LIB")


def brotli(lib, data, quality=11, lgwin=22):
    size = ctypes.c_size_t(lib.BrotliEncoderMaxCompressedSize(len(data)) or len(data) + 1024)
    out = ctypes.create_string_buffer(size.value)
    if not lib.BrotliEncoderCompress(quality, lgwin, 0, len(data), data, ctypes.byref(size), out):
        raise RuntimeError("brotli failed")
    return out.raw[: size.value]


# ---------------------------------------------------------------- the files


def bucket(rel, data):
    name = os.path.basename(rel)
    if name.endswith(".wasm"):
        # Kotlin/Wasm imports its JS helpers from the `js_code` module; skiko's wasm does not.
        return "app .wasm (Kotlin)" if b"js_code" in data else "skiko .wasm"
    if name.endswith((".js", ".mjs")):
        return "js glue (composeApp.js: app + skiko loaders)"
    if name.endswith((".ttf", ".otf", ".woff", ".woff2")):
        return "fonts"
    if name.endswith(".html"):
        return "index.html"
    return "other"


def load_dist(dist, lib):
    files = {}
    for root, _, names in os.walk(dist):
        for n in names:
            path = os.path.join(root, n)
            if n.endswith((".br", ".gz")) and os.path.exists(path[:-3]):
                continue  # a precompressed variant (B-34): read below as its original's size
            rel = os.path.relpath(path, dist).replace(os.sep, "/")
            data = open(path, "rb").read()
            compress = not n.endswith(INCOMPRESSIBLE)

            def variant(ext, make):
                if os.path.exists(f"{path}.{ext}"):
                    return open(f"{path}.{ext}", "rb").read()
                return make() if compress else None

            files[rel] = {
                "raw": data,
                "gzip": variant("gz", lambda: gzip.compress(data, 9, mtime=0)),
                "br": variant("br", lambda: brotli(lib, data)),
                "bucket": bucket(rel, data),
            }
    return files


MIME = {".wasm": "application/wasm", ".js": "text/javascript", ".mjs": "text/javascript",
        ".html": "text/html; charset=utf-8", ".ttf": "font/ttf", ".txt": "text/plain",
        ".json": "application/json", ".map": "application/json"}


def serve(files, identity):
    seen = set()

    class Handler(http.server.BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *a):
            pass

        def do_GET(self):
            rel = self.path.split("?")[0].lstrip("/") or "index.html"
            f = files.get(rel)
            if f is None:
                self.send_response(404)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            accept = self.headers.get("Accept-Encoding", "")
            seen.add(accept)
            enc, body = None, f["raw"]
            if not identity and f["br"] is not None and "br" in accept:
                enc, body = "br", f["br"]
            elif not identity and f["gzip"] is not None and "gzip" in accept:
                enc, body = "gzip", f["gzip"]
            self.send_response(200)
            self.send_header("Content-Type", MIME.get(os.path.splitext(rel)[1], "application/octet-stream"))
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Vary", "Accept-Encoding")
            if enc:
                self.send_header("Content-Encoding", enc)
            self.end_headers()
            self.wfile.write(body)

    class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
        daemon_threads = True

    srv = Server(("127.0.0.1", 0), Handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv, seen


# ---------------------------------------------------------------- a CDP client over a bare websocket


class Cdp:
    def __init__(self, ws_url):
        hostport, path = ws_url[len("ws://"):].split("/", 1)
        host, port = hostport.split(":")
        self.sock = socket.create_connection((host, int(port)))
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall(
            f"GET /{path} HTTP/1.1\r\nHost: {hostport}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n".encode())
        head = b""
        while b"\r\n\r\n" not in head:
            head += self.sock.recv(1)
        if b" 101 " not in head.split(b"\r\n")[0]:
            raise RuntimeError(head.decode(errors="replace"))
        self.next_id = 0
        self.lock = threading.Lock()
        self.results = {}
        self.cond = threading.Condition()
        self.events = []
        self.listeners = []
        self.closed = False
        threading.Thread(target=self._reader, daemon=True).start()

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise EOFError
            buf += chunk
        return buf

    def _frame(self):
        b0, b1 = self._exact(2)
        n = b1 & 0x7F
        if n == 126:
            n = struct.unpack(">H", self._exact(2))[0]
        elif n == 127:
            n = struct.unpack(">Q", self._exact(8))[0]
        mask = self._exact(4) if b1 & 0x80 else None
        data = self._exact(n)
        if mask:
            data = bytes(c ^ mask[i % 4] for i, c in enumerate(data))
        return b0 & 0x80, b0 & 0x0F, data

    def _reader(self):
        try:
            message = b""
            while True:
                fin, op, data = self._frame()
                if op == 8:
                    break
                if op == 9:
                    self._send(10, data)
                    continue
                if op in (0, 1, 2):
                    message += data
                    if not fin:
                        continue
                    msg = json.loads(message)
                    message = b""
                    if "id" in msg:
                        with self.cond:
                            self.results[msg["id"]] = msg
                            self.cond.notify_all()
                    else:
                        for f in self.listeners:
                            f(msg)
        except (EOFError, OSError):
            pass
        finally:
            with self.cond:
                self.closed = True
                self.cond.notify_all()

    def _send(self, op, payload):
        mask = os.urandom(4)
        n = len(payload)
        head = bytes([0x80 | op])
        if n < 126:
            head += bytes([0x80 | n])
        elif n < 65536:
            head += bytes([0x80 | 126]) + struct.pack(">H", n)
        else:
            head += bytes([0x80 | 127]) + struct.pack(">Q", n)
        body = bytes(c ^ mask[i % 4] for i, c in enumerate(payload))
        with self.lock:
            self.sock.sendall(head + mask + body)

    def call(self, method, timeout=30, **params):
        self.next_id += 1
        i = self.next_id
        self._send(1, json.dumps({"id": i, "method": method, "params": params}).encode())
        deadline = time.time() + timeout
        with self.cond:
            while i not in self.results:
                if self.closed or time.time() > deadline:
                    raise RuntimeError(f"{method}: no answer")
                self.cond.wait(0.5)
            msg = self.results.pop(i)
        if "error" in msg:
            raise RuntimeError(f"{method}: {msg['error']}")
        return msg.get("result", {})


def chromium():
    c = os.environ.get("CHROME_BIN")
    if c:
        return c
    found = sorted(glob.glob(os.path.expanduser("~/.cache/ms-playwright/chromium-*/chrome-linux*/chrome")))
    if not found:
        raise SystemExit("no Chromium: set CHROME_BIN")
    return found[-1]


def launch(chrome):
    profile = tempfile.mkdtemp(prefix="b28-chrome-")
    proc = subprocess.Popen(
        [chrome, "--headless=new", "--remote-debugging-port=0", f"--user-data-dir={profile}",
         "--no-first-run", "--no-default-browser-check", "--no-sandbox", "--disable-extensions",
         "--disable-background-networking", "--disable-component-update", "--disable-sync",
         "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--window-size=1440,900",
         "about:blank"],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    port = None
    deadline = time.time() + 30
    while time.time() < deadline:
        line = proc.stderr.readline()
        if "DevTools listening on" in line:
            port = int(line.strip().rsplit(":", 1)[1].split("/")[0])
            break
    if port is None:
        proc.kill()
        raise RuntimeError("Chromium did not start")
    threading.Thread(target=lambda: [None for _ in proc.stderr], daemon=True).start()
    pages = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list"))
    page = next(p for p in pages if p["type"] == "page")
    return proc, profile, Cdp(page["webSocketDebuggerUrl"])


# ---------------------------------------------------------------- one run


def stand():
    load = open("/proc/loadavg").read().split()[:3]
    mem = next(int(l.split()[1]) for l in open("/proc/meminfo") if l.startswith("MemAvailable"))
    return {"nproc": os.cpu_count(), "loadavg": [float(x) for x in load], "mem_available_mb": mem // 1024}


def run_once(chrome, url, profile_name, blocked, timeout, shot=None):
    proc, profile_dir, cdp = launch(chrome)
    inflight, responses, finished, failed, accepts = set(), {}, {}, [], set()
    state = {"load": False, "last_net": time.time()}

    def on_event(msg):
        m, p = msg["method"], msg.get("params", {})
        if m == "Network.requestWillBeSent":
            inflight.add(p["requestId"])
            state["last_net"] = time.time()
        elif m == "Network.requestWillBeSentExtraInfo":
            hdr = {k.lower(): v for k, v in p.get("headers", {}).items()}
            if "accept-encoding" in hdr:
                accepts.add(hdr["accept-encoding"])
        elif m == "Network.responseReceived":
            r = p["response"]
            hdr = {k.lower(): v for k, v in r.get("headers", {}).items()}
            responses[p["requestId"]] = {"url": r["url"], "status": r["status"],
                                         "encoding": hdr.get("content-encoding", "identity"),
                                         "type": hdr.get("content-type"), "cache": hdr.get("cache-control"),
                                         "vary": hdr.get("vary")}
        elif m == "Network.loadingFinished":
            inflight.discard(p["requestId"])
            finished[p["requestId"]] = p["encodedDataLength"]
            state["last_net"] = time.time()
        elif m == "Network.loadingFailed":
            inflight.discard(p["requestId"])
            failed.append(p.get("errorText"))
            state["last_net"] = time.time()
        elif m == "Page.loadEventFired":
            state["load"] = True

    cdp.listeners.append(on_event)
    try:
        cdp.call("Network.enable")
        cdp.call("Page.enable")
        cdp.call("Network.setCacheDisabled", cacheDisabled=True)
        prof = PROFILES[profile_name]
        cdp.call("Network.emulateNetworkConditions", offline=False, latency=prof["latency"],
                 downloadThroughput=prof["down"], uploadThroughput=prof["up"])
        if blocked:
            cdp.call("Network.setBlockedURLs", urls=blocked)
        cdp.call("Emulation.setDeviceMetricsOverride", width=1440, height=900, deviceScaleFactor=1, mobile=False)
        cdp.call("Page.addScriptToEvaluateOnNewDocument", source=PROBE)
        before = stand()
        started = time.time()
        cdp.call("Page.navigate", url=url)
        last_calls, quiet_since, timed_out = -1, time.time(), False
        while True:
            time.sleep(0.2)
            try:
                v = cdp.call("Runtime.evaluate", expression="window.__probe ? window.__probe.calls : -1",
                             returnByValue=True)["result"].get("value", -1)
            except RuntimeError:
                v = -1
            if v != last_calls or inflight:
                last_calls, quiet_since = v, time.time()
            now = time.time()
            if state["load"] and not inflight and now - quiet_since >= QUIET and now - state["last_net"] >= QUIET:
                break
            if now - started > timeout:
                timed_out = True
                break
        data = json.loads(cdp.call("Runtime.evaluate", expression=COLLECT, returnByValue=True)["result"]["value"])
        if shot:
            png = cdp.call("Page.captureScreenshot", format="png")["data"]
            with open(shot, "wb") as f:
                f.write(base64.b64decode(png))
    finally:
        try:
            cdp.call("Browser.close", timeout=5)
        except Exception:
            pass
        try:
            proc.wait(10)
        except subprocess.TimeoutExpired:
            proc.kill()
        shutil.rmtree(profile_dir, ignore_errors=True)

    probe = data["probe"] or {}
    frames = probe.get("frames") or []
    net = [dict(v, bytes=finished.get(k)) for k, v in responses.items()]
    bundle_end = max((r["responseEnd"] for r in data["resources"]), default=None)
    return {
        "profile": profile_name, "blocked": blocked, "timed_out": timed_out, "stand": before,
        "first_frame_ms": frames[0] if frames else None,
        "settled_ms": frames[-1] if frames else None,
        "first_gl_ms": probe.get("firstGl"), "gl_calls": probe.get("calls", 0), "gl_frames": len(frames),
        "contexts": probe.get("contexts"), "canvas": data["canvas"],
        "html_end_ms": (data["nav"] or {}).get("responseEnd"), "nav_transfer": (data["nav"] or {}).get("transferSize"), "load_event_ms": (data["nav"] or {}).get("load"),
        "last_byte_ms": bundle_end,
        "transferred_bytes": sum(n for n in finished.values() if n),
        "requests": net, "failed": failed, "resources": data["resources"], "accept_encoding": sorted(accepts),
    }


# ---------------------------------------------------------------- summary


def fmt_kb(n):
    return f"{n / 1024:,.0f}" if n is not None else "—"


def size_table(files, requested):
    rows, buckets = [], {}
    for rel in sorted(files, key=lambda r: -len(files[r]["raw"])):
        f = files[rel]
        req = rel in requested
        raw, gz, br = len(f["raw"]), len(f["gzip"] or f["raw"]), len(f["br"] or f["raw"])
        rows.append(f"| `{rel}` | {f['bucket']} | {'yes' if req else 'no'} | {fmt_kb(raw)} | {fmt_kb(gz)} | {fmt_kb(br)} |")
        if req:
            b = buckets.setdefault(f["bucket"], [0, 0, 0])
            b[0] += raw
            b[1] += gz
            b[2] += br
    total = [sum(b[i] for b in buckets.values()) for i in range(3)]
    out = ["| File | Bucket | Requested | Raw KiB | gzip -9 KiB | brotli 11 KiB |", "|---|---|---|---:|---:|---:|"]
    out += rows
    out += ["", "| Bucket (requested files) | Raw KiB | gzip KiB | brotli KiB | Share of brotli |",
            "|---|---:|---:|---:|---:|"]
    for name, b in sorted(buckets.items(), key=lambda kv: -kv[1][2]):
        out.append(f"| {name} | {fmt_kb(b[0])} | {fmt_kb(b[1])} | {fmt_kb(b[2])} | {100 * b[2] / total[2]:.1f} % |")
    out.append(f"| **total** | **{fmt_kb(total[0])}** | **{fmt_kb(total[1])}** | **{fmt_kb(total[2])}** | |")
    return "\n".join(out), total


def spread(xs):
    xs = [x for x in xs if x is not None]
    if not xs:
        return "—"
    return f"{statistics.median(xs):,.0f} ({min(xs):,.0f}–{max(xs):,.0f})"


def med_of(xs):
    xs = [x for x in xs if x is not None]
    return f"{statistics.median(xs):,.0f}" if xs else "—"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dist")
    ap.add_argument("out")
    ap.add_argument("--rounds", type=int, default=7)
    ap.add_argument("--label", default="main")
    ap.add_argument("--identity", action="store_true", help="serve uncompressed")
    ap.add_argument("--profiles", default="none,fast4g,slow4g")
    ap.add_argument("--url", help="measure this running server instead of serving DIST (B-34)")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    chrome = chromium()
    lib, libpath = brotli_lib(chrome)
    files = load_dist(a.dist, lib)
    if a.url:
        srv, seen, url = None, set(), a.url.rstrip("/") + "/"
    else:
        srv, seen = serve(files, a.identity)
        url = f"http://127.0.0.1:{srv.server_address[1]}/"
    version = subprocess.run([chrome, "--version"], capture_output=True, text=True).stdout.strip()
    header = {"label": a.label, "chrome": version, "brotli": libpath, "dist": os.path.abspath(a.dist),
              "identity": a.identity, "served_by": a.url or "this script", "stand": stand(), "kernel": os.uname().release,
              "date": time.strftime("%Y-%m-%dT%H:%M:%S%z")}
    print(json.dumps(header))
    raw = open(os.path.join(a.out, f"{a.label}-runs.jsonl"), "w")
    raw.write(json.dumps({"header": header}) + "\n")

    # Control 1: the probe must stay silent when the bundle cannot run.
    ctl = run_once(chrome, url, "none", ["*.wasm"], 30, os.path.join(a.out, f"{a.label}-control-blocked.png"))
    raw.write(json.dumps({"round": 0, **ctl}) + "\n")
    # What the first frame shows before the fonts arrive: the page with every font blocked stays there.
    nofont = run_once(chrome, url, "none", ["*.ttf"], 30, os.path.join(a.out, f"{a.label}-control-no-fonts.png"))
    raw.write(json.dumps({"round": 0, **nofont}) + "\n")
    probe_ok = ctl["first_frame_ms"] is None and ctl["gl_calls"] == 0
    print(f"control blocked-wasm: gl_calls={ctl['gl_calls']} first_frame={ctl['first_frame_ms']} -> "
          f"{'PASS' if probe_ok else 'FAIL'}")

    profiles = a.profiles.split(",")
    runs = []
    for r in range(1, a.rounds + 1):
        order = profiles[r % len(profiles):] + profiles[: r % len(profiles)]
        for p in order:
            timeout = 60 if p == "none" else 240
            res = run_once(chrome, url, p, False, timeout, os.path.join(a.out, f"{a.label}-r{r}-{p}.png"))
            res["round"] = r
            raw.write(json.dumps(res) + "\n")
            raw.flush()
            runs.append(res)
            print(f"round {r} {p:7s} first={res['first_frame_ms']} settled={res['settled_ms']} "
                  f"last_byte={res['last_byte_ms']} bytes={res['transferred_bytes']} gl={res['gl_calls']} "
                  f"timeout={res['timed_out']} load={res['stand']['loadavg']} mem={res['stand']['mem_available_mb']}",
                  flush=True)
    raw.close()
    if srv:
        srv.shutdown()
    seen |= {e for x in runs for e in x["accept_encoding"]}

    counted = [x for x in runs if x["round"] > 1]
    requested = {u.split("/", 3)[3].split("?")[0] or "index.html"
                 for x in counted for u in (q["url"] for q in x["requests"]) if u.startswith(url)}
    sizes, total = size_table(files, requested)
    encodings = sorted({(q["url"].rsplit("/", 1)[-1] or "index.html", q["encoding"])
                        for x in counted for q in x["requests"]})
    served_headers = sorted({(q["url"].rsplit("/", 1)[-1] or "index.html", q["type"] or "—", q["cache"] or "—",
                              q["vary"] or "—") for x in counted for q in x["requests"]})
    lines = [f"# First load — {a.label}", "", "```", json.dumps(header, indent=1), "```", "",
             "## Sizes", "", sizes, "",
             f"Accept-Encoding the browser sent: {sorted(seen)}", "",
             "Encodings served: " + ", ".join(f"`{n}` {e}" for n, e in encodings), "",
             "| File | Content-Type | Cache-Control | Vary |", "|---|---|---|---|",
             *[f"| `{n}` | {t} | {c} | {v} |" for n, t, c, v in served_headers], "",
             "## Time (ms since navigation start; median (min–max) of the counted rounds)", "",
             f"Rounds {a.rounds}, round 1 discarded; counted per profile: "
             f"{ {p: sum(1 for x in counted if x['profile'] == p) for p in profiles} }", "",
             "| Profile | HTML end | Last byte | First GL call | First frame | Settled | Transferred KiB | GL frames |",
             "|---|---:|---:|---:|---:|---:|---:|---:|"]
    med = {}
    for p in profiles:
        xs = [x for x in counted if x["profile"] == p]
        med[p] = statistics.median([x["first_frame_ms"] for x in xs if x["first_frame_ms"] is not None] or [0])
        lines.append(
            f"| {p} | {spread([x['html_end_ms'] for x in xs])} | {spread([x['last_byte_ms'] for x in xs])} "
            f"| {spread([x['first_gl_ms'] for x in xs])} | {spread([x['first_frame_ms'] for x in xs])} "
            f"| {spread([x['settled_ms'] for x in xs])} | {spread([x['transferred_bytes'] / 1024 for x in xs])} "
            f"| {spread([x['gl_frames'] for x in xs])} |")
    # When each bucket's last byte arrived, against the first frame: what the frame waited for.
    names = sorted({files[r]["bucket"] for r in requested if r in files})
    lines += ["", "When each bucket's last byte arrived (ms, median of the counted rounds):", "",
              "| Profile | " + " | ".join(names) + " | First frame |", "|---|" + "---:|" * (len(names) + 1)]
    for p in profiles:
        xs = [x for x in counted if x["profile"] == p]
        cells = []
        for b in names:
            ends = []
            for x in xs:
                e = [r["responseEnd"] for r in x["resources"]
                     if r["name"].startswith(url) and files.get(r["name"][len(url):], {}).get("bucket") == b]
                if e:
                    ends.append(max(e))
            cells.append(f"{statistics.median(ends):,.0f}" if ends else "—")
        lines.append(f"| {p} | " + " | ".join(cells) + f" | {med_of([x['first_frame_ms'] for x in xs])} |")
    void = [x for x in counted if x["timed_out"] or x["first_frame_ms"] is None or x["failed"]]
    order_ok = all(med[profiles[i]] < med[profiles[i + 1]] for i in range(len(profiles) - 1))
    floor_ms = frame_floor_ms = None
    floor_ok = frame_floor_ok = True
    if "slow4g" in med:
        slow = [x for x in counted if x["profile"] == "slow4g"]
        slow_bytes = statistics.median([x["transferred_bytes"] for x in slow])
        floor_ms = 1000 * slow_bytes / PROFILES["slow4g"]["down"]
        floor_ok = med["slow4g"] >= floor_ms
        # The bytes the first frame can have waited for: everything but the fonts, which Compose
        # fetches in parallel and draws without (the no-fonts control shows what that frame is).
        frame_bytes = statistics.median([
            sum(r["transferSize"] for r in x["resources"]
                if files.get(r["name"][len(url):], {}).get("bucket") != "fonts") + (x["nav_transfer"] or 0)
            for x in slow])
        frame_floor_ms = 1000 * frame_bytes / PROFILES["slow4g"]["down"]
        frame_floor_ok = med["slow4g"] >= frame_floor_ms
    lines += ["", "## Controls", "",
              f"- blocked `.wasm`: GL calls {ctl['gl_calls']}, first frame {ctl['first_frame_ms']} — "
              f"{'PASS' if probe_ok else 'FAIL'}",
              f"- known order {' < '.join(profiles)} in median first frame: {'PASS' if order_ok else 'FAIL'} "
              f"({', '.join(f'{p} {med[p]:,.0f}' for p in profiles)})",
              f"- Slow 4G first frame ≥ all its bytes ÷ bandwidth ({floor_ms or 0:,.0f} ms), as pre-registered: "
              f"{'PASS' if floor_ok else 'FAIL'}",
              f"- Slow 4G first frame ≥ its non-font bytes ÷ bandwidth ({frame_floor_ms or 0:,.0f} ms): "
              f"{'PASS' if frame_floor_ok else 'FAIL'}",
              f"- fonts blocked: first frame {nofont['first_frame_ms']}, settled {nofont['settled_ms']}, "
              f"GL frames {nofont['gl_frames']} (screenshot {a.label}-control-no-fonts.png)",
              f"- void runs (timed out, no frame or a failed request): {len(void)}",
              f"- stand load average per run: {sorted({tuple(x['stand']['loadavg']) for x in runs})[:3]} … "
              f"max 1-min {max(x['stand']['loadavg'][0] for x in runs):.2f}; "
              f"MemAvailable min {min(x['stand']['mem_available_mb'] for x in runs)} MiB"]
    summary = "\n".join(lines) + "\n"
    open(os.path.join(a.out, f"{a.label}-summary.md"), "w").write(summary)
    print(summary)
    return 0 if probe_ok and order_ok and frame_floor_ok and not void else 1


if __name__ == "__main__":
    raise SystemExit(main())
