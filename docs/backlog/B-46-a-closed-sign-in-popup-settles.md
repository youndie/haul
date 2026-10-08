---
id: B-46
title: "client: a closed sign-in popup settles the sign-in"
status: wip
priority: P2
size: S
stage: stage-4-cart
blocked_by: [B-44]
---

# B-46 — client: a closed sign-in popup settles the sign-in

B-41 redraws when the popup is closed and B-44 lands on `/` when a sign-in does not go through — both
assume the sign-in ends when the shopper closes the popup. From the OIDC library's symbols, its web popup
flow resumes only on the redirect page's message and does not watch `popup.closed`, and a popup the
browser blocks returns `null`: either way the sign-in would stay pending, a second press would open a
second popup, and neither «cancelled» path would ever run in a browser. Found by B-44, from the library's
code, not walked.

Decided as product owner: a closed or blocked popup ends the sign-in as «did not go through» within a
second, and a press while a sign-in is pending focuses the open popup rather than opening another.

- AC: walked in a browser against a shildik realm first — closing the popup, and a blocked popup —
  recording what the library does. If it hangs: the client watches the popup and settles the sign-in
  itself (a client test with a fake popup), and the finding is reported to the library with the user's
  consent. If it does not hang: B-41's and B-44's paths are confirmed and the item closes with that record.
- Anchors (planned): `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/feature/identity/`.
