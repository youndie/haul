---
id: B-130
title: "fix: a lapsed sign-in draws the guest's header, not the customer's"
status: done
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-identity
---

# B-130 — fix: a lapsed sign-in draws the guest's header, not the customer's

Found by the catalogue's run on v0.5.0, TC-SIGN-08 step 2 ([run](../qa/runs/2026-10-12-v0.5.0.md)), on a local stack at 1440. Maya on
`/account/orders`; the next parts request answered 401 and the realm's token endpoint `400 invalid_grant`; she
pressed «Delivered». The body was taken down to «This page needs a sign‑in · Sign in to continue», the tokens left
`sessionStorage`, `haul.guest` stayed the one before and no `POST /api/v1/guests` went out — B-115's half holds.
But no `GET /ui/header` followed: the header still read «Maya» with her menu («Account», «Sign out») and «Cart 3»,
while the tab was a guest — its cart opened «Your cart is empty». Requests seen: `GET /ui/parts/account/orders?
status=delivered 401` → `GET /api/v1/sign-in` → token `400` → the parts again `401` → `GET /ui/account/orders
401`, nothing for the header. Whether v0.4.0 drew the guest's header here was not recorded.

- **A session that ends asks the header anew, however it ended.** Sign-out already does (TC-ERR-07 step 4); a
  renewal the provider refused is the same change of who is looking. Rejected: blanking the header — the guest has a
  header of their own (cart, sign-in).

- AC: the walk above ends with the guest's header (no name, «Sign in», the guest's cart count) asked once, the
  sign-in prompt in the body; signing in from it brings Maya's header back. A client test holds it and fails on
  today's code.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/Identity.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.

## Findings

- **Why the header stayed hers.** `Storefront` read who is looking (`SessionControls.signedIn`) only when its own
  body was composed again, and only a visit — or a header drawn — does that. Sign-out is followed by a visit
  (`Signing.actions`: home from a customer's page, or a reload), so the body re-read the session, `ShellHeaders`
  no longer knew the shopper and `AskForHeader` asked (TC-ERR-07 step 4, `KnownHeaderTest`). A renewal the
  provider refused drops the tokens inside `Identity.send`, under a press, with no visit: the parts' `401` takes
  the page down (`signedOut`, B-62), which recomposes only the screen, so `AskForHeader` was handed the stale
  «signed in», found Maya's header known for her and asked nothing; `LocalShellHeader` stayed hers.
- **The fix follows the session instead of reading it.** `SessionControls.signedInChanges` (a `Flow<Boolean>`;
  `Identity`'s is its state mapped to `signedIn`) is collected in `Storefront`, so a session that ends however
  it ends draws the client's guest header at once and asks `/ui/header` once over the prompt; the sign-in from
  the prompt flips it back and the reloaded tree draws Maya's. The three test fakes of `SessionControls` follow
  their `signedIn` the same way.
- **Not B-115's doing.** At v0.4.0 (`git show v0.4.0:…/Identity.kt`) `send` already renewed once and, refused,
  dropped the tokens and sent once more as the guest (`recover()`), with nothing telling the shell; and
  `Storefront` read `session?.signedIn` into `AskForHeader` exactly as now (B-105 is in v0.4.0). So, by the
  code, v0.4.0 kept Maya's header here too — its run did not look at the header (it records the prompt and
  the landing back only). B-115 changed what happens to the guest (kept, not replaced on the next `401`), not
  who the shell thinks is looking. Not re-run against v0.4.0.
- **Verified** by `LapsedHeaderTest` over the real `Identity`, `ktorTransport` and a mock server whose provider
  refuses every refresh: Maya's orders, «Delivered» (a `load` of `/ui/parts/account/orders?status=delivered`),
  then the prompt under the guest's header (count 1, «Sign in», no «Maya»), `GET /ui/header` once, the refresh
  tried once, the same guest id and no `POST /api/v1/guests`; signing in brings «Maya» and «3» back. It fails on
  the code before the fix, waiting for the guest's count (mutation: the `collectAsState` reverted to the plain
  read). Not walked in a browser against a local stack: `App` hands `Storefront` the same `Identity` the test
  does.
