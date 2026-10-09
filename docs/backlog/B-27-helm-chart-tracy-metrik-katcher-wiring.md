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
- Anchors: `charts/haul/`, `charts/haul/files/shildik-bootstrap.py`,
  `server/src/main/kotlin/io/github/youndie/haul/ops/Observability.kt`, `scripts/chart-check.sh`,
  `scripts/stand-kind.sh`, `.github/workflows/stand.yaml`.

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

## Question (2026-10-08)

Everything but the deploy is merged; the item waits for the owner, who decides:

1. **Deploy the public stand at all?** A deploy is outward-facing and was never approved; merging
   this item deployed nothing (`stand.yaml` runs only by hand).
2. If yes: **which cluster**, and is the host `haul.kotlin.website` right (or covered by a wildcard)?
3. The secrets the first run needs, created by the owner: `STAND_KUBECONFIG`, `STAND_PG_PASSWORD`,
   `STAND_TRACY_KEY`, `STAND_KATCHER_KEY` (and `STAND_METRIK_KEY` if metrik's in-cluster ingest is reachable from that cluster).

On a yes the item goes back to `wip`, the workflow runs with `deploy: true`, and the AC's last clause
is checked against the public host.

**Answered (2026-10-09):** deploy it, through the owner's infrastructure repository, the way the
sibling stands are deployed. Iteration 2 below.

## Iteration 2 (2026-10-09)

The owner decided: the stand is deployed, and from the infrastructure repository that deploys the
sibling stands — it keeps the stand's values, its secrets and the release tag, and deploys when the
tag changes there. Everything on this side is built; the item stays `wip` until the first deploy
answers at the public host (the AC's last clause).

**Decided** (research D6, «Decided in B-27»):

- **Publishing, not deploying, here.** `.github/workflows/stand.yaml` («publish image») runs on a
  pushed tag `v*` (or by hand against a tag ref, refusing any other ref): `scripts/image-check.sh`,
  refusal of a tag already published, push of `ghcr.io/youndie/haul-server:<tag>`, then a second job
  pulls it back **without credentials** — as the cluster will — and walks the e2e path through it
  (`E2E_SKIP_BUILD=true scripts/e2e.sh <image>`). Its deploy step and inputs are gone, and with them
  every secret it read (`STAND_KUBECONFIG`, `STAND_PG_PASSWORD`, `STAND_TRACY_KEY`,
  `STAND_KATCHER_KEY`, `STAND_METRIK_KEY`): none was ever created, and none is needed now.
- **`values-stand.yaml` is removed.** The stand's values are the infrastructure repository's; a copy
  here would be a second list of the same choices. `scripts/chart-check.sh` renders the stand's
  *shape* instead — every part on, placeholder secrets — and asserts what that render says.
- **Hosts**: `haul.kotlin.website` and, for sign-in, `haul-id.kotlin.website` (one label: the
  existing wildcard covers it whatever is recorded under `haul.kotlin.website`).
- **Sign-in: a shildik of the stand's own** in the chart (`shildik.enabled`), the published SQLite
  image 0.4.1, written as templates rather than the provider's `shildik-sqlite` subchart (a plain
  Ingress where the cluster uses IngressRoutes, and a second chart for the deploy to fetch). A
  post-install/post-upgrade hook (`files/shildik-bootstrap.py`) converges the realm `haul` (closed),
  the public client `haul-web` returning to `https://<hostname>/signed-in.html` (re-created when it
  differs), the people `maya` and `sam` with the password `shildik.demoPassword`, and checks that
  discovery names the issuer the server is given. Secrets — `shildik.masterKeys`,
  `shildik.bootstrapToken`, `shildik.demoPassword` — come from the deploy, never from a values file.
- **Who signs in**: a visitor browses and shops as a guest; Maya and Sam sign in with the owner's
  password, which is published nowhere. shildik's password method has no sign-up. Opening sign-in
  to visitors (shared demo credentials shown on the stand, or a sign-up) is not decided here.
- **`server.fulfilmentSpeed`** reaches the server as `HAUL_FULFILMENT_SPEED` (B-17 found the chart
  did not pass it); the stand runs 288, a day in five minutes. Anything but a positive number is
  refused at render.
- **One replica**: `server.replicas` other than 1 is refused at render (the live-update bus is in the
  process, B-29).
- **NetworkPolicies** (`networkPolicy`, default on): PostgreSQL admits only the server; the
  provider's management port admits only the hook; its public port is open.
- **Observability** by the cluster's in-cluster Services, environment `stage`; katcher is left out of
  the first deploy, since `haul-server` is not an app in katcher yet and its key is an app's.
- The chart is **0.2.0**: a refusal added (replicas) and a values file removed.

**Verified, on the Linux build machine.** `scripts/chart-check.sh`: 32 lines ok — two lints, six
renders, ten assertions on the stand-shaped render, thirteen refusals, the hook's script parses.
`scripts/image-check.sh`: ready, 1,047 of 1,047 server classes from the AOT cache, page 200,
`application/wasm`, the bundle precompressed. `scripts/stand-kind.sh` on a throwaway kind cluster
(deleted after), the image loaded and the stand's shape installed with random secrets: installed with
the realm made in 53 s, no pod restarted; the hook said «realm haul: created, closed to strangers»,
«client haul-web: created», both people present, discovery naming the server's issuer; an upgrade with
the same values took 7 s and the hook said «exists», «as wanted»; the script run by hand once more
changed nothing. From a pod in the namespace: `/readyz`, `/` and `/version` 200; `GET /api/v1/sign-in`
named the issuer and `haul-web`; discovery 200; Maya signed in through `haul-web` by the browser's flow
(the provider's page, its form, `302` back to `https://haul.example/signed-in.html`, the code
exchanged), and `/ui/account` with her token answered 200 as Maya, a forged one 401; the provider's
public port reachable from that pod while its management port and the database were not (kindnet
enforces NetworkPolicy; the hook, labelled for it, did reach the management port).

Not proven there: the public hosts, Traefik and the certificates (no ingress in kind, so the issuer was
the provider's Service), and **whether the server's pod reaches `https://haul-id.kotlin.website`** for
the realm's keys — on the cluster that goes out to the public address and back through Traefik. If it
cannot, every sign-in on the stand ends in a `401`; the deploy's last step asks discovery from outside
only. Not run: the e2e (`stand.yaml` runs it on the published image) and helm 4.3.0, which the deploy
uses (the checks ran helm 3.17.0).

**What remains** is the owner's, in order: the secrets, the namespace's RBAC, the tag `v0.1.0` (whose
`stand.yaml` run publishes the image), and the infrastructure change that pins it — whose merge
deploys. Then `https://haul.kotlin.website/version` answers `{"commit":"v0.1.0"}`, the AC's last
clause is checked, and the item closes.
