---
id: B-43
title: "server + client: «Helpful» votes on reviews"
status: open
priority: P3
size: S
stage: stage-7-reviews
blocked_by: [B-22]
---

# B-43 — server + client: «Helpful» votes on reviews

B-22 serves reviews with their «Helpful (48)» button drawn and inert: the vote route the feature
document names (`PUT /api/v1/reviews/{id}/helpful`, refused on one's own review with `own_review`) was
outside its acceptance. Found by B-22.

Decided as product owner: one vote per customer per review, and a second press takes it back; a guest's
press is `navigate /sign-in`; the count on the button is the stored count.

- AC: a vote moves the review's count by one and a second press moves it back; voting on one's own review
  answers `409 own_review`; two votes by one customer never count twice (a route test and a unique index).
  The button carries the action and the client follows its `refresh`.
- Anchors (planned): `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/`.
