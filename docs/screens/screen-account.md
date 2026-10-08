---
id: screen-account
title: Account
type: client_screen
platform: [web]
status: active
entry:
  web: "/account"
parent_feature: feature-account
calls_api:
  - endpoint-account
  - endpoint-orders
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Account)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Account_Loading
    Content: Account_Content
    NotMember: Account_NotMember
    Orders: Account_Orders
    NoOrders: Account_NoOrders
    Error: Account_Error
---

# Screen: Account

Two addresses draw this screen: the overview at `/account` (`GET /ui/account`) and the history at
`/account/orders` (`GET /ui/account/orders`, the Orders state), both `PageKind.Account` in the shell.
Menu items other than Overview, Orders and Saved are not shown in v1 (research D6). The Saved list,
drawn inside the same frame, is [screen-saved](screen-saved.md).

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/account/AccountViews.kt` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt` (`AccountLoading`, `AccountError`) |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/account/screen/AccountScreen.kt` |
| The contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/AccountComponents.kt` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/account` and `/account/orders` (`?status=active|delivered|returned|cancelled`) in
  the browser; the header's name and «Orders».
- **Shown when:** signed in. A guest's `401` is drawn as the sign-in prompt («This page needs a
  sign-in», «Sign in to continue»), which returns here once signed in (B-44).

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [x] **Loading:** side menu, placeholder tiles and order cards (`AccountLoading`)
- [x] **Content:** «Hi, Maya», Points 2,480 / $24.80, Haul Plus $186 / renews Nov 2, Price drops 6, two active orders, 4 history rows
- [x] **NotMember:** Sam: the Plus tile offers the trial («Try 30 days free» presents the trial dialog), points 0, no active orders, history with one order
- [x] **Orders:** «Orders» selected: the full history with a status filter (All / Active / Delivered / Returned / Cancelled), each chip its own address
- [x] **NoOrders:** a customer with no orders: «No orders yet», «See today's deals», «Joined <month>»
- [x] **Error:** header, «Your account didn’t load», Retry (`AccountError`)

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1014 / 1403; Content 1629 / 2255; NotMember 1112 / 1378; Orders 1067 / 1332; NoOrders 708 / 693; Error 900 / 641.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-account | [endpoint-account](../api/endpoint-account.md) |
| endpoint-orders | [endpoint-orders](../api/endpoint-orders.md) |

## 5. Navigation (summary)

- the menu's Overview, Orders, Saved → `/account`, `/account/orders`, `/account/saved`
- an active order's card, «Track», «Details» → the order (`/account/orders/{id}`, screen-order)
- «Reorder» on a delivered row → `POST /api/v1/me/orders/{id}/reorder` (`CartCommand.Reorder`), then `/cart`; a refusal draws the page again
- «All orders» → `/account/orders`; a filter chip → `/account/orders?status=…`
- «See today's deals» → `/deals`
- «Try 30 days free» → the trial dialog (kompot `present`, [feature-membership](../features/feature-membership.md))

## 6. Quirks

- The header's Orders, Saved and name labels sit 7 px below the canvas on every screen with the full
  header (the shared `HaulHeaderView`).
- A non-member's avatar tone, and an overview without past orders, a filter matching nothing or an
  active order going to a pickup point, are drawn without artboards
  ([feature-account](../features/feature-account.md), quirks).
