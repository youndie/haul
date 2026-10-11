---
id: B-110
title: "server: a client leaving the order page is not an error"
status: done
priority: P3
size: XS
stage: stage-11-qa
---

# B-110 — server: a client leaving the order page is not an error

Found in the QA pass of 2026-10-10 (v0.3.0). Whenever a client leaves the order page the server logs `ERROR … unhandled: GET /ui/updates` (Broken pipe) —
an expected disconnect reported as an unhandled error; on the stand the synthetic shoppers make one per walk.

- AC: a closed update stream ends quietly (DEBUG at most); a test.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/LiveOrders.kt`.

## Findings (2026-10-11)

- **What the server saw**, reproduced over CIO with a raw socket that reads the start of a stream and closes:
  the next write meets `java.io.IOException: Broken pipe`, which reaches the catch-all (`ErrorAnswers.kt`) in one
  of two shapes — Ktor's `ClosedWriteChannelException` when the stream's heartbeat writes first (its job is the
  call's child, so it fails the call), or `ChannelWriteException("Cannot write to channel")` wrapping it when a
  frame does (`BaseApplicationResponse` rethrows a closed channel that way). Neither is a `CancellationException`,
  so both were logged `ERROR unhandled` and handed to katcher.
- **Fixed in the catch-all, not the route**: the heartbeat's failure never passes through the route's own code,
  and any response a client walks away from is the same non-event. A failure is the client gone when the response
  is already committed and a `ClosedWriteChannelException` is in its causes — Ktor's `ChannelWriteException` wraps
  nothing else. It is a `debug` line, not reported, not answered. Anything else on a committed response — a stream
  that throws while its client listens — is still `ERROR unhandled` and reported.
- **Tests** (`ErrorAnswersTest`, over CIO on a real port: the test engine never breaks a pipe): a client that
  leaves is not reported, whether the heartbeat's write or a frame's meets the closed connection; the control, a
  stream that fails of its own while its client listens, is reported. Mutations, each red then restored: the
  check disabled → the heartbeat shape reported `ClosedWriteChannelException: Broken pipe`; the causes not walked
  → the frame shape reported `ChannelWriteException`; every committed failure quiet → the control.
