---
id: B-67
title: "client: the header on loading, error and not-found pages leads somewhere"
status: done
priority: P2
size: S
stage: stage-10-review
---

# B-67 — client: the header on loading, error and not-found pages leads somewhere

Loading placeholders and error pages (`ErrorShell`, `SearchError`, `CartError`, `AccountError`, `SavedError`) draw
`SHELL_HEADER`, whose actions are all empty (`shell/Shell.kt:55-75`): on a 5xx or unreachable page only the logo and
the search work. Catalog, Deals, the category words, Orders, Saved, Sign in, Cart and HAUL PLUS do nothing. Landing
straight on a 404 or the sign-in prompt gives the same dead header (`Storefront.kt:286, :291`).
`NotFoundShell` and `SignInPrompt` draw the header without `onAccount` (`Shell.kt:405`, `SignInPrompt.kt:57`,
`ProductNotFound.kt:16`). «Go to your orders» on a missing order is a no-op with that header
(`feature/order/OrderNotFound.kt:23`).

- AC: on each of those pages every header control the shopper sees works, or is not drawn; a test per page kind.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/order/OrderNotFound.kt`.

## Findings (2026-10-10)

- **Decided: the shell draws the last tree's header, and before any tree a guest's header with the addresses
  the server fixes** (research §2, «Decided in B-67», says why and what was rejected). `LocalShellHeader`
  (`shell/Shell.kt`) is the header of the last tree drawn, or `SHELL_HEADER` before any; every placeholder and
  error page draws it as a placeholder (`PendingHeader`), and a page that is not there, the prompt and the
  sign-in page draw it as they did. `SHELL_HEADER` now carries «Deals» `/deals`, the cart `/cart`, «Orders» and
  «Saved» as a guest's sign-in returning to them, the account and «HAUL PLUS» as the sign-in. A category's
  address is the server's, so before a tree each category word and each «Catalog» entry opens `/c`.
- **A non-member's «HAUL PLUS» on a page the shell draws** (left out of B-66): the shell's handler now runs
  kompot's `present`, `close`, `sequence` and `refresh` as a screen's does. The trial's dialog is drawn over the
  page, its answer's `refresh` loads the page again, and a visit takes the dialog away.
- **Found on the way, fixed because it is the same header**: a guest's «Saved» over the Saved list's error page
  signed in and then did nothing. Its `next` was the page already shown, which `Navigator.open` ignores. The
  shell's handler loads the page again in that case, as a tree's handler already did.
- **The phone menu opens over a placeholder** (B-73 kept it shut there: a button drawn and dead). Its account
  row is the sign-in or the account, while the slot itself stays the placeholder the artboards draw.
- **«Go to your orders» on a missing order** follows the header's «Orders». On arrival that is the shell's: a
  guest signs in and lands on the orders, and a customer arriving at `/sign-in` is sent straight on (B-66).
- **Not changed**: the cart button on the cart's own error page opens nothing, because `Navigator.open` does
  not open the page already shown and Retry is beside it. The checkout's header (logo only) works already. The
  search field and the logo already worked. After a trial started from a page that is not there, that page is
  loaded again but still has no tree, so the header stays the last tree's (a non-member's) until the next tree
  arrives. Pressing «HAUL PLUS» then asks for the trial again, and the server answers that refusal by closing
  the dialog and drawing the page again.
- **Tests**: `ShellHeaderTest` (fourteen tests). One per page kind at 1440: loading, the error page, the search
  error, the cart's, the account's and Saved's errors, a product and a category that are not there, the
  sign-in prompt and the sign-in page. Each presses every control the header draws: «Deals», the cart, «Orders»,
  «Saved», the category row, «Catalog» and an entry, «HAUL PLUS», the account where it is drawn, and the logo,
  then checks where each press went. The remaining tests: «Go to your orders» on a missing order, the last
  tree's links kept on a failed page, a non-member's «HAUL PLUS» presenting the trial with its `refresh`
  loading the page again, and the phone menu over an error page. `SignInEverywhereTest`'s reload test presses
  the sign-in page's own button, because the header's account slot now reads «Sign in» and works too.
- **Mutations**, each red then restored with `git status` clean:
  - the shell's header without its actions (on arrival, and over placeholders);
  - the last tree's header not kept;
  - the shell's `present` dropped;
  - its `refresh` dropped;
  - the phone menu shut on a placeholder;
  - the menu's account row dead on a placeholder;
  - a sign-in to the page already shown opening instead of loading;
  - the category words without links.
- **Goldens**: none changed. `viddikVerify` passes in `check` on Linux: the actions add no pixels, and a
  screenshot has no `LocalShellHeader` and draws `SHELL_HEADER` as before.
