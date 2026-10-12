---
id: B-140
title: "test: the database outage tests hold on a busy runner"
status: done
priority: P1
size: S
stage: stage-12-qa-run
---

# B-140 — test: the database outage tests hold on a busy runner

B-139's `PlacementOutageTest` failed once in CI — on haul#169's pull-request run of `0064f9af`, while the push run of
the same commit, both runs of B-139's own head and main's run after it passed: «a placement rides out a five second
outage and the order is placed» answered `500 internal` where it expects `202` (`PlacementOutageTest.kt:78`), and in the
same run, a second later, `PlacementRestartTest` «a placement killed inside the payment is finished by the next
process()» failed on `HikariDataSource (HikariPool-107) has been closed`. A test that fails one run in five blocks every
pull request it lands on (the merge gate refuses any red run on the head commit).

- **The two failures are read together first**: whether the restart test's pool was closed by the outage test (a shared
  harness, a fixed host port, a container stopped under another test's pool, parallel test classes) or by something
  else. The fix is the cause, not a retry: a test that must be retried to pass is a test that does not say what it
  holds.
- **The five-second case keeps its claim with a margin the runner cannot eat**: the outage, the phase timeout and the
  pass's patience measured against the runner's real clock (the hosted runner is slower than the build box), so the
  test still distinguishes «rode it out» from «compensated» — not a longer sleep that hides the line.

- AC: the cause of both failures written in the findings with the evidence; `:server:test --tests '*Placement*'` and
  `*Outage*` run 20 times in a row on the build box under CPU pressure (e.g. `stress-ng` or a parallel build) without a
  failure; the mutation that B-139's test caught (no saga marker) still turns it red.
- Anchors: `server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementOutageTest.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/feature/order/PlacementRestartTest.kt`.

## Findings

- **The two failures are one chain.** The first is a defect of B-139's patience, the second a test that went on
  running after the first had failed.
- **The five second case failed because saga work was handed connections the outage had already closed.** The
  outage timeline the test now prints put it beyond doubt: on a hosted runner (this branch's CI, attempt 3 of run
  38303404606) PostgreSQL was asked to stop, asked to start at 5.0 s, accepted connections at 5.15 s — and the
  placement answered `500` at 5.16 s, 11 ms later. Not the member's 10 s phase: the member had failed at once, and
  the compensation, whose writes do wait for the database, finished the moment it came back. Why at once: Hikari
  does not check a connection used within the last 500 ms (`aliveBypassWindowMs`), so the pool hands back the
  connections the shutdown terminated, and each fails at its first statement (`57P01`) — after the ask, where
  B-139's patience is not. Exposed tries a transaction three times; the three went on dead connections in
  milliseconds. Reproduced on the build box by a burst of pages just before the stop (every connection then used
  within the window): both cases answered `503` in about 2 s, the box's logs showing `reserve-stock`'s tries
  failing on `57P01` one after another; the same with Hikari's window widened by its system property. The build
  box never met it on its own because Docker there takes 0.5–0.9 s to stop the container — past the window — where
  the hosted runner takes 0.16–0.53 s. The item's premise that the hosted runner is the slower one was wrong for
  this test: it is the faster, and its speed is what put the stop inside the window.
- **Fixed in the product, not the test**: under `ConnectionPatience` a connection is asked whether it is alive
  (`isValid`, one round trip) and a dead one is evicted from the pool and the ask made again
  (`FailFastDataSource`). Everybody else keeps Hikari's own check and fails as before. Rejected: more Exposed
  tries (a pool of ten dead connections outlasts any count, and it hides the line); Hikari's window lowered for
  the JVM (a system property read once at class load, and a round trip on every page's ask); a retry of the test.
- **The restart test failed on the outage test's pool.** Ktor's `testApplication` stops the application only
  after a block that returned (`runTestApplication` has no `finally`, ktor 3.6.0). The outage test's failed
  assertion left its application running, Koin graph included, after `StoppablePostgres.close()` had closed the
  pool. The feature modules are shared values whose singles keep what they made until a graph is closed, so
  `PlacementRestartTest`'s first process — a `koinApplication` over the same modules — was handed the dead test's
  `ExposedCartRepository` on the closed `HikariPool`: its `CheckoutCommands.quote` read the cart through a
  `FailFastDataSource`, which that process never builds. Reproduced by forcing the five second case to fail (a
  9 s outage): the restart test failed on `HikariPool-2 has been closed` every time; with `haulTest` stopping the
  application when its block throws, it passes while the outage test fails. B-18's findings had seen the same
  leak «only under a mutation». `ErrorAnswersTest`, `WebBundleTest` and `FulfilmentRunnerTest` build `haulModule`
  under a bare `testApplication` and would leak the same way on a failure; not changed here.
- **The outage is now the one the saga meets, on the runner's clock**: counted from the stop being asked for (the
  waiting statement dies then and Hikari's tries start) to PostgreSQL accepting a connection, not from Docker
  returning to Docker being asked to start. Each case checks the outage it got against the member's phase before
  the saga's answer, the answer's message carries the timeline, and the timeline is printed to the build log, so
  a hosted run says what its runner did. Hikari's sleeps between tries reach 0.64 s and 1.28 s past about 7 s of
  outage, so on the saga's clock the line is nearer 9 s than 10 s: a database back 9.8 s after the stop began was
  undone (box). Five seconds sits well inside it on both machines.
- **Measured** (the final code, a burst of pages before every stop). Build box, 20 runs in a row of
  `:server:test --tests '*Placement*' --tests '*Outage*'` (WSL, the Gradle scope at `CPUQuota=200%`, 20 busy loops
  on the box's 20 cores), all green: 5 s asked — the stop took 0.63–0.93 s, PostgreSQL back at 5.87–6.09 s, placed
  6.71–6.88 s after the stop began; 12 s asked — back at 12.91–13.26 s, undone, answered 13.56–13.65 s. Hosted
  runner (this branch's push run of 64288eea): the stop 0.36 s, back at 5.16 s, placed 5.19 s; across eleven runs
  printing the timeline the stop took 0.16–0.53 s and PostgreSQL was back 0.15–0.25 s after the start was asked for.
- **Seen on the way**: the `image` job of this branch's first run failed in `kotlinWasmStoreYarnLock` (`build/wasm/
  yarn.lock` missing) and passed on the rerun — not this item's.
