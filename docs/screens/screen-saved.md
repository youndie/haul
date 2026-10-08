---
id: screen-saved
title: Saved
type: client_screen
platform: [web]
status: active
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

Drawn inside the account's frame (`AccountBody.saved`, the title's count pill beside «Saved»),
`PageKind.Saved` in the shell; reuses `ProductCard` with a filled heart and a «Price dropped −$200» mark.
The address is `/account/saved` (research D6, «Decided in B-20»), its tree `GET /ui/account/saved`.

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/saved/SavedViews.kt`, inside `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/AccountViews.kt` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt` (`SavedLoading`, `SavedError`) |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/saved/screen/SavedScreen.kt`, built through `server/src/main/kotlin/io/github/youndie/haul/feature/account/screen/AccountScreen.kt` |
| The contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/SavedComponents.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/account/saved` (`?filter=price-dropped`, `?page=2`) in the browser; the header's
  «Saved» and the account menu's «Saved».
- **Shown when:** signed in. A guest's `401` is drawn as the sign-in prompt, which returns here once
  signed in (B-44).

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [x] **Loading:** side menu, 12 card placeholders (`SavedLoading`)
- [x] **Content:** «Saved 48», filter All / Price dropped 6, 12 cards, two with the price-drop mark, the page numbers
- [x] **PriceDrops:** filter «Price dropped», the 6 cards
- [x] **Empty:** «Nothing saved yet», the three steps of how the heart works, «Browse deals»
- [x] **Error:** header, «Saved didn’t load», Retry (`SavedError`)

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1659 / 2422; Content 1890 / 2727; PriceDrops 1415 / 1685; Empty 948 / 1316; Error 799 / 641.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-saved | [endpoint-saved](../api/endpoint-saved.md) |
| endpoint-cart | [endpoint-cart](../api/endpoint-cart.md) |

## 5. Navigation (summary)

- a card's heart → `DELETE /api/v1/me/saved/{productId}` (a saved one) or `PUT` (any other), then the page drawn again; a guest's heart → `/sign-in`
- a filter chip, a page number → `/account/saved?filter=…&page=…`
- a card → the product (`/p/{productId}`, screen-product); a card's «+» → the cart's line command
- «Browse deals» on the empty list → `/deals`
- the menu → the account's other pages (screen-account)

## 6. Quirks

- The canvas's pager on `Saved_Content` reads «1 2 2» (a typo); 48 saved are two pages, drawn «1 2».
- `Saved_Empty` keeps «Saved 48» in the menu over an empty list; the server draws an empty list
  without a count, so the menu and the title agree with the list.
- The canvas's cards (robot vacuum, keyboard, coffee set, …) are not products the seed sells: the
  fixture bodies keep the canvas's cards, and the stand shows Maya her seeded list.
