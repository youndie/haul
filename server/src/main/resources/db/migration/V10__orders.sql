-- Placement (feature-checkout, feature-orders, research D4): the order saga's own tables, what it
-- reserves and authorises, and the order it places.

-- petich's tables, hand-written because petich-postgres ships no DDL: the tables describe themselves
-- in Kotlin (`PetichTable`, `OutboxEventsTable`, `IdempotencyKeysTable`), and SchemaTest asks Exposed
-- whether this file agrees with them. The JSON-shaped columns are `json`, as `PetichTable` declares them
-- with Exposed's `json()`; petich's upgrade notes print the native store's TEXT, which would make the
-- schema disagree with its own declaration.
CREATE TABLE petiches (
    id                        VARCHAR(255) PRIMARY KEY,
    type                      VARCHAR(100) NOT NULL,
    current_phase             VARCHAR(50)  NOT NULL,
    current_interceptor_index INT          NOT NULL,
    status                    VARCHAR(50)  NOT NULL,
    payload                   JSON         NOT NULL,
    enriched_payload          JSON         NOT NULL,
    version                   BIGINT       NOT NULL,
    suspended_until           BIGINT       NULL,
    compensation_attempts     INT          NOT NULL DEFAULT 0,
    compensating_from_index   INT          NULL,
    compensating_towards      VARCHAR(32)  NULL,
    updated_at                BIGINT       NOT NULL DEFAULT 0,
    chain_fingerprint         VARCHAR(64)  NULL,
    step_records              JSON         NOT NULL DEFAULT '{}'::json
);
CREATE INDEX idx_petiches_status_suspended_until ON petiches (status, suspended_until);
-- A saga row is updated at every step boundary; room on the page keeps those updates off the index
-- (`PetichTable.tuningStatements()`). The table is empty here, so the lock this takes costs nothing.
ALTER TABLE petiches SET (fillfactor = 80);

CREATE TABLE outbox_events (
    id          VARCHAR(255) PRIMARY KEY,
    type        VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    status      VARCHAR(20)  DEFAULT 'PENDING' NOT NULL,
    retry_count INT          DEFAULT 0 NOT NULL,
    created_at  BIGINT       NOT NULL
);
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);

-- Placement's `Idempotency-Key`, per customer, with the fingerprint of the request first sent under it
-- (petich-idempotency): the same key with another request is refused.
CREATE TABLE idempotency_keys (
    key                 VARCHAR(255) PRIMARY KEY,
    request_fingerprint VARCHAR(64)  NOT NULL,
    created_at          BIGINT       NOT NULL
);

-- An order number is `HL-` and the next of this sequence. It starts where the canvas's checkout
-- fixture places its order (research §6, #HL-48302), so the first order a fresh store places is the
-- one the `Order_Placed` artboard draws.
CREATE SEQUENCE order_numbers START WITH 48302;

-- The order (research §5, `Order`): `status` is the saga's side of it — `placing` while the saga runs,
-- `placed` once it confirmed, `cancelled` once it was undone, with `cancel_reason` (`payment_declined`,
-- or `failed` for a fault). What the shopper chose and paid is copied from the quote at placement:
-- the checkout and the cart move on, the order does not.
CREATE TABLE orders (
    id             TEXT PRIMARY KEY,
    saga_id        TEXT        NOT NULL,
    customer_id    TEXT        NOT NULL,
    status         TEXT        NOT NULL CHECK (status IN ('placing', 'placed', 'cancelled')),
    cancel_reason  TEXT,
    method         TEXT        NOT NULL CHECK (method IN ('courier', 'pickup_point', 'parcel_locker')),
    address_id     TEXT,
    point_id       TEXT,
    slot           TEXT,
    payment        TEXT        NOT NULL,
    promo_code     TEXT,
    items_cents    INTEGER     NOT NULL,
    discount_cents INTEGER     NOT NULL,
    delivery_cents INTEGER     NOT NULL,
    total_cents    INTEGER     NOT NULL,
    points         INTEGER     NOT NULL,
    placed_at      TIMESTAMPTZ NOT NULL
);

-- What was bought, at the price it was bought for, in the cart's order.
CREATE TABLE order_lines (
    order_id    TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    sku_id      TEXT    NOT NULL,
    seller_id   TEXT    NOT NULL,
    title       TEXT    NOT NULL,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    price_cents INTEGER NOT NULL,
    list_cents  INTEGER NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (order_id, position)
);

-- One shipment per seller (research §5, `Shipment`); B-16 writes `placed` and `cancelled`, the
-- fulfilment simulator (B-17) the rest.
CREATE TABLE shipments (
    id        TEXT PRIMARY KEY,
    order_id  TEXT    NOT NULL,
    seller_id TEXT    NOT NULL,
    position  INTEGER NOT NULL,
    status    TEXT    NOT NULL CHECK (status IN ('placed', 'packed', 'in_transit', 'ready_for_pickup', 'delivered', 'picked_up', 'cancelled'))
);

-- Stock reserved by an order (its holder): the units are taken off `skus.stock` when reserved and put
-- back when released, so a holder's rows are exactly what its compensation gives back.
CREATE TABLE stock_reservations (
    holder      TEXT        NOT NULL,
    sku_id      TEXT        NOT NULL,
    quantity    INTEGER     NOT NULL CHECK (quantity > 0),
    reserved_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_stock_reservations PRIMARY KEY (holder, sku_id)
);

-- The payment simulator's ledger (research D4): one authorisation per key the saga names it by, with
-- what the simulator answered — `authorised` or `declined` — and `voided` once the saga undid it.
-- Capture per shipment is B-17's.
CREATE TABLE payment_authorisations (
    key          TEXT PRIMARY KEY,
    order_id     TEXT        NOT NULL,
    method       TEXT        NOT NULL,
    amount_cents INTEGER     NOT NULL,
    status       TEXT        NOT NULL CHECK (status IN ('authorised', 'declined', 'voided')),
    created_at   TIMESTAMPTZ NOT NULL
);

-- Indexes and foreign keys named and written the way Exposed declares them (SchemaTest).
CREATE UNIQUE INDEX orders_saga_id_unique ON orders (saga_id);
CREATE INDEX orders_customer_id ON orders (customer_id);
ALTER TABLE orders ADD CONSTRAINT fk_orders_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE order_lines ADD CONSTRAINT fk_order_lines_order_id__id FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE order_lines ADD CONSTRAINT fk_order_lines_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_lines ADD CONSTRAINT fk_order_lines_seller_id__id FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE UNIQUE INDEX shipments_order_id_seller_id_unique ON shipments (order_id, seller_id);
ALTER TABLE shipments ADD CONSTRAINT fk_shipments_order_id__id FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE shipments ADD CONSTRAINT fk_shipments_seller_id__id FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE stock_reservations ADD CONSTRAINT fk_stock_reservations_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
