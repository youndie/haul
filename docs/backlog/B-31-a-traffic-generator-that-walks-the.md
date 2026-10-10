---
id: B-31
title: "synthetic shoppers: a traffic generator that walks the e2e path continuously on the stand"
status: question
priority: P2
size: M
stage: stage-9-ship
blocked_by: [B-26, B-27]
---

# B-31 — synthetic shoppers: a traffic generator that walks the e2e path continuously on the stand

Synthetic shoppers walking the e2e path on the stand, so the stand has traffic to measure (research risk 5).

- Not covered: load testing.

- AC: a stand under it shows sagas and orders in tracy and metrik (research D6).
- Anchors: `e2e/src/main/kotlin/io/github/youndie/haul/e2e/shoppers/`, `charts/haul/templates/shoppers.yaml`,
  `docker/shoppers.Dockerfile`, `scripts/shoppers-check.sh`, `scripts/stand-kind.sh`, `.github/workflows/stand.yaml`.

## Iteration 1 (2026-10-10)

Everything short of the stand: the AC — «a stand under it shows sagas and orders in tracy and metrik» — needs the
owner to turn the shoppers on in the stand's values, which live in the infrastructure repository. Decisions are in
research D6, «Decided in B-31».

**Built.**

- **The walk** (`e2e/src/main/kotlin/io/github/youndie/haul/e2e/shoppers/Walk.kt`): the whole path of
  `WholePathTest`, for traffic — a guest; a random category tile and a random card that can be added; «Add to
  cart» on the product's page; sign-in by the storefront's PKCE flow (`SignIn.kt`, taken out of the test's
  `Shildik`) and the merge; courier checkout with the same address, a random window with room and the first card;
  placement under a fresh `Idempotency-Key`; the order's page read every `poll` until delivered; the first line
  returned for a random reason; the page until «Refunded»; the history. Every address after `/` is one a tree or an
  answer gave, as in the test (`Storefront.kt` moved to `main` for both). A failing step ends the walk with
  «failed at step «…»: …» and the next walk starts on time.
- **The loop** (`Main.kt`): waits for `/readyz` and for the realm the server names to answer its discovery, then
  starts a walk every `interval` as the configured people in turn, at most `inFlight` at once (a start past it is
  skipped and said); one log line per finished walk. A configuration it cannot run exits 2 naming the variable
  (`ShoppersConfig`, `HAUL_SHOPPERS_*`); `HAUL_SHOPPERS_WALKS=n` stops after n and exits 1 on any failure.
- **The image** `ghcr.io/youndie/haul-shoppers:<tag>` (`docker/shoppers.Dockerfile`, the e2e module's
  `installDist`): built and asked to refuse an empty configuration by `scripts/shoppers-check.sh` in CI's `image`
  job; published and pulled back anonymously by `stand.yaml` beside the server's, under the same tag.
- **The chart** 0.2.1: `shoppers.enabled: false` by default; on, one pod walking `http://<release>`, signing in as
  `shoppers.people` (ids of `shildik.demoPeople`, Sam by default) with the Secret `<release>-shildik`'s
  `demo-password` by reference. Refused at render: no `shildik.enabled`, no demo password, a person not in the
  realm, nobody, a rate that is not a positive whole number.

