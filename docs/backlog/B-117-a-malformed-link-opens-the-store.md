---
id: B-117
title: "fix: a page address with broken percent-encoding opens the store"
status: done
priority: P2
size: S
stage: stage-12-qa-run
---

# B-117 — fix: a page address with broken percent-encoding opens the store

Found by the catalogue's first run, TC-ERR-04 step 4 ([run](../qa/runs/2026-10-11-v0.4.0.md)). Opening
`/c/electronics?brand=%E0%A4%A`, `/search?q=%zz`, `/c/electronics?%zz` or `/p/p-sony-wh-1000xm6?sku=%zz` on the
stand answers the page request itself with `400 application/json`, and the browser shows
`{"code":"validation_failed","message":"Malformed request"}`: Ktor fails decoding the query, and
`ErrorAnswers.kt` answers any `BadRequest` with the API's error body. The store's own «This link is out of
date» never appears — every other refusal on `/ui/…` names a field, so on the stand that page is unreachable.

- **A browser's request for a page always gets the page.** The document an address opens is the static frame,
  whatever its query; the tree request that follows is where a malformed query is refused, and the client
  turns that refusal into «This link is out of date». Rejected: decoding leniently on the server — it would
  hide a broken link behind a guessed one.
- Not covered: the API's own answer to a malformed query, which stays `400 validation_failed`.

- AC: each of the four addresses above opens the store with «This link is out of date», «Open the page» and
  «Go to the home page»; `GET /api/…?%zz` still answers `400` JSON. A server test holds both.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/ErrorAnswers.kt`.

## Findings (2026-10-12)

- **Where the `400` came from.** Not the static route: Ktor 3.6 reads the call's parameters whole, the query's
  with them, as it starts any routed call (`RoutingCall`'s constructor), so a query that does not decode is a
  `BadRequestException` before any route or route-scoped plugin runs, and `ErrorAnswers.kt` answers it. The page
  is now answered ahead of routing by an application plugin, `PageAtAnyQuery` (`WebBundle.kt`), installed after
  `OneCategoryAddress` so another form of a category's address still goes to its one address first: a `GET` of a
  `StorefrontPage` address whose query does not decode gets `index.html`, `no-cache`. It is sent as it is, not
  precompressed — the static route's choice of encoding is Ktor-internal, and a mangled link is not worth a copy
  of it for a page of a few kilobytes.
- **The tree request is never sent from the browser.** Ktor's client decodes a URL's query as it builds the
  request (`URLBuilder.build`) and throws `URLDecodeException`, so the shell caught it as a missing network:
  «We couldn't reach Haul» with a Retry that fails the same way. The shell now takes it as the refusal the server
  gives such a query, `400 validation_failed` naming nothing (`Transport.kt`), which is «This link is out of date».
  And `/search?q=%zz` threw from the composition drawing the search's placeholders (`Address.query` decoded the
  whole query): a query that does not decode is now no query, and spoils no other parameter.
- **«Open the page» and «Go to the home page».** Read as B-94 decided and TC-ERR-04 says: the page offers «Open
  the page» (its path) when the link has a query, «Go to the home page» when it has none. All four addresses have
  a query, so each offers «Open the page», which opens the page at its path.
- The server test sends the four addresses over a socket to CIO, the stand's engine: Ktor's test client cannot
  send them either. Unchanged and out of scope: any other routed path with such a query — an unknown one, a file —
  answers the same `400` rather than its `404` or the file.

