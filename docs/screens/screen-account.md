---
id: screen-account
title: Account
type: client_screen
platform: [web]
status: draft
entry:
  web: "/account"
parent_feature: feature-account
calls_api:
  - endpoint-account
  - endpoint-orders
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/account
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

Menu items other than Overview, Orders and Saved are not shown in v1 (§2).

## 0a. Code anchors

| What | File (planned) |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/account/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/shell/` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/server/account/` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/account` in the browser.
- **Shown when:** signed in; a guest is sent to sign-in.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [ ] **Loading:** side menu, placeholder tiles and order cards
- [ ] **Content:** «Hi, Maya», Points 2,480 / $24.80, Haul Plus $186 / renews Nov 2, Price drops 6, two active orders, 4 history rows
- [ ] **NotMember:** Sam: the Plus tile offers the trial, points 0, no active orders, history with one order
- [ ] **Orders:** «Orders» selected: the full history with a status filter (All / Active / Delivered / Returned / Cancelled)
- [ ] **NoOrders:** a customer with no orders: «No orders yet», link to deals
- [ ] **Error:** header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 1014 / 1403; Content 1629 / 2255; NotMember 1112 / 1378; Orders 1067 / 1332; NoOrders 708 / 693; Error 900 / 641.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-account | [endpoint-account](../api/endpoint-account.md) |
| endpoint-orders | [endpoint-orders](../api/endpoint-orders.md) |

## 5. Navigation (summary)

- see the parent feature
