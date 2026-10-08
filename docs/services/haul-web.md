---
id: haul-web
title: Haul web storefront
type: service
module: composeApp
tech_stack: [Kotlin, Compose Multiplatform, wasmJs, kompot-client, viddik]
owner: unassigned
depends_on:
  - haul-shared
  - haul-server
publishes:
  - the web bundle, served by haul-server
---

# Haul web storefront

> Describes the module as it stands after B-10 and B-27: the renderers of the browse, product and
> search screens exist and are checked against the canvas, and the server serves the bundle. The
> root does not draw screens yet — `App.kt` lays out the theme around an empty body and calls no
> server — so navigation, sign-in and the guest id are *target*.

## 1. Responsibility

Renders every screen the server describes, at 1440 and 390 wide, through a registry of one renderer
per Haul component; owns navigation, the `Loading` and `Error` states of every screen, the browser
sign-in flow and the guest id kept in browser storage.

Deliberately does **not**: decide what a screen holds (the server's tree does), compute a total, a
fee or a delivery date, or keep any state the server owns.

## 2. API contracts

* **Contracts:** [haul-shared](haul-shared.md); every call is listed in the screen documents.

## 2a. Code anchors

| File | What is there |
|---|---|
| `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/App.kt` | the bundle's entry point and the root (the theme at the page's width; no screen yet) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/` | colour roles and the four bundled fonts (`composeApp/src/commonMain/composeResources/font/`) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/` | the renderer registry (`haulRegistry()`) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/` | the renderers of `catalog/`, `home/`, `product/`, `search/`; the cart's are B-13 |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/` | the Loading, Error and NotFound pages a screen shows before or instead of its tree; navigation is *planned* |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/` | sign-in and the guest id (*planned*, B-12) |
| `composeApp/src/desktopTest/snapshots/design/` | one reference PNG per artboard, exported from the canvas |

## 3. How it is built

* **wasmJs ships, `jvm("desktop")` tests.** The same renderers run on both; the desktop target
  draws the screenshots that `viddikDesignParity` compares with the references.
* **A fixture is a recorded server body** for the sample data (*hypothesis*, research risk 3), so a
  screenshot tests the tree and the renderer together.
* **The theme is written from the canvas's roles**, not from the pictures (research §1.6).

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Module | [haul-shared](haul-shared.md) | the components on the wire |
| Service | [haul-server](haul-server.md) | every tree and every command |
| External | shildik | the browser's sign-in |

## 5. Infrastructure and deploy

* Served as static files by [haul-server](haul-server.md): `:composeApp:wasmJsBrowserDistribution`
  is copied into the server's distribution, in its `web` directory (`server/build.gradle.kts`), and
  the server serves `HAUL_WEB_DIR` at `/` with no fallback to `index.html`. One image and one
  origin, so the client needs no base URL. Today the files go out uncompressed (B-34).
* **First load**, measured in B-28: the bytes and the time to the first frame, per file and per
  network profile, are in [research-architecture](../research/research-architecture.md) D9,
  «Measured in B-28». Skiko's wasm dominates them; what Haul's own screens add is about a tenth.

## 6. Local setup

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Until the root draws screens (see the note at the top) the page is the theme's background and calls
no server. The screens are seen in the screenshot tests instead: `viddikVerify` and
`viddikDesignParity` on the desktop target.
