---
id: B-40
title: "server: saving the address being delivered to updates it in place"
status: done
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
  test reads the customer's addresses after two saves. Saving an address identical to one already stored adds
  no row.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/domain/CheckoutCommands.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/data/ExposedCheckoutRepository.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/domain/Placement.kt`,
  `server/src/main/resources/db/migration/V14__order_address.sql`.

## Done (2026-10-08)

- **Which address a save edits — the server's state, not the request.** The form holds the address delivered to,
  so a save edits that one: the checkout's chosen address (`checkouts.address_id`) while it is still the
  customer's, else the newest — the same rule the quote uses (`CheckoutCommands.delivered`, shared by
  `CheckoutCommands.state` and `CheckoutCommands.saveAddress`). `AddressEntry` carries no id and the client's
  request (`POST /api/v1/me/addresses`) is unchanged: an id in the body would be a second source of truth for
  what the form holds, and one more value to check for «not yours». A customer with no address gets their first;
  the edited address keeps its id and its `created_at`.
- **An identical address is not stored twice.** A form equal to an address the customer already has — every
  field, after the form's own trimming, an absent field equal to an empty one (`Address.entry()`) — makes that
  address the checkout's and writes no address. It matters for customers who used the form before B-40 (a row
  per save): edited in place, the chosen row would otherwise become a copy of an older one. Choosing it leaves
  the chosen row as it was.
- **Past orders keep their address: a copy, not the row** (decided as product owner). `orders.address_id`
  referenced the saved address (V10, B-16), so an edit in place would have rewritten the address of every order
  placed to it — Maya's order to «4F» reading «5B». Rejected: copy-on-write (an address an order used is never
  edited; the save adds a new row instead) — it brings back the duplicate this item removes, the shopper's list
  keeps the old «4F», and a placement racing a save could still read the edited row. Taken: the order copies the
  address at placement, as it already copies every other value of the quote (V10: «the checkout and the cart move
  on, the order does not»). **Migration V14** (`server/src/main/resources/db/migration/V14__order_address.sql`):
  `orders.address JSONB` (`AddressEntry`), backfilled from the row each existing order names — their rows were
  never edited before this change. `address_id` stays as which saved address it was. The copy rides the saga's
  payload (`OrderPayload.address`, defaulted, so a saga stored before V14 still reads); `NewOrder.address` is
  what the order page (B-18) should draw.
- **The quote's fingerprint names the address's fields, not only its id** (`Quote.fingerprint`): before, every
  save changed the id and so the fingerprint; now the id stays, and a page drawn with «4F» would otherwise place
  to «5B» from another tab. Pickup and locker fingerprints are unchanged (no address); courier ones changed once,
  so the four courier wire bodies the client draws its goldens from carry the new value (only `quote` differs —
  `CheckoutFixturesTest`).
- **Tests**: `CheckoutRoutesTest` — `saving the address form twice edits the one address in place` (two saves,
  the customer's addresses read from the table: one row, the first save's id, at «5B»; the form holds «5B»; the
  fingerprint changed), `an address equal to a saved one is chosen and not stored again` (an older address
  written as a save before B-40 left it; the form saved equal to it, padded and then exact: two rows, neither
  rewritten, the older one delivered to). `PlacementRoutesTest` — `an order keeps the address it was placed to
  when the saved address is edited`, `a quote whose address was edited since the page was drawn is refused`
  (`409 cart_changed`, nothing placed; the redrawn page places to «5B»). `OrderAddressMigrationTest` — `an order
  placed before V14 keeps the address its id named` (a database migrated to V12, an order written, then V14).
  `Ledger.addresses`, `PostgresHarness.freshDatabase(upTo)` added.

## Findings (2026-10-08)

- **V13 must reach `main` before V14, or be renumbered.** Flyway runs with `validateOnMigrate` and without
  `outOfOrder` (`server/src/main/kotlin/io/github/youndie/haul/db/Database.kt`): a database already at V14 refuses
  to start once a V13 appears below it.
- **One `409 cart_changed` per open courier page at the deploy**: the fingerprint's formula changed, so a page
  drawn before the deploy is refused once and redrawn.
- **`saveAddress` reads what the form held outside the write's transaction** (`CheckoutCommands.saveAddress`
  reads the checkout and the addresses, then the repository writes in a transaction of its own). Two saves racing
  from two tabs could each edit what they read; the last one wins, which is what one form saved twice does anyway.