**Tests.** `SyntheticShopperTest` (e2e, against the composed stack): two walks of one fresh customer started
together, both complete, and the customer's own history then holds two orders, both Returned — mutation: the walk
without its merge fails both walks at «check out by courier and place the order» («/ui/cart has 0
haul_cart_body»). `ShoppersConfigTest`: defaults, seconds, each missing variable by name, non-positive rates
refused, the password absent from the printed configuration — mutation: the bound check removed and the password's
`toString` returning it, two tests red. `scripts/chart-check.sh` gained nine lines (two renders, seven assertions on
the shoppers' render) and five refusals; with the password written as a literal and the provider check removed,
three of them failed.

**Verified on the Linux build machine.** `scripts/e2e.sh`: `WholePathTest`, `SyntheticShopperTest` and the config
tests green (the two walks took about 6 s each at a day a second). `scripts/shoppers-check.sh`: the image starts
and refuses. `SHOPPERS=true scripts/stand-kind.sh` on a throwaway kind cluster (deleted after), the stand's shape
plus `shoppers.enabled=true`, speed 8640, a walk every 15 s: B-27's checks all ok, no pod restarted; the shoppers
waited for the realm, then four walks as Sam each placed an order (placed after 0.4–1.1 s, delivered after about
21 s, refunded after about 41 s), none failed; the database then held 7 orders, all `placed`, all Sam's, 7 order
sagas `COMPLETED`, 5 returns and 4 refunds (later walks still in flight). The first run, before the shoppers waited
for the realm, lost its first walk to the hook not having made the realm yet (discovery 404) — hence the wait.

**Not shown locally.** tracy and metrik do not run in kind, so «sagas and orders in tracy and metrik» is read from
the rows they would be about; the server writes no log line per order at `info`, so its spans are the only record
of a walk outside the database. The server's own spans and metrics are already wired on the stand (B-27).

## Findings (2026-10-10)

- **A guest-only walk produces no order**: a guest's «Checkout» goes to sign-in. So the shoppers sign in, and the
  chart refuses them without the demo password rather than running a walk that cannot reach a saga.
- **The cart lock's need is not shown**: two walks of one person started together, without the lock, both passed
  once. It stays (one cart per customer; nothing promises two interleaved merges and placements come out as two
  orders); `SyntheticShopperTest` says so.
- **Every walk takes one unit of stock**, and a refund does not put it back. Seeded stock is 1–199 per SKU over a
  random spread of products, so at the default rate (144 walks a day) a page of a category runs dry in months, not
  days; a walk that finds nothing to add says so.
- **No declined payment**: the test card that always declines (`card-0002`) is not offered by the checkout, so the
  saga's compensation is not part of the traffic.

## Question (2026-10-10)

Everything but the stand is built; the owner turns it on and checks the AC.

**To turn it on**, after this merges: cut the next release tag here — its «publish image» run now publishes
`ghcr.io/youndie/haul-shoppers:<tag>` beside the server and pulls both back anonymously — and in the
infrastructure repository's `k8s/haul/values.yaml`, together with moving the stand to that tag, add

```yaml
shoppers:
  enabled: true
```

Nothing else: the shoppers sign in with `shildik.demoPassword`, which the deploy already passes for Maya and Sam,
and take their image under `server.version`. A tag published before this merge has no shoppers' image, so enabling
them on `v0.1.0` would leave the pod in `ImagePullBackOff`. Optional, in the same block: `people`, `interval`
(seconds, default 600), `inFlight`.

**What to look at afterwards.**

- `kubectl -n haul logs deploy/haul-shoppers`: «waiting for http://haul to be ready…» at most briefly, then about
  every ten minutes one line «walk N as sam@example.com: «…», order HL-… placed after …, delivered after …,
  refunded after …» — about twenty minutes after its start at speed 288. A «failed at step «…»» line names where a
  walk stopped.
- tracy, service `haul-server`, environment `stage`: placement's spans and the order saga's, a walk's requests every
  ten minutes. If `observability.tracy.sampleRate` is not `1.0` on the stand, tail sampling keeps few of them.
- metrik: `haul-server`'s request rates on the cart, checkout and order routes, no longer zero between visits —
  if metrik's ingest is wired on the stand.
- On the stand itself, Sam's order history grows by one returned order per walk.

**Decide:** whether Sam is the right person to walk as (Maya would lose her seeded cart, points and Plus state to
the first walk), and whether the default rate is right. On a yes the item goes back to `wip` until the stand shows
the walks in tracy and metrik, then `done`.
