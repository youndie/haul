#!/usr/bin/env python3
"""B-173's measurement of the server's image: start-up with and without its AOT cache, latency, CPU and memory.

The questions, their lines, the arms and the kill criteria are frozen in
`docs/research/measurements/b-173/brief.md`; this is the harness that brief describes, and nothing is measured
beside it. Linux only (cgroup v2, `/proc`), Docker, k6 and `taskset` on the PATH.

    measure-stack.py startup --out DIR [--rounds 12]       RQ1: five arms interleaved, one run each per round
    measure-stack.py load --out DIR                        stage 0 (ceiling, steps, R, controls), RQ2-RQ4
    measure-stack.py summarise --raw DIR --data FILE       the verdicts and every number, from the raw files
    measure-stack.py summarise --raw DIR --data FILE --check   the committed data is what the raw files say

`startup` and `load` need the image (`IMAGE`, default `haul/server:b173`, built by `scripts/image-build.sh`) and the
commit it was built from (`COMMIT`), and `load` the shoppers' distribution (`./gradlew :e2e:installDist`). They write
only under `--out`. The password of the realm's people and the test card's key are generated per run, handed to the
processes in their environment, and written nowhere.

`summarise` is standard library only, so `make check` can run its `--check`.
"""

from __future__ import annotations

import argparse
import csv
import http.client
import json
import math
import os
import re
import secrets
import statistics
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

IMAGE = os.environ.get("IMAGE", "haul/server:b173")
COMMIT = os.environ.get("COMMIT", "unknown")
ROOT = Path(__file__).resolve().parent.parent

# The stand (brief, «The stand»): every process on cores of its own.
SERVER_CPUS, PG_CPUS, SHILDIK_CPUS, SHOPPERS_CPUS, DRIVER_CPUS = "16-17", "14-15", "12-13", "8-11", "4-7"
SERVER_CORES = (16, 17)
MEMORY = "768m"  # charts/haul/values.yaml, server.resources.limits.memory
CHART_JVM = "-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"  # charts/haul/values.yaml, server.jvmOptions
PORT, PG_PORT, SHILDIK_PORT, SHILDIK_ADMIN_PORT = 18480, 55473, 18490, 18491
ORIGIN = f"http://127.0.0.1:{PORT}"
ISSUER_ORIGIN = f"http://127.0.0.1:{SHILDIK_PORT}"
REALM, CLIENT, REDIRECT = "haul", "haul-web", "http://127.0.0.1/signed-in.html"
SERVER, PG, SHILDIK = "b173-server", "b173-pg", "b173-shildik"
SHILDIK_IMAGE = "ghcr.io/youndie/shildik-sqlite:0.4.1"
BOOTSTRAP = "b173-bootstrap"

FOREIGN = re.compile(r"GradleDaemon|GradleWorkerMain|KotlinCompileDaemon|Runner\.Worker|gradle-wrapper\.jar|org\.gradle\.launcher|konan\.home|\.kexe")
CLOCK_LIMIT = 0.050  # K6
BUSY_LIMIT = 0.5  # K7, cores
TICK = os.sysconf("SC_CLK_TCK")


# -- the box ----------------------------------------------------------------------------------------------------


def sh(*args: str, check: bool = True, capture: bool = True) -> str:
    done = subprocess.run(args, check=check, text=True, capture_output=capture)
    return done.stdout.strip() if capture else ""


def clock() -> float:
    """K6's reading: the wall clock against the monotonic one; a jump or a slow clock moves it."""
    return time.time() - time.monotonic()


def loadavg() -> str:
    return " ".join(Path("/proc/loadavg").read_text().split()[:3])


def _proc_cpu(pid: str) -> int:
    fields = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
    return int(fields[11]) + int(fields[12])  # utime, stime


def foreign_cpu(seconds: float = 2.0) -> float:
    """Cores taken in [seconds] by other builds on the box (Gradle, Kotlin daemons, the CI runner's jobs)."""
    me = {str(os.getpid()), str(os.getppid())}
    pids = []
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit() or entry.name in me:
            continue
        try:
            if FOREIGN.search((entry / "cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")):
                pids.append(entry.name)
        except OSError:
            continue
    before = {}
    for pid in pids:
        try:
            before[pid] = _proc_cpu(pid)
        except OSError:
            pass
    time.sleep(seconds)
    used = 0
    for pid, ticks in before.items():
        try:
            used += _proc_cpu(pid) - ticks
        except OSError:
            pass
    return used / TICK / seconds


def gate() -> dict:
    """K7: wait up to 15 minutes for other builds to leave; then go on, marked busy if they have not."""
    started = time.monotonic()
    while True:
        cores = foreign_cpu()
        waited = time.monotonic() - started
        if cores <= BUSY_LIMIT or waited > 15 * 60:
            return {"foreign_cores": round(cores, 2), "waited_s": round(waited), "busy": cores > BUSY_LIMIT}
        time.sleep(13)


def core_ticks(cores=SERVER_CORES) -> int:
    """Busy ticks of [cores] (user, nice, system, irq, softirq, steal), whoever ran there."""
    total = 0
    for line in Path("/proc/stat").read_text().splitlines():
        name = line.split(" ", 1)[0]
        if name.startswith("cpu") and name[3:].isdigit() and int(name[3:]) in cores:
            f = [int(x) for x in line.split()[1:]]
            total += f[0] + f[1] + f[2] + f[5] + f[6] + f[7]
    return total


