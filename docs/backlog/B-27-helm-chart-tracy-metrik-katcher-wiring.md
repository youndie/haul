---
id: B-27
title: "ops: Helm chart, tracy / metrik / katcher wiring, the public demo stand"
status: wip
priority: P2
size: M
stage: stage-9-ship
blocked_by: [B-03]
---

# B-27 — ops: Helm chart, tracy / metrik / katcher wiring, the public demo stand

The chart, observability and the public stand (research D6).

- Not covered: synthetic traffic (B-31).

- AC: the chart installs on a clean namespace, the server turns ready, the stand answers at its public host.
- Anchors: `charts/haul/`, `server/src/main/kotlin/io/github/youndie/haul/ops/Observability.kt`,
  `scripts/chart-check.sh`, `.github/workflows/stand.yaml`.

## Iteration 1 (2026-10-08)

Everything short of the deploy. The AC's last clause — «the stand answers at its public host» — needs
a deploy the owner has not approved yet, so the item stays `wip`.

**Done.**

- **The server serves the page.** The distribution carries the wasm bundle under `web/`
  (`server/build.gradle.kts`), the image points `HAUL_WEB_DIR` at it, and `staticFiles("/")` serves it
  beside the API, with no fallback to `index.html` so an unknown `/ui/...` stays a 404
  (`WebBundleTest`). Ktor labels the module `application/wasm` on its own. The image makes its files
  world-readable before the final stage: a replica checkout gave the bundle mode 600, unreadable by
  the image's uid 1000.
- **metrik, tracy, katcher** (`ops/Observability.kt`), the shape the sibling reference services use
  on a Ktor JVM server: each on when `HAUL_<AGENT>_ENDPOINT` and `HAUL_<AGENT>_KEY` are both set, half a
  pair refuses the start (`ServerConfigTest`), each one off says so at `warn`; an unhandled 500 is
  filed with katcher; tracy's delivery and katcher are flushed on `ApplicationStopping`. tracy is
  0.2.15: from 0.3 its Ktor server plugin lives in `agent-ktor-server`, which is not published.
- **The chart** `charts/haul/`: the server Deployment (rolled one pod at a time; start-up and liveness
  on `/healthz`, readiness on `/readyz`; an init container that waits for the database; no service
  links), its Service, PostgreSQL in the release (Deployment + PVC kept on uninstall + Service, the
  siblings' shape — no operator, no subchart), a Secret for the password and the agent keys, and
  Traefik IngressRoutes for the host, as the cluster's other public services have theirs. Refusals at
  render: no image tag, no password, no host, half an agent. `values-stand.yaml` holds the stand's
  choices.
- **Checks.** `scripts/chart-check.sh` — lint, three renders, six refusals each made to fire — runs as
  the `chart` job of `check.yaml`. `scripts/image-check.sh` also asserts the page (`200`) and the
  module's type (`application/wasm`).

**Verified, on the Linux build machine.** The image check: `ready=yes`, 395 of 395 server classes
from the AOT cache, page 200, `application/wasm`. The chart on a throwaway kind cluster (deleted
after), with the stand's values and the image loaded locally: a fresh install ready in 16–21 s with
no restart, under `-XX:AOTMode=on` and the chart's memory limit (so the cache is accepted there);
`/readyz`, `/version`, `/`, `/ui/home` answer 200, `/ui/nowhere` 404; with the database scaled to zero
the pod goes not-ready and is not restarted, and comes back ready; a rolling upgrade with all three
agents on (loopback endpoints) answers throughout. Two defects found there and fixed: the first
version crash-looped three times on a fresh install before the database was up (hence the init
container), and an agent key change recreated the database pod (its checksum now covers the password
only).

**Choices the owner confirms with the deploy.**

- Host `haul.kotlin.website` — the siblings' stands are `<name>.kotlin.website`. Assumption.
- Image `ghcr.io/youndie/haul-server:<commit>`, as the siblings publish; the image carries
  `org.opencontainers.image.source` so the package takes the repository's (public) visibility — if it
  lands private, `server.imagePullSecrets` is where the pull secret goes.
- tracy and katcher at `https://tracy.kotlin.website` and `https://katcher.kotlin.website` (their
  hosts accept writes); metrik left off in the stand's values: its ingest is UDP on a Service inside
  the cluster it runs in, so its address depends on which cluster the stand goes to.
- Which cluster, and its kubeconfig as a repository secret.

**What remains, and the exact steps.** `.github/workflows/stand.yaml` is written and runs **only by
hand** (`workflow_dispatch`): it publishes `ghcr.io/youndie/haul-server:<sha>` after the same
`scripts/image-check.sh`, then, with `deploy: true`, installs and asks the public host which commit
answers. Before the first run the owner creates `STAND_KUBECONFIG`, `STAND_PG_PASSWORD`,
`STAND_TRACY_KEY`, `STAND_KATCHER_KEY` (and `STAND_METRIK_KEY` with the `metrik` input), and a DNS
record for the host pointing at the cluster's Traefik, unless a wildcard already covers it. The deploy it runs:

```bash
helm upgrade --install haul charts/haul --namespace haul --create-namespace \
  -f charts/haul/values-stand.yaml \
  --set server.version=<sha> --set postgres.password=<…> \
  --set observability.tracy.key=<…> --set observability.katcher.key=<…> \
  --atomic --wait --timeout 6m
curl -fsS https://haul.kotlin.website/version   # {"commit":"<sha>"}
```

Every value is passed on every run, none reused (`--reuse-values` renders a key the chart gained
since as empty). Then the AC's clause is checked at the host and the item closes.

Not covered here, noticed on the way: whether `ApplicationStopping` fires on SIGTERM — a stopped pod
exits 143 with no stop line in its log, so the agents' flush on shutdown is unverified; and the
service documents in the open documentation pull request (`haul-server` §5 and §7) do not yet list
`HAUL_WEB_DIR`, `HAUL_ENVIRONMENT`, `HAUL_<AGENT>_ENDPOINT`/`_KEY` and `HAUL_TRACY_SAMPLE_RATE`.
