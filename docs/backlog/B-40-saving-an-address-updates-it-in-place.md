---
id: B-40
title: "server: saving the address being delivered to updates it in place"
status: open
priority: P3
size: S
stage: stage-5-order
blocked_by: [B-15]
---

# B-40 — server: saving the address being delivered to updates it in place

The checkout's address is an inline form holding the address being delivered to (B-15), but every save
adds a row (`addAddress`, B-14): changing «4F» to «5B» leaves the old address stored and unlisted.
Decided as product owner: a save edits the chosen address in place; an identical address is not
stored twice.

- AC: saving the form twice with a changed field leaves one stored address with the new value; a route
  test reads the customer's addresses after two saves.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/`.