def header(out: Path, kind: str) -> None:
    lines = [
        f"# B-173 {kind}, {time.strftime('%Y-%m-%dT%H:%M:%S%z')}",
        f"host: {sh('uname', '-a')}",
        f"nproc (host): {os.cpu_count()}",
        f"image: {IMAGE} {sh('docker', 'image', 'inspect', IMAGE, '--format', '{{.Id}}')}",
        f"commit: {COMMIT}",
        f"docker: {sh('docker', 'version', '--format', '{{.Server.Version}}')}",
        f"k6: {sh('k6', 'version').splitlines()[0]}",
        f"stand: server cpuset {SERVER_CPUS} memory {MEMORY} JAVA_OPTS '{CHART_JVM} -Xlog:aot'; postgres {PG_CPUS}; "
        f"shildik {SHILDIK_CPUS}; shoppers {SHOPPERS_CPUS}; driver and k6 {DRIVER_CPUS}",
        f"loadavg: {loadavg()}",
    ]
    (out / f"{kind}-header.txt").write_text("\n".join(lines) + "\n")


# -- the stack --------------------------------------------------------------------------------------------------


def rm(*names: str) -> None:
    subprocess.run(["docker", "rm", "-f", *names], capture_output=True)


def get(url: str, timeout: float = 2.0, headers: dict | None = None) -> tuple[int, dict]:
    host, _, rest = url.removeprefix("http://").partition("/")
    connection = http.client.HTTPConnection(host, timeout=timeout)
    try:
        connection.request("GET", "/" + rest, headers=headers or {})
        response = connection.getresponse()
        response.read()
        return response.status, dict(response.getheaders())
    finally:
        connection.close()


def status(url: str, timeout: float = 2.0) -> int:
    try:
        return get(url, timeout)[0]
    except OSError:
        return 0


def wait_for(url: str, seconds: float = 120) -> bool:
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        if status(url) == 200:
            return True
        time.sleep(0.25)
    return False


