---
id: B-178
title: "feat: the desktop client signs in through the system browser"
status: done
priority: P3
size: M
stage: stage-15-showcase
---

# B-178 — feat: the desktop client signs in through the system browser

B-176 left the desktop app a guest's: sign-in needs the stand's provider to accept an app's loopback return address,
which shildik does from `0cbc2c7`, released as 0.5.0. The owner chose sign-in (2026-11-01, B-176's question, (a)).

- The chart moves to shildik 0.5.0 and registers the loopback address (`http://127.0.0.1/<path>`, any port) — on
  `haul-web` or as a second public client for the desktop alone, whichever the research says why;
  `kotlin-multiplatform-oidc`'s JVM `oidc-appsupport` flow (system browser, a listener on a loopback port) is wired into
  `DesktopIdentity.kt`: sign-in, renewal, sign-out, and the demo shopper's token renewed like a signed-in person's.

- AC: the desktop app signs Maya in through the system browser against a composed stack (and, after the deploy, the
  stand), shows her account and cart, renews the token past its lifetime and signs out; the browser's sign-in is
  unchanged (e2e green).
- Anchors: `composeApp/src/desktopMain/kotlin/io/github/youndie/haul/feature/identity/DesktopIdentity.kt`,
  `charts/haul/files/shildik-bootstrap.py`, `charts/haul/values.yaml`.

## Findings (2026-11-01)

- **Done.** The chart moved to shildik **0.5.0** (`shildik.version`; the server's suite, the e2e and
  `scripts/measure-stack.py` pin the same image; the catalog's `shildik` libraries are `0.5.0.24`, which resolve from
  the reposilite snapshots repo) and registers the desktop's loopback address on **`haul-web`** itself:
  `shildik.desktopRedirectUri: http://127.0.0.1/signed-in`, refused at render with a port or on a name (`localhost`),
  empty for the browser's page alone. Why not a second client is in research D5, «Decided in B-178»: the server
  accepts one client's tokens (`azp`), and a demo shopper's refresh token, issued to `haul-web`, renews only there.
  Chart 0.4.0 → 0.5.0 (a refusal added).
- **The stand's upgrade re-creates the client once.** The realm hook now takes the set
  (`SHILDIK_REDIRECT_URIS`, JSON) and re-creates the client when its set differs; `scripts/bootstrap-check.py`, run by
  `scripts/chart-check.sh`, runs the hook against a fake management API: the stand's client gains the address with one
  DELETE and one POST, the next upgrade asks for neither, a new release creates it with both (mutated — compare
  against the first address alone — it fails). Against a real shildik 0.5.0 on the build box, by hand: the loopback
  address refused before (`400`), accepted after on two ports; a sign-in made before the client's re-creation renewed
  after it (`200`) — so the shoppers signed in on the stand at the upgrade, and the synthetic shoppers, are not signed
  out by it. `scripts/stand-kind.sh` passes the same set.
- **Desktop** (`composeApp/src/desktopMain/kotlin/io/github/youndie/haul/feature/identity/DesktopIdentity.kt`):
  `LoopbackSignInFlow` is kotlin-multiplatform-oidc 0.18.4's `JvmCodeAuthFlowFactory` with a listener of Haul's own
  (`LoopbackListener`, the JDK's HTTP server, so `jdk.httpserver` joins the packaged runtime —
  `suggestRuntimeModules` names exactly the five modules now listed): the library's binds port 8080 — the local
  stack's — on every interface. Haul's binds 127.0.0.1 on a free port before the browser opens. A second press reopens
  the pending sign-in's page; ten minutes without a return ends it. Renewal and sign-out go through `Identity` as in
  the browser, so the demo shopper's token is renewed too (B-176's gap). `PreferencesSessionStore` keeps the tokens
  per server beside the guest, so a sign-in outlives the window. The browser's sign-in code is untouched.
- **Tests**: `DesktopSignInTest` (shildik's image at the chart's version, the realm registered with the chart's own
  addresses read off `values.yaml`; the test plays the browser — fetches the page the app opened, posts the password
  form, follows the redirect to the listener): sign-in with the merge, renewal past the token's `exp`, sign-out, a
  press during a sign-in, an abandoned one, a demo-shaped token renewed. Mutated (the listener drops the query; the
  refresh keeps the old token): five of six fail. `DesktopPartsTest` updated for kept tokens and a server with sign-in
  off. shildik takes a spent refresh token for a few seconds and revokes the chain after (its protocol-oidc-browser
  §4a), so no test asks it twice.
- **Docs**: research D5 «Decided in B-178», D2's B-176 notes, `docs/qa/14-desktop.md` (TC-DESK-02 rewritten for
  sign-in, TC-DESK-03 renewal, TC-DESK-04 a pending or abandoned sign-in), README's desktop line.
- **Not done here**: the stand walk (TC-DESK-02…04 against haul.kotlin.website) follows the v0.8.1 deploy that
  carries this chart; no walk by hand against a local stack was made (the composed stack was the test's). The code
  gate and the e2e on the head are the pull request's CI. The library keeps the request's state and PKCE verifier in a file of its own in the user's home while a
  sign-in is under way (its JVM `PreferencesFactory`), emptied when the code returns; not configurable without
  replacing its flow. Tokens sit in the user's preferences, not the system's keychain (no JVM API).
