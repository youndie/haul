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

> Drafted before the module exists: every path below is planned.

## 1. Responsibility

Renders every screen the server describes, at 1440 and 390 wide, through a registry of one renderer
per Haul component; owns navigation, the `Loading` and `Error` states of every screen, the browser
sign-in flow and the guest id kept in browser storage.

Deliberately does **not**: decide what a screen holds (the server's tree does), compute a total, a
fee or a delivery date, or keep any state the server owns.

## 2. API contracts

* **Contracts:** [haul-shared](haul-shared.md); every call is listed in the screen documents.

## 2a. Code anchors

| File (planned) | What is there |
|---|---|
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/` | colour roles and the three bundled fonts |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/` | the renderer registry |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/` | navigation, Loading, Error |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/` | sign-in and the guest id |
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

* Served as static files by [haul-server](haul-server.md).

## 6. Local setup

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

The server must be running.