def admin(method: str, path: str, body: dict) -> None:
    request = urllib.request.Request(
        f"http://127.0.0.1:{SHILDIK_ADMIN_PORT}{path}",
        data=json.dumps(body).encode(),
        method=method,
        headers={"Authorization": f"Bearer {BOOTSTRAP}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=10) as answer:
        if not 200 <= answer.status < 300:
            raise RuntimeError(f"{method} {path} answered {answer.status}")


def stack_up(people: int, password: str) -> list[str]:
    """PostgreSQL and shildik, fresh, with the realm, its public client and [people] people; their logins."""
    rm(SERVER, PG, SHILDIK)
    sh("docker", "run", "-d", "--name", PG, "--cpuset-cpus", PG_CPUS, "-p", f"127.0.0.1:{PG_PORT}:5432",
       "-e", "POSTGRES_USER=haul", "-e", "POSTGRES_PASSWORD=haul", "-e", "POSTGRES_DB=haul", "postgres:18-alpine")
    sh("docker", "run", "-d", "--name", SHILDIK, "--cpuset-cpus", SHILDIK_CPUS,
       "-p", f"127.0.0.1:{SHILDIK_PORT}:8080", "-p", f"127.0.0.1:{SHILDIK_ADMIN_PORT}:9000",
       "-e", f"SHILDIK_ISSUER={ISSUER_ORIGIN}", "-e", "SHILDIK_PORT=8080", "-e", "SHILDIK_MANAGEMENT_PORT=9000",
       "-e", "SHILDIK_DB_PATH=/app/shildik.db", "-e", "SHILDIK_MASTER_KEYS=b173-master-key",
       "-e", f"SHILDIK_BOOTSTRAP_TOKEN={BOOTSTRAP}", SHILDIK_IMAGE)
    for _ in range(120):
        if subprocess.run(["docker", "exec", PG, "pg_isready", "-U", "haul", "-d", "haul"], capture_output=True).returncode == 0:
            break
        time.sleep(0.5)
    if not wait_for(f"http://127.0.0.1:{SHILDIK_ADMIN_PORT}/ready", 60):
        raise RuntimeError("shildik did not get ready")
    time.sleep(1)
    admin("POST", "/admin/tenants", {"realm": REALM})
    admin("POST", f"/admin/tenants/{REALM}/clients", {"clientId": CLIENT, "public": True, "redirectUris": [REDIRECT]})
    logins = []
    for n in range(1, people + 1):
        person = f"b173-shopper-{n}"
        admin("POST", f"/admin/tenants/{REALM}/users", {"id": person, "email": f"{person}@example.test", "name": f"Shopper {n}"})
        admin("PUT", f"/admin/tenants/{REALM}/users/{person}/password", {"password": password})
        logins.append(f"{person}@example.test")
    return logins


def server_args(name: str, card_key: str, memory: str = MEMORY, extra_jvm: str = "", entry: list[str] | None = None) -> list[str]:
    """`docker run` for the server, configured as the e2e's ComposedStack configures it, with the stand's limits."""
    args = ["docker", "run", "-d", "--name", name, "--network", "host", "--cpuset-cpus", SERVER_CPUS, "--memory", memory,
            "-e", f"PORT={PORT}", "-e", f"HAUL_DB_URL=jdbc:postgresql://127.0.0.1:{PG_PORT}/haul",
            "-e", "HAUL_DB_USER=haul", "-e", "HAUL_DB_PASSWORD=haul", "-e", "HAUL_SEED=true",
            "-e", f"HAUL_OIDC_ISSUER={ISSUER_ORIGIN}/realms/{REALM}", "-e", f"HAUL_OIDC_CLIENT_ID={CLIENT}",
            "-e", "HAUL_FULFILMENT_SPEED=86400", "-e", "HAUL_ENVIRONMENT=b173", "-e", f"HAUL_COMMIT={COMMIT}",
            "-e", f"HAUL_TEST_CARD_KEY={card_key}",
            "-e", f"JAVA_OPTS={CHART_JVM} -Xlog:aot {extra_jvm}".strip()]
    if entry:
        args += ["--entrypoint", entry[0], IMAGE, *entry[1:]]
    else:
        args.append(IMAGE)
    return args


def cgroup(name: str) -> Path:
    pid = sh("docker", "inspect", "-f", "{{.State.Pid}}", name)
    line = Path(f"/proc/{pid}/cgroup").read_text().strip().splitlines()[-1]
    return Path("/sys/fs/cgroup") / line.split("::", 1)[1].lstrip("/")


def cpu_usec(group: Path) -> int:
    for line in (group / "cpu.stat").read_text().splitlines():
        if line.startswith("usage_usec "):
            return int(line.split()[1])
    raise RuntimeError(f"no usage_usec in {group}")


def memory(group: Path, name: str) -> dict:
    stat = dict(line.split() for line in (group / "memory.stat").read_text().splitlines())
    events = dict(line.split() for line in (group / "memory.events").read_text().splitlines())
    pid = sh("docker", "inspect", "-f", "{{.State.Pid}}", name)
    status_lines = dict(
        line.split(":", 1) for line in Path(f"/proc/{pid}/status").read_text().splitlines() if ":" in line
    )
    return {
        "current_mib": int((group / "memory.current").read_text()) / 2**20,
        "peak_mib": int((group / "memory.peak").read_text()) / 2**20,
        "anon_mib": int(stat["anon"]) / 2**20,
        "file_mib": int(stat["file"]) / 2**20,
        "oom_kill": int(events.get("oom_kill", 0)),
        "vmrss_mib": int(status_lines["VmRSS"].split()[0]) / 1024,
        "threads": int(status_lines["Threads"]),
    }


def logs(name: str) -> str:
    """The container's output, stdout and stderr in the order written."""
    return subprocess.run(["docker", "logs", name], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout


def aot_lines(log: str) -> dict:
    lines = [line for line in log.splitlines() if "[aot" in line]
    return {
        "aot_lines": len(lines),
        "aot_linked": any("Using AOT-linked classes: true" in line for line in lines),
        "aot_opened": any("Opened AOT cache" in line for line in lines),
        "aot_errors": sum(1 for line in lines if "[error][aot" in line or "[warning][aot" in line),
    }


# -- RQ1 --------------------------------------------------------------------------------------------------------

ARMS = {
    "aot": {},
    "no-aot": {"extra_jvm": "-XX:AOTMode=off"},
    "aot-again": {},
    "aot-sleep": {"entry": ["sh", "-c", "sleep 1; exec /opt/app/bin/server"]},
    "empty": {"entry": ["true"]},
}


def start_once(arm: str, card_key: str) -> dict:
    rm(SERVER)
    while status(f"{ORIGIN}/healthz"):
        time.sleep(0.05)
    spec = ARMS[arm]
    c0, t0 = clock(), time.monotonic()
    sh(*server_args(SERVER, card_key, extra_jvm=spec.get("extra_jvm", ""), entry=spec.get("entry")))
    t_run = time.monotonic() - t0
    result = {"arm": arm, "docker_run_ms": round(t_run * 1000, 1), "healthz_ms": None, "readyz_ms": None, "home_ms": None}
    if arm != "empty":
        until = t0 + 120
        while time.monotonic() < until and result["home_ms"] is None:
            tick = time.monotonic()
            if result["healthz_ms"] is None and status(f"{ORIGIN}/healthz") == 200:
                result["healthz_ms"] = round((time.monotonic() - t0) * 1000, 1)
            if result["readyz_ms"] is None and status(f"{ORIGIN}/readyz") == 200:
                result["readyz_ms"] = round((time.monotonic() - t0) * 1000, 1)
            if result["readyz_ms"] is not None and status(f"{ORIGIN}/ui/home", 30.0) == 200:
                result["home_ms"] = round((time.monotonic() - t0) * 1000, 1)
                break
            time.sleep(max(0.0, 0.010 - (time.monotonic() - tick)))
    else:
        sh("docker", "wait", SERVER)
    result["clock_drift_ms"] = round((clock() - c0) * 1000, 2)
    log = logs(SERVER)
    result.update(aot_lines(log))
    result["running"] = sh("docker", "inspect", "-f", "{{.State.Running}}", SERVER) == "true"
    result["_log"] = log
    rm(SERVER)
    return result


def startup(out: Path, rounds: int) -> None:
    out.mkdir(parents=True, exist_ok=True)
    header(out, "startup")
    stack_up(people=0, password="unused")
    card_key = secrets.token_urlsafe(24)
    # The one uncounted start that seeds the database; every counted start is a restart against it.
    seeded = start_once("aot", card_key)
    print(f"seeded: readyz after {seeded['readyz_ms']} ms (not counted)", flush=True)
    rows, logs = [], []
    order = ["aot", "no-aot", "aot-again", "aot-sleep", "empty"]
    for r in range(1, rounds + 1):
        for arm in order:
            g = gate()
            row = {"round": r, **g, "loadavg": loadavg(), "time": time.strftime("%H:%M:%S"), **start_once(arm, card_key)}
            logs.append(f"===== round {r} arm {arm}\n{row.pop('_log')}")
            row["void"] = abs(row["clock_drift_ms"]) > CLOCK_LIMIT * 1000
            rows.append(row)
            print(json.dumps(row), flush=True)
    fields = ["round", "arm", "time", "loadavg", "foreign_cores", "waited_s", "busy", "docker_run_ms", "healthz_ms",
              "readyz_ms", "home_ms", "aot_lines", "aot_opened", "aot_linked", "aot_errors", "running",
              "clock_drift_ms", "void"]
    with (out / "startup.csv").open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    # The [aot] lines and the server's own lines, without the class-by-class noise: what the lever reading read.
    (out / "startup-logs.txt").write_text("\n".join(logs))
    rm(SERVER, PG, SHILDIK)


# -- RQ2-RQ4 ----------------------------------------------------------------------------------------------------


def k6(out: Path, label: str, rate: int, seconds: int, routes: str = "mix") -> dict:
    console, summary = out / f"k6-{label}.txt", out / f"k6-{label}.json"
    console.unlink(missing_ok=True)
    subprocess.run(
        ["taskset", "-c", DRIVER_CPUS, "k6", "run", "--quiet", "--no-usage-report", "--console-output", str(console),
         "-e", f"ORIGIN={ORIGIN}", "-e", f"RATE={rate}", "-e", f"SECONDS={seconds}", "-e", f"ROUND={label}",
         "-e", f"ROUTES={routes}", "-e", f"SUMMARY={summary}", str(ROOT / "scripts/measure-stack.k6.js")],
        check=True, capture_output=True, text=True,
    )
    return json.loads(summary.read_text())


def requests_of(out: Path, label: str) -> list[list[str]]:
    rows = []
    for line in (out / f"k6-{label}.txt").read_text().splitlines():
        m = re.search(r'msg="([^"]*)"', line)
        text = m.group(1) if m else line
        parts = text.split(",")
        if len(parts) == 5 and parts[0] == label:
            rows.append(parts)
    return rows


def walks_in(log: Path, since: int) -> tuple[int, int, int]:
    """Walks that finished, declined as planned, and failed, in the shoppers' log past byte [since]."""
    text = log.read_bytes()[since:].decode(errors="replace")
    finished = len(re.findall(r"^.*walk \d+ as .*refunded after", text, re.M))
    declined = len(re.findall(r"^.*walk \d+ as .*declining card and refused", text, re.M))
    failed = len(re.findall(r"walk \d+ as \S+ failed", text))
    return finished, declined, failed


class Window:
    """One round's readings: the server's and PostgreSQL's CPU, the server cores' busy ticks, the clock."""

    def __init__(self, server: Path, pg: Path):
        self.server, self.pg = server, pg

    def __enter__(self):
        self.c0, self.t0 = clock(), time.monotonic()
        self.s0, self.p0, self.k0 = cpu_usec(self.server), cpu_usec(self.pg), core_ticks()
        return self

    def __exit__(self, *_):
        self.seconds = time.monotonic() - self.t0
        self.server_usec = cpu_usec(self.server) - self.s0
        self.pg_usec = cpu_usec(self.pg) - self.p0
        busy_cores = (core_ticks() - self.k0) / TICK / self.seconds
        self.contamination = max(0.0, busy_cores - self.server_usec / 1e6 / self.seconds)
        self.drift_ms = (clock() - self.c0) * 1000


def load(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    header(out, "load")
    password, card_key = secrets.token_urlsafe(18), secrets.token_urlsafe(24)
    logins = stack_up(people=8, password=password)
    sh(*server_args(SERVER, card_key))
    if not wait_for(f"{ORIGIN}/readyz", 120):
        raise RuntimeError("the server did not get ready")
    server_cg, pg_cg = cgroup(SERVER), cgroup(PG)
    (out / "load-nproc.txt").write_text(f"nproc inside the server: {sh('docker', 'exec', SERVER, 'nproc')}\n")

    shoppers_log = out / "shoppers.log"
    env = {**os.environ, "HAUL_SHOPPERS_ORIGIN": ORIGIN, "HAUL_SHOPPERS_REDIRECT": REDIRECT,
           "HAUL_SHOPPERS_PEOPLE": ",".join(logins), "HAUL_SHOPPERS_PASSWORD": password,
           "HAUL_SHOPPERS_INTERVAL_SECONDS": "2", "HAUL_SHOPPERS_POLL_SECONDS": "1", "HAUL_SHOPPERS_WAIT_SECONDS": "120",
           "HAUL_SHOPPERS_IN_FLIGHT": "16", "HAUL_SHOPPERS_DECLINE_EVERY": "10", "HAUL_SHOPPERS_TEST_CARD_KEY": card_key,
           "JAVA_OPTS": "-Xmx512m"}
    shoppers = subprocess.Popen(["taskset", "-c", SHOPPERS_CPUS, str(ROOT / "e2e/build/install/shoppers/bin/shoppers")],
                                env=env, stdout=shoppers_log.open("wb"), stderr=subprocess.STDOUT)
    rounds: list[dict] = []

    def round_(label: str, kind: str, rate: int = 0, seconds: int = 60, routes: str = "mix") -> dict:
        g = gate()
        since = shoppers_log.stat().st_size
        with Window(server_cg, pg_cg) as w:
            summary = k6(out, label, rate, seconds, routes) if rate else (time.sleep(seconds) or {})
        finished, declined, failed = walks_in(shoppers_log, since)
        row = {"label": label, "kind": kind, "rate": rate, "time": time.strftime("%H:%M:%S"), "loadavg": loadavg(), **g,
               "seconds": round(w.seconds, 2), "server_cpu_usec": w.server_usec, "pg_cpu_usec": w.pg_usec,
               "contamination_cores": round(w.contamination, 3), "clock_drift_ms": round(w.drift_ms, 2),
               "iterations": summary.get("iterations", 0), "dropped": summary.get("dropped", 0),
               "walks_finished": finished, "walks_declined": declined, "walks_failed": failed,
               **{k: round(v, 1) if isinstance(v, float) else v for k, v in memory(server_cg, SERVER).items()}}
        row["void"] = abs(row["clock_drift_ms"]) > CLOCK_LIMIT * 1000
        rounds.append(row)
        print(json.dumps(row), flush=True)
        return row

    try:
        # The shoppers reach the store and sign in before anything is read.
        time.sleep(30)
        # Stage 0: the generator's ceiling, then the steps that choose R (brief, «The operating point R»).
        round_("ceiling-400", "ceiling", 400, 30, "healthz")
        ceiling = rounds[-1]
        ceiling_ok = ceiling["dropped"] <= 0.01 * (ceiling["iterations"] + ceiling["dropped"])
        round_("warm-25", "warm", 25, 30)
        steps, highest = [25, 50, 100, 200, 400], 0
        for step in steps:
            if step == 400 and not ceiling_ok:
                break
            row = round_(f"step-{step}", "step", step, 30)
            reqs = requests_of(out, row["label"])
            durations = sorted(float(r[3]) for r in reqs)
            p99 = durations[max(0, math.ceil(0.99 * len(durations)) - 1)] if durations else math.inf
            bad = sum(1 for r in reqs if not r[2].startswith("2"))
            offered = row["iterations"] + row["dropped"]
            saturated = row["dropped"] > 0.01 * offered or p99 > 1000 or bad > 0.01 * max(1, len(reqs))
            print(f"step {step}: p99 {p99:.1f} ms, dropped {row['dropped']}, non-2xx {bad}, saturated {saturated}", flush=True)
            if saturated:
                break
            highest = step
        rate = max(1, highest // 2)
        (out / "load-rate.txt").write_text(f"R = {rate} rps (half of the highest unsaturated step, {highest}); "
                                           f"generator ceiling at 400 rps cleared: {ceiling_ok}\n")
        print(f"R = {rate}", flush=True)
        # The counted block: seven pairs, the first discarded.
        for pair in range(1, 8):
            round_(f"p{pair}-shoppers", "shoppers", 0)
            round_(f"p{pair}-gets", "gets", rate)
        # The readers' known-order control: /healthz alone at R, beside one more pair.
        round_("c-shoppers", "shoppers", 0)
        round_("c-healthz", "control", rate, 60, "healthz")
        aot = aot_lines(logs(SERVER))
        state = sh("docker", "inspect", "-f", "{{.State.Running}} {{.State.OOMKilled}} {{.State.ExitCode}} {{.RestartCount}}", SERVER)
        (out / "load-server.txt").write_text(f"aot: {json.dumps(aot)}\nstate (running oomkilled exitcode restarts): {state}\n")
        rm(SERVER)
        # The survival control (brief, stage 0.4): the same image and load under a small limit must die.
        for limit in ["192m", "128m"]:
            name = f"b173-survival-{limit}"
            rm(name)
            t0 = time.monotonic()
            sh(*server_args(name, card_key, memory=limit))
            ready = wait_for(f"{ORIGIN}/readyz", 120)
            died_at, summary = None, {}
            if ready:
                proc = subprocess.Popen(["taskset", "-c", DRIVER_CPUS, "k6", "run", "--quiet", "--no-usage-report",
                                         "--console-output", str(out / f"k6-survival-{limit}.txt"),
                                         "-e", f"ORIGIN={ORIGIN}", "-e", f"RATE={rate}", "-e", "SECONDS=120",
                                         "-e", f"ROUND=survival-{limit}", "-e", "ROUTES=mix",
                                         "-e", f"SUMMARY={out / f'k6-survival-{limit}.json'}",
                                         str(ROOT / "scripts/measure-stack.k6.js")],
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                while proc.poll() is None:
                    if sh("docker", "inspect", "-f", "{{.State.Running}}", name, check=False) != "true":
                        died_at = time.monotonic() - t0
                        break
                    time.sleep(0.5)
                proc.wait()
            state = sh("docker", "inspect", "-f", "{{.State.Running}} {{.State.OOMKilled}} {{.State.ExitCode}}", name, check=False)
            text = logs(name)
            oome = "OutOfMemoryError" in text
            line = (f"limit {limit}: ready={ready} died_after_s={None if died_at is None else round(died_at, 1)} "
                    f"state(running oomkilled exitcode)={state} OutOfMemoryError_in_log={oome}")
            print(line, flush=True)
            with (out / "load-survival.txt").open("a") as f:
                f.write(line + "\n")
                f.write("\n".join(l for l in text.splitlines() if "OutOfMemory" in l or "Terminating" in l)[:2000] + "\n")
            rm(name)
            if not state.startswith("true"):
                break
    finally:
        shoppers.terminate()
        try:
            shoppers.wait(10)
        except subprocess.TimeoutExpired:
            shoppers.kill()
        fields = list(rounds[0].keys()) if rounds else []
        with (out / "load-rounds.csv").open("w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=fields, lineterminator="\n")
            w.writeheader()
            w.writerows(rounds)
        # One line per request of every k6 run, in the order they ran, kept on the box; and their histogram, which
        # is what the repository keeps and the percentiles are read from.
        with (out / "load-requests.csv").open("w") as f:
            f.write("round,route,status,client_ms,server_ms\n")
            for row in rounds:
                if row["rate"]:
                    for r in requests_of(out, row["label"]):
                        f.write(",".join(r) + "\n")
        histogram(out / "load-requests.csv", out / "load-latency.csv")
        rm(SERVER, PG, SHILDIK, "b173-survival-192m", "b173-survival-128m")


def bucket(ms: float) -> str:
    """[ms] to three significant figures (12.3, 184, 3740): a percentile read from it is off by under 0.5 %."""
    if ms <= 0:
        return "0"
    digits = 2 - math.floor(math.log10(ms))
    return f"{round(ms, digits):.{max(0, digits)}f}"


def histogram(requests: Path, latency: Path) -> None:
    """The per-request lines as counts: round, route, status, which time (client or server), the bucket, how many."""
    counts: dict[tuple, int] = {}
    order: list[tuple] = []
    for r in csv.DictReader(requests.open()):
        for kind, field in (("client", "client_ms"), ("server", "server_ms")):
            if r[field]:
                key = (r["round"], r["route"], r["status"], kind, bucket(float(r[field])))
                if key not in counts:
                    order.append(key)
                    counts[key] = 0
                counts[key] += 1
    with latency.open("w") as f:
        f.write("round,route,status,kind,ms,count\n")
        for key in sorted(order, key=lambda k: (k[0], k[1], k[2], k[3], float(k[4]))):
            f.write(",".join(key) + f",{counts[key]}\n")


# -- summarise --------------------------------------------------------------------------------------------------

T975 = {1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571, 6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262, 10: 2.228,
        11: 2.201, 12: 2.179, 13: 2.160, 14: 2.145, 15: 2.131, 20: 2.086, 30: 2.042}


def t975(df: int) -> float:
    return T975.get(df) or T975[min(T975, key=lambda k: abs(k - df))]


def paired(a: list[float], b: list[float]) -> dict:
    """mean(a − b) with its 95 % half-interval, over pairs of one round."""
    d = [x - y for x, y in zip(a, b)]
    n = len(d)
    mean = statistics.fmean(d)
    half = t975(n - 1) * statistics.stdev(d) / math.sqrt(n) if n > 1 else math.inf
    return {"n": n, "mean": mean, "half": half}


def pct(values: list[float], q: float) -> float:
    s = sorted(values)
    return s[max(0, math.ceil(q * len(s)) - 1)]


def summarise(raw: Path) -> dict:
    data: dict = {"rq": {}}
    head = (raw / "startup" / "startup-header.txt").read_text()
    data["measured"] = re.search(r"# B-173 startup, (\S+)", head).group(1)[:10]
    data["commit"] = re.search(r"commit: (\S+)", head).group(1)
    data["image"] = re.search(r"image: \S+ (\S+)", head).group(1)

    # RQ1
    rows = list(csv.DictReader((raw / "startup" / "startup.csv").open()))
    counted = [r for r in rows if int(r["round"]) > 1]
    rounds = sorted({int(r["round"]) for r in counted})

    def arm(name: str, field: str) -> list[float]:
        by = {int(r["round"]): r for r in counted if r["arm"] == name}
        return [float(by[n][field]) for n in rounds]

    void = sum(1 for r in counted if r["void"] == "True")
    busy = sum(1 for r in counted if r["busy"] == "True")
    lever_on = all(r["aot_linked"] == "True" and r["aot_errors"] == "0" for r in counted if r["arm"] in ("aot", "aot-again", "aot-sleep"))
    lever_off = all(r["aot_lines"] == "0" for r in counted if r["arm"] == "no-aot")
    running = all(r["running"] == "True" for r in counted if r["arm"] != "empty")
    control = paired(arm("aot-sleep", "readyz_ms"), arm("aot", "readyz_ms"))
    control_ok = abs(control["mean"] - 1000) <= 250
    startup_out = {"rounds": len(rounds), "void": void, "busy": busy, "lever_on": lever_on, "lever_off": lever_off,
                   "control_ms": round(control["mean"]), "control_half_ms": round(control["half"]), "control_ok": control_ok,
                   "docker_run_ms": round(statistics.median(arm("empty", "docker_run_ms")))}
    for key, field in (("rq1a", "readyz_ms"), ("rq1b", "home_ms")):
        aot, off, again = arm("aot", field), arm("no-aot", field), arm("aot-again", field)
        effect = paired(off, aot)
        ruler = paired(aot, again)
        off_mean = statistics.fmean(off)
        share, share_half, ruler_share = effect["mean"] / off_mean, effect["half"] / off_mean, ruler["half"] / off_mean
        distinguishable = abs(effect["mean"]) > effect["half"]
        k1 = ruler_share > 0.15
        green = (distinguishable and effect["mean"] > 0 and share >= 0.15 and share >= 2 * ruler_share
                 and lever_on and lever_off and control_ok and running)
        red = (not distinguishable) or effect["mean"] <= 0 or not lever_on
        verdict = "not measured" if (k1 or not control_ok or void * 3 > len(counted)) else (
            "red" if red else "green" if green and busy * 3 <= len(counted) else "grey")
        data["rq"][key] = {
            "verdict": verdict, "aot_median_ms": round(statistics.median(aot)), "no_aot_median_ms": round(statistics.median(off)),
            "aot_mean_ms": round(statistics.fmean(aot)), "no_aot_mean_ms": round(off_mean),
            "saved_ms": round(effect["mean"]), "saved_half_ms": round(effect["half"]),
            "saved_share": round(share, 3), "saved_share_half": round(share_half, 3),
            "ruler_ms": round(ruler["mean"]), "ruler_half_ms": round(ruler["half"]), "ruler_share": round(ruler_share, 3),
            "distinguishable": distinguishable, "k1": k1,
        }
    # `/healthz` is read before `/readyz` in each poll, so a server that came up between the two has no time of its
    # own for it: reported over the runs that have one, with their count; it decides nothing.
    for name, key in (("aot", "healthz_aot"), ("no-aot", "healthz_no_aot")):
        times = [float(r["healthz_ms"]) for r in counted if r["arm"] == name and r["healthz_ms"]]
        data["rq"]["rq1a"][f"{key}_median_ms"] = round(statistics.median(times))
        data["rq"]["rq1a"][f"{key}_n"] = len(times)
    data["startup"] = startup_out

    # RQ2-RQ4, per campaign of the load block (raw/load-<n>/)
    data["load"] = {d.name.removeprefix("load-"): load_campaign(d) for d in sorted(raw.glob("load-*")) if d.is_dir()}
    return data


def weighted_pct(pairs: list[tuple[float, int]], q: float) -> float:
    """The nearest-rank percentile of a histogram: (value, count) pairs."""
    pairs = sorted(pairs)
    total = sum(c for _, c in pairs)
    rank, seen = max(1, math.ceil(q * total)), 0
    for value, count in pairs:
        seen += count
        if seen >= rank:
            return value
    return pairs[-1][0]


def load_campaign(raw: Path) -> dict:
    """RQ2-RQ4 of one load block, by the brief's rules, from its rounds, its latency histogram and its controls."""
    load_rows = list(csv.DictReader((raw / "load-rounds.csv").open()))
    rate = int(re.search(r"R = (\d+)", (raw / "load-rate.txt").read_text()).group(1))
    pairs = [(next(r for r in load_rows if r["label"] == f"p{p}-shoppers"), next(r for r in load_rows if r["label"] == f"p{p}-gets"))
             for p in range(2, 8)]
    labels = sorted(b["label"] for _, b in pairs)
    hist = list(csv.DictReader((raw / "load-latency.csv").open()))

    def series(route: str, kind: str, rounds: list[str]) -> list[tuple[float, int]]:
        return [(float(h["ms"]), int(h["count"])) for h in hist if h["route"] == route and h["kind"] == kind and h["round"] in rounds]

    routes = ["home", "category", "product", "suggest", "live"]
    per_route, round_p99_over = {}, False
    for route in routes:
        client, server = series(route, "client", labels), series(route, "server", labels)
        per_round = [weighted_pct(series(route, "client", [lab]), 0.99) for lab in labels]
        round_p99_over |= max(per_round) > 250
        per_route[route] = {
            "n": sum(c for _, c in client), "p50_ms": weighted_pct(client, 0.5), "p99_ms": weighted_pct(client, 0.99),
            "server_p50_ms": weighted_pct(server, 0.5) if server else None,
            "server_p99_ms": weighted_pct(server, 0.99) if server else None,
            "round_p50_ms": [weighted_pct(series(route, "client", [lab]), 0.5) for lab in labels],
            "round_p99_ms": per_round,
            "non_2xx": sum(int(h["count"]) for h in hist if h["route"] == route and h["kind"] == "client"
                           and h["round"] in labels and not h["status"].startswith("2")),
        }
    requests = sum(v["n"] for v in per_route.values())
    offered = sum(int(b["iterations"]) + int(b["dropped"]) for _, b in pairs)
    delivered = sum(int(b["iterations"]) for _, b in pairs)
    non2xx = sum(v["non_2xx"] for v in per_route.values())
    walks = sum(int(a["walks_finished"]) + int(b["walks_finished"]) for a, b in pairs)
    walks_failed = sum(int(a["walks_failed"]) + int(b["walks_failed"]) for a, b in pairs)
    load_void = sum(1 for a, b in pairs for r in (a, b) if r["void"] == "True")
    load_busy = sum(1 for a, b in pairs for r in (a, b) if r["busy"] == "True")
    control_row = next(r for r in load_rows if r["label"] == "c-healthz")
    control_base = next(r for r in load_rows if r["label"] == "c-shoppers")
    ctrl_p50 = weighted_pct(series("healthz", "client", ["c-healthz"]), 0.5)
    ctrl_server_timing = sum(c for _, c in series("healthz", "server", ["c-healthz"]))

    def marginal(a: dict, b: dict, field: str = "server_cpu_usec") -> float:
        rate_a = int(a[field]) / float(a["seconds"])
        rate_b = int(b[field]) / float(b["seconds"])
        return (rate_b - rate_a) * float(b["seconds"]) / int(b["iterations"])

    cpu_each = [marginal(a, b) for a, b in pairs]
    pg_each = [marginal(a, b, "pg_cpu_usec") for a, b in pairs]
    cpu = paired(cpu_each, [0.0] * len(cpu_each))
    ctrl_cpu = marginal(control_base, control_row)
    k4_latency = not all(ctrl_p50 < v["p50_ms"] for v in per_route.values()) or ctrl_server_timing > 0
    k4_cpu = not ctrl_cpu < cpu["mean"]
    worst_p50 = max(v["p50_ms"] for v in per_route.values())
    worst_p99 = max(v["p99_ms"] for v in per_route.values())
    rq2_green = (worst_p50 <= 50 and worst_p99 <= 250 and not round_p99_over and non2xx <= 0.001 * requests
                 and delivered >= 0.99 * offered and walks > 0)
    rq2_red = worst_p99 > 1000 or non2xx > 0.01 * requests or delivered < 0.95 * offered
    too_many_void = load_void * 3 > 2 * len(pairs)
    capped = load_busy * 3 > 2 * len(pairs)
    out: dict = {"rate": rate, "rounds": len(pairs), "busy": load_busy, "void": load_void}
    out["rq2"] = {
        "verdict": "not measured" if (k4_latency or too_many_void) else "red" if rq2_red else "green" if rq2_green and not capped else "grey",
        "rate": rate, "rounds": len(pairs), "seconds": round(statistics.fmean(float(b["seconds"]) for _, b in pairs)),
        "requests": requests, "offered": offered, "delivered": delivered, "delivered_share": round(delivered / offered, 4),
        "non_2xx": non2xx, "walks_finished": walks, "walks_failed": walks_failed, "routes": per_route,
        "control_healthz_p50_ms": ctrl_p50, "control_server_timing": ctrl_server_timing,
        "round_loadavg": [float(b["loadavg"].split()[0]) for _, b in pairs],
        "round_contamination_cores": [float(b["contamination_cores"]) for _, b in pairs],
        "round_delivered_share": [round(int(b["iterations"]) / (int(b["iterations"]) + int(b["dropped"])), 4) for _, b in pairs],
        "round_walks_failed": [int(a["walks_failed"]) + int(b["walks_failed"]) for a, b in pairs],
    }
    out["rq3"] = {
        "verdict": "not measured" if (k4_cpu or too_many_void) else (
            "red" if cpu["mean"] >= 50_000 else "green" if cpu["mean"] <= 10_000 and cpu["half"] < 0.2 * cpu["mean"] and not capped else "grey"),
        "server_usec": round(cpu["mean"]), "server_half_usec": round(cpu["half"]),
        "round_server_usec": [round(x) for x in cpu_each], "round_pg_usec": [round(x) for x in pg_each],
        "pg_usec": round(statistics.fmean(pg_each)), "control_healthz_usec": round(ctrl_cpu),
        "shoppers_only_server_cores": round(statistics.fmean(int(a["server_cpu_usec"]) / float(a["seconds"]) / 1e6 for a, _ in pairs), 3),
    }
    block = [r for r in load_rows if r["kind"] in ("shoppers", "gets", "control")]
    peak = max(float(r["peak_mib"]) for r in block)
    oom = max(int(r["oom_kill"]) for r in block)
    state = (raw / "load-server.txt").read_text()
    exited = "state (running oomkilled exitcode restarts): true false 0 0" not in state
    survival_file = raw / "load-survival.txt"
    survival = survival_file.read_text() if survival_file.exists() else ""
    died = bool(re.search(r"died_after_s=\d|state\(running oomkilled exitcode\)=false", survival))
    limit = 768
    rq4_red = oom > 0 or exited or peak > 0.95 * limit
    rq4_green = peak <= 0.80 * limit and not rq4_red and died
    out["rq4"] = {
        "verdict": "red" if rq4_red else "green" if rq4_green else "grey",
        "limit_mib": limit, "peak_mib": round(peak), "peak_share": round(peak / limit, 3),
        "anon_max_mib": round(max(float(r["anon_mib"]) for r in block)),
        "vmrss_max_mib": round(max(float(r["vmrss_mib"]) for r in block)),
        "threads_max": max(int(r["threads"]) for r in block), "oom_kill": oom, "exited": exited,
        "control_ran": bool(survival), "control_died": died,
    }
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)
    s = sub.add_parser("startup")
    s.add_argument("--out", type=Path, required=True)
    s.add_argument("--rounds", type=int, default=12)
    l = sub.add_parser("load")
    l.add_argument("--out", type=Path, required=True)
    c = sub.add_parser("compact")
    c.add_argument("--requests", type=Path, required=True)
    c.add_argument("--out", type=Path, required=True)
    m = sub.add_parser("summarise")
    m.add_argument("--raw", type=Path, required=True)
    m.add_argument("--data", type=Path, required=True)
    m.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.command == "startup":
        startup(args.out, args.rounds)
    elif args.command == "load":
        load(args.out)
    elif args.command == "compact":
        histogram(args.requests, args.out)
    else:
        data = summarise(args.raw)
        text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
        if args.check:
            if args.data.read_text() != text:
                print(f"{args.data} is not what {args.raw} says: run summarise without --check and look at the diff",
                      file=sys.stderr)
                return 1
            print(f"{args.data}: what {args.raw} says")
        else:
            args.data.write_text(text)
            print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
