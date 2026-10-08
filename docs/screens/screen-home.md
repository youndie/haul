---
id: screen-home
title: Home
type: client_screen
platform: [web]
status: draft
entry:
  web: "/"
parent_feature: feature-browse
calls_api:
  - endpoint-catalog
  - endpoint-recommendations
  - endpoint-membership
  - endpoint-saved
  - endpoint-cart
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/home
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Home)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Home_Loading
    Content: Home_Content
    Guest: Home_Guest
    PlusTrialDialog: Home_PlusTrialDialog
    Error: Home_Error
---

# Screen: Home

## 0a. Code anchors

| What | File (planned) |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/home/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/app/shell/` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/server/catalog/` |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |

## 0. Entry point and visibility

- **Entry point:** `/` in the browser.
- **Shown when:** always; guests included.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [ ] **Loading:** header, placeholder blocks for hero, categories and two product rows
- [ ] **Content:** Maya signed in: campaign «Autumn mega sale», «Tech week», «Free delivery» banners, 8 categories, 6 deals with the countdown, the Plus block **in its member form** («You saved $186 on delivery this year · renews Nov 2»), «Picked for you» 6 products, footer
- [ ] **Guest:** header «Sign in»; Plus block offers the trial; no «Picked for you»
- [ ] **PlusTrialDialog:** Sam signed in, dialog over Content: the benefits, «30 days free, then $4.99/month», Start trial / Not now
- [ ] **Error:** header, message that the page could not load, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 2129 / 2010; Content 3263 / 3875; Guest 2627 / 2721; PlusTrialDialog 3134 / 3850; Error 900 / 692.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-catalog | [endpoint-catalog](../api/endpoint-catalog.md) |
| endpoint-recommendations | [endpoint-recommendations](../api/endpoint-recommendations.md) |
| endpoint-membership | [endpoint-membership](../api/endpoint-membership.md) |
| endpoint-saved | [endpoint-saved](../api/endpoint-saved.md) |
| endpoint-cart | [endpoint-cart](../api/endpoint-cart.md) |

## 5. Navigation (summary)

- category tile → screen-catalog
- card → screen-product
- «+» on a card → adds the cheapest `Sku`
- heart → save
- «Try 30 days free» → PlusTrialDialog (guest → sign-in)
- search field → screen-search
