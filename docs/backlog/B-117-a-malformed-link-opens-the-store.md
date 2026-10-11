---
id: B-117
title: "fix: a page address with broken percent-encoding opens the store"
status: wip
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
