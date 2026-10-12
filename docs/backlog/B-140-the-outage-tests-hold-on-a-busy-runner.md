---
id: B-140
title: "test: the database outage tests hold on a busy runner"
status: wip
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
