---
id: B-176
title: "feat: a second client — desktop — draws the same server trees"
status: done
priority: P3
size: L
stage: stage-15-showcase
---

# B-176 — feat: a second client — desktop — draws the same server trees

Server-driven UI's promise is one server and many clients, and haul shows one. `composeApp` already compiles for
desktop to draw the goldens, so a desktop app that browses the stand is the cheapest second client. The owner put it
at low priority (2026-11-01).

- A `jvm("desktop")` entry point that talks to the stand (sign-in through the system browser), packaged for the three
  desktop systems; what had to change on the server for a second client (nothing, ideally) recorded in the research.

- AC: the desktop app browses the stand, adds to the cart, and shows the same pages as the browser; the server
  changed only where the research says why.
- Anchors: `composeApp/build.gradle.kts`, `composeApp/src/desktopMain/`.

## Findings (2026-11-01)

- **Done.** `composeApp/src/desktopMain` holds the desktop app: `DesktopMain.kt` (`main`, the window, the Go menu,
  `--server=` / `HAUL_SERVER`, the stand by default), `DesktopStore.kt` (the same `App` over one Ktor CIO client,
  `storeClient`), `shell/BackStackHistory.kt`, `shell/DesktopLinks.kt` (system clipboard and browser),
  `feature/identity/DesktopIdentity.kt` (the guest in the user's preferences, one per server; no provider sign-in).
  `compose.desktop.application` packages it as `.dmg`, `.msi` and `.deb`; nothing is published.
- **The server changed in nothing** (research D2, «Decided in B-176»). The one thing a window lacks is an origin:
  the store's bare paths (`/api/v1/guests`, `/api/v1/sign-in`, the stand's `mergeUrl` `/api/v1/me/cart/merge`) go
  to the server through the client's `defaultRequest`. `DesktopStoreTest` fails without it (the guest is asked of
  `localhost`, and the home page is the error page).
- **Walked against the stand** (`/version` v0.7.2) through the desktop wiring, headless on the Linux build box: home
  in the store's faces and pictures, the cart, a card's «+» on the cart page that made a new guest's line and the
  header's count. tracy shows the walks' `POST /api/v1/guests` and `GET /ui/home`; the unpacked `.deb`'s app made a
  guest on its first start and none on its second (the preferences kept it).
- **Packaging**: `packageDeb` on the build box: `haul_1.0.0_amd64.deb`, 66 MB, 152 MB installed; its runtime is
  jlink's, the modules `suggestRuntimeModules` names plus Compose's (`java.instrument`, `java.management`,
  `java.prefs`, `jdk.unsupported`). Started from the unpacked package under WSLg: it ran, fetched the home page and
  its pictures from the stand (tracy), and logged no error but skiko's fallback from OpenGL to software. `.dmg` and
  `.msi` are configured, not built: jpackage builds only its host's.
- **Not done**: the OS's reduce-motion setting (the JVM has no API for it; motion is on), the mouse's back and
  forward buttons, a desktop icon of the store's own (jpackage's default).
- **Demo shopper** (B-162): the desktop's `Identity` offers it as the browser's does, with no provider in the way,
  when the server has it on; on the stand it is off. Its five-minute token cannot be renewed on the desktop — renewal
  is the provider flow's — so it would last five minutes.

## Question (2026-11-01)

Sign-in through the system browser needs the stand's provider, not the client. kotlin-multiplatform-oidc 0.18.4
publishes a JVM variant of `oidc-appsupport` (the system browser and a listener on a loopback port), and shildik
accepts a public client's `http://127.0.0.1/<path>` on any port from `0cbc2c7` (2026-10-07), but:

1. the stand runs shildik **0.4.1** (the chart's `shildik.version`), released 2026-09-17, before that commit — a
   shildik release carrying it, and the chart moved to it;
2. the chart registers `haul-web` with **one** return address, `https://<hostname>/signed-in.html`, and its
   bootstrap re-creates the client whenever the set differs (`charts/haul/files/shildik-bootstrap.py`) — it would
   register `http://127.0.0.1/signed-in` too (or a second public client, `haul-desktop`, for the desktop alone).

The owner decides:

- **(a)** release shildik with `0cbc2c7` and register the loopback address; then a follow-up item wires
  `oidc-appsupport`'s JVM flow into `DesktopIdentity.kt` (sign-in, renewal, sign-out) and walks it against the stand.
- **(b)** leave the desktop a guest's: browse and the cart, as now, and close the item.


## Decided (2026-11-01)

The owner chose (a): shildik released with `0cbc2c7` (0.5.0), sign-in wired in B-178. This item is done as a
guest's desktop client.
