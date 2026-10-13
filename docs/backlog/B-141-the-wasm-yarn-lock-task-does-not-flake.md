---
id: B-141
title: "ci: kotlinWasmStoreYarnLock does not fail on a missing build/wasm/yarn.lock"
status: wip
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
