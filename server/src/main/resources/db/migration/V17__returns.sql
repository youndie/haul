-- Returns and refunds (B-21, feature-orders: «a delivered order can be returned within 30 days»). A return is
-- asked for on the order's page, collected by the simulated courier and refunded once it is back — the
-- canvas's «Requested · Picked up · Refunded». V16 is another item's; this one follows it.

-- One return per order: the order's id is the key, so a second request — or two at once — finds the row
-- there. `refund_cents` is what its lines give back (delivery is not), `points` what it takes back of the
-- points the order earned. Each status is stamped when it is entered, by the simulator's clock.
CREATE TABLE returns (
    order_id     TEXT PRIMARY KEY,
    reason       TEXT        NOT NULL,
    refund_cents INTEGER     NOT NULL CHECK (refund_cents >= 0),
    points       INTEGER     NOT NULL CHECK (points >= 0),
    status       TEXT        NOT NULL CHECK (status IN ('requested', 'picked_up', 'refunded')),
    requested_at TIMESTAMPTZ NOT NULL,
    picked_up_at TIMESTAMPTZ,
    refunded_at  TIMESTAMPTZ
);

-- The lines going back, whole, by their position in the order (`order_lines.position`).
CREATE TABLE return_lines (
    order_id     TEXT    NOT NULL,
    position     INTEGER NOT NULL,
    refund_cents INTEGER NOT NULL CHECK (refund_cents >= 0),
    CONSTRAINT pk_return_lines PRIMARY KEY (order_id, position)
);

-- The payment simulator's refunds: one row per key the caller refunds under, out of what the order was
-- captured. The refunds of an order never add up to more than its captures, and a key refunded once is
-- never refunded again.
CREATE TABLE payment_refunds (
    key          TEXT PRIMARY KEY,
    order_id     TEXT        NOT NULL,
    amount_cents INTEGER     NOT NULL CHECK (amount_cents > 0),
    refunded_at  TIMESTAMPTZ NOT NULL
);

-- Indexes and foreign keys named and written the way Exposed declares them (SchemaTest).
CREATE INDEX returns_status ON returns (status);
ALTER TABLE returns ADD CONSTRAINT fk_returns_order_id__id FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE return_lines ADD CONSTRAINT fk_return_lines_order_id__order_id FOREIGN KEY (order_id) REFERENCES returns (order_id) ON DELETE CASCADE ON UPDATE RESTRICT;
CREATE INDEX payment_refunds_order_id ON payment_refunds (order_id);
