---
id: B-94
title: "client + server: an error page says what happened and offers what helps"
status: done
priority: P1
size: S
stage: stage-11-qa
---

# B-94 — client + server: an error page says what happened and offers what helps

Found in the QA pass of 2026-10-10 (v0.3.0), code audit. Three answers are drawn as «try again» with a Retry that can never succeed:
- `/checkout` with nothing selected (back after placing an order, a reload, an empty merged cart) gets `409 cart_empty`
  → «Checkout didn't load · Your cart is unchanged. Try again» (`server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt:188`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt:724-726`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt:757-766`). It should take the shopper to the cart.
- a one-character search gets `400 query_too_short` → «Search didn't respond … Try again in a moment»
  (`server/src/main/kotlin/io/github/youndie/haul/feature/search/domain/Search.kt:36`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt:158-166`).
- any `400` from an old or edited link (bad `page`, `sort`, `from`, `shown`, a `sku` no longer on the product — a
  shared link included) → «Something went wrong on our side» (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt:736-739`).
The Account and Saved error pages always say «on our side», even when the network was unreachable (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt:955, 1018`).

- AC: checkout with an empty cart lands on the cart; a too-short query explains itself in the search; a bad
  parameter falls back to the page without it (or says the link is stale); every error page tells «unreachable»
  from «our side»; tests per case.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt`.

## Done (2026-10-11)

- **Decision: the client reads what the server already answers; no server change.** `409 cart_empty`,
  `400 query_too_short` and `400 validation_failed` with its `field` (the query parameter `CatalogError.Invalid`
  names) say what happened; Retry sends the same request and gets the same answer. `ScreenFailed.Refused` now
  carries the `field` (`shell/Transport.kt`), and `Address.recovery` (`shell/Recovery.kt`) decides:
  - an empty checkout gives its history entry to the cart (`Navigator.redirect`), so back does not return to it;
  - a search too short is `SearchTooShort` (`shell/Shell.kt`): «Keep *typing*», «Search needs at least two
    characters…», the query in the header's field, no Retry;
  - a `400` naming a query parameter the address has loads the page without it — every value of it, the rest of
    the query as written; one parameter per refusal, so `?page=x&from=3` drops `page`, then `from`; no message,
    the shopper lands on the page the link meant;
  - a `400` naming none the address has (Ktor's malformed-request answer has no field) is `StaleLink`: «This link
    is *out of date*», with «Open the page» (its path) or, at a bare path, «Go to the home page».
  - Over a page kept on the same screen (B-62) — a one-character search typed over results — the notice says
    «Search needs at least two characters.» (or that the link is out of date) and has no Retry.
- **Unreachable versus our side** on every error page: `ShellFailure.of(cause)` picks the failure; Account and
  Saved say `ShellFailure.Unreachable.message` when there was no answer; Cart, Checkout and Search keep their
  reassurance and say «We couldn’t reach Haul. … Check your connection and try again.» for no answer, and their
  artboards' «… Try again in a moment.» for an error of the server's (copy unchanged, goldens unchanged).
- **Rejected**: answering a short query or an empty checkout with a server tree (a `200` for a request the
  endpoint refuses, and the contract's code unread); a message over the page saying a parameter was dropped
  (the page the link meant is what the shopper wanted, and B-92 is reworking the message host).
- **Tests**: `RecoveryTest` (7, pure: each code, the field dropped with the query kept as written, repeated
  parameters, stale link, Retry's), `RefusedPagesTest` (12, the shell against a fake transport: checkout → cart
  with the entry replaced, a too-short search landed on and typed over kept results, a bad `sort` and a gone
  `sku` dropped, a stale link opening its path, unreachable Account/Saved/Cart/Checkout/Search and a server
  error on Saved).
- **Mutations**, each restored, `git status` clean: the cart_empty branch → 2 red; `ShellFailure.of` always
  `Server` → the 5 unreachable tests; `retryHelps` always true → the kept-search test and its unit test; the
  query_too_short branch → 2 red; the shell's redirect branch disabled → the 3 redirect tests; the `field` not
  read off the body → the two drop tests; `without` keeping nothing → 2 unit tests and the drop test; the
  stale-link page and Saved's failure → their tests.
- **Goldens**: two new, `Search_TooShort` and `Search_TooShort_Phone` (no artboard; the not-found layout under the
  shell's header with the query in the field), recorded on the Linux build machine; no existing golden moved.
