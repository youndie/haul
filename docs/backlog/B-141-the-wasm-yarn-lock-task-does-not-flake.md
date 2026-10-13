---
id: B-141
title: "ci: kotlinWasmStoreYarnLock does not fail on a missing build/wasm/yarn.lock"
status: done
priority: P2
size: S
stage: stage-12-qa-run
---

# B-141 — ci: kotlinWasmStoreYarnLock does not fail on a missing build/wasm/yarn.lock

Three CI runs failed on hosted runners with the same error and passed on a rerun of the same commit: «A problem was
found with the configuration of task ':kotlinWasmStoreYarnLock' … property 'inputFile' specifies file
'/home/runner/work/haul/haul/build/wasm/yarn.lock' which doesn't exist» — the `e2e` job of B-120's push run
(`6d54bc01`, run 38185237842), the `image` job of B-140's first run, and once before. A job that fails one run in a
few for a reason outside the code costs every pull request a rerun and teaches the loop to rerun reds.

- **Find why the lock file is sometimes not there** when the store task runs — the Gradle cache restored by
  `gradle/actions/setup-gradle` (task outputs restored without the file, or the configuration cache reusing a graph
  whose `kotlinNpmInstall` was skipped), the task order in the jobs that do not build the wasm bundle themselves, or
  the Kotlin/Wasm Gradle plugin's own up-to-date check — and fix the cause, not a retry.
- Rejected: `continue-on-error` or a rerun step — they hide the next real failure of the same task.

- AC: the cause written in the findings with evidence (a reproduction on a clean checkout, or the runs' logs showing
  the restored cache); the jobs that ran into it do not depend on the file's presence by luck; the fix holds on a
  number of fresh CI runs (re-run the workflow several times on the branch) with no `kotlinWasmStoreYarnLock` failure.
- Anchors: `.github/workflows/check.yaml`, `composeApp/build.gradle.kts`.

## Findings

- **The lock file is missing because the install never ran: yarn exited 0 without installing.** The wasm target
  runs yarn twice, from two root-project tasks of the Kotlin plugin (2.4.20): `kotlinWasmNpmInstall` (the bundle's
  packages, into `build/wasm`) and `kotlinWasmToolingSetup` (webpack and the rest, into `~/.kotlin`). Gradle may
  run them at once, and the plugin passes both `--mutex network`: the second yarn finds port 31997 taken, asks the
  holder its name over HTTP and then waits on an open socket to it. The holder is a Node HTTP server, and Node
  (26.2.0 here, the plugin's) closes a connection that has sent no request once `headersTimeout` passes — 60 s,
  checked every 30 s. The waiter's socket closes, its `startServer()` sets up a server that is `unref`'d, nothing
  else holds its event loop, and the process ends with code 0: no `node_modules`, no `yarn.lock`, a task the plugin
  reports as done, and `kotlinWasmStoreYarnLock` failing on the missing input. Not the Gradle cache (no task with
  an output over `build/wasm/yarn.lock` runs after the install, and a trace shows nothing deleting it), not a
  configuration cache entry reused from elsewhere (every failure was the first Gradle invocation on a fresh
  checkout), and not the store task's order (it does depend on the install; in a cold build Gradle runs it late,
  after the wasm compiles, which is why the failures came 2–2.5 minutes into the step).
- **Why a hosted runner, and why rarely.** The runner starts with no `~/.kotlin` and an empty yarn cache, so the
  tooling install fetches webpack's tree one package at a time (`--network-concurrency 1`): about 30 s after the
  bundle's install in the gradle job's logs. The bundle's install usually starts first and takes a second or two;
  the trap needs the tooling install to start first **and** to hold the mutex past the timeout. In the 31 gradle-
  job logs at hand, one (run 38299048100, job 114977178340) shows the bundle's install waiting 30 s on the tooling
  one and surviving; the three failures (runs 37850387217 `image`, 38185237842 `e2e`, 38303404606 `image`, all
  `-q` with `GRADLE_BUILD_ACTION_CACHE_RESTORED: true` and a restore-key hit) are the waits that ran past it. Over
  the ~950 runs since 08.10 that is three in roughly 1,300 executions of the two jobs; the gradle job, which runs
  the same two installs, never hit it, which at that rate is not a difference.
- **Reproduced.** Minimal, on the build box with the plugin's own Node and yarn: a server holding yarn's port the
  way yarn does, and yarn 1.22.22 run in a copy of `build/wasm` with the plugin's arguments. Held for 30 s, the
  waiter installs and writes the lock file; held for 150 s, it lives 90 s and exits 0 with neither `node_modules`
  nor `yarn.lock`. In the build: a fresh clone, a fresh `-Pkotlin.user.home` and yarn cache, `--no-build-cache`,
  four workers, `:server:installDist -q` under `strace`: CI's failure word for word in two of seven runs. The trace
  of one: the bundle's yarn gets `EADDRINUSE` on 31997 at 01:03:12, connects to the tooling yarn, and exits 0 at
  01:04:42 — 90 s later, never having bound the port — while the tooling yarn runs until 01:05:23. Of the five
  traced runs that passed, the two yarns overlapped in two — the bundle's starting first both times.
- **The positive control** makes the trap certain instead of a matter of order: an init script holds yarn's port
  for 120 s at the start of `kotlinWasmToolingSetup` (a tooling install slower than the timeout) and starts
  `kotlinWasmNpmInstall` 5 s late; `:kotlinWasmStoreYarnLock :kotlinWasmToolingSetup` on a fresh clone, offline,
  in a private network namespace (yarn's port is the machine's, and other builds share the box). Interleaved,
  three runs each: `main` (526f645e) failed 3 of 3 with CI's message and no `node_modules`; this branch passed
  3 of 3.
- **Only the configuration cache lets the two overlap**: they are tasks of one project, which Gradle runs in
  parallel only with the configuration cache on (`org.gradle.configuration-cache=true` here).
- **Fixed by not letting the two installs overlap**: a shared build service with one slot (`YarnLane`, root
  `build.gradle.kts`), used by every `KotlinNpmInstallTask` and `KotlinToolingSetupTask`, so Gradle holds the
  mutex and yarn's is never waited on inside a build. On the root project because both tasks live there; the
  root build script says why it carries this one rule. Rejected: a retry or `continue-on-error` (the item); npm
  instead of yarn (`kotlin.js.yarn=false`: other lock files, another installer for the bundle — a larger change
  for the same effect); a `--mutex file` (the plugin hard-codes the arguments). The image and e2e jobs do not
  change: they now get the lock file because the install runs, not because it happened to start first.
- **Not covered**: two separate builds on one machine still share yarn's port — two agents on the build box both
  installing wasm tooling for more than a minute could meet the same trap. CI runs one build per runner. Upstream,
  this is the Kotlin plugin's (two of its own yarn runs, and a mutex that cannot outlive a Node HTTP timeout); not
  reported upstream from here.
- **Verified.** The traced builds of this branch (four, interleaved with four of `main`): the two yarns never
  overlapped, each starting after the other's exit. CI on 8fd2298e (the fix, before the rebase onto `main`): the
  push run and five reruns of it (run 38320857740, attempts 1–6), every job green. At the observed rate six runs
  say little on their own; the control above is what shows the cause gone.
