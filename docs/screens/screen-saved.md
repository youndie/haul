---
id: screen-saved
title: Saved
type: client_screen
platform: [web]
status: draft
entry:
  web: "/account/saved"
parent_feature: feature-account
calls_api:
  - endpoint-saved
  - endpoint-cart
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Saved)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Saved_Loading
    Content: Saved_Content
    PriceDrops: Saved_PriceDrops
    Empty: Saved_Empty
    Error: Saved_Error
---

# Screen: Saved

Not on the canvas; reuses `ProductCard` with a «Price dropped −$200» mark.

## 0a. Code anchors

| What | File (planned) |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/saved/` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/account/saved` in the browser.
- **Shown when:** signed in; a guest is sent to sign-in.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [ ] **Loading:** side menu, 12 card placeholders
- [ ] **Content:** «Saved 48», filter All / Price dropped 6, 12 cards, two with the price-drop mark
- [ ] **PriceDrops:** filter «Price dropped», the 6 cards
- [ ] **Empty:** «Nothing saved yet», how the heart works
- [ ] **Error:** header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1659 / 2422; Content 1890 / 2727; PriceDrops 1415 / 1685; Empty 948 / 1316; Error 799 / 641.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-saved | [endpoint-saved](../api/endpoint-saved.md) |
| endpoint-cart | [endpoint-cart](../api/endpoint-cart.md) |

## 5. Navigation (summary)

- see the parent feature
