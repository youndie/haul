-- Haul Plus and points (feature-membership, B-23; research D6–D7).

-- A customer's Haul Plus membership, one row at most: it is started once — a 30-day trial, or, for the
-- sample data, a membership older than the store's records — and v1 has no way to end one, so the row is
-- the membership for good. `customers.plus` stays the flag every price reads (free delivery, double
-- points); the trial writes it in the same transaction as this row.
--
-- `paid_from` is the first day billed at $4.99 a month (simulated: nothing is charged), the trial's end
-- for a trial; the membership renews on that day of every month after it. While the store's date is
-- before it, the membership is a trial.
--
-- `carried_savings_cents` is what the membership saved on delivery before the store kept orders, counted
-- in `carried_savings_year` only: research §6's $186 for Maya, whose orders no seed holds. Every other
-- saving is read off the orders (`orders.delivery_waived_cents`).
CREATE TABLE memberships (
    customer_id           TEXT        PRIMARY KEY,
    started_at            TIMESTAMPTZ NOT NULL,
    trial                 BOOLEAN     NOT NULL,
    paid_from             DATE        NOT NULL,
    carried_savings_cents INT         NOT NULL DEFAULT 0,
    carried_savings_year  INT
);

-- The points ledger: append-only, one row per movement, the balance their sum. `key` names the movement
-- (`earned:<shipment>`, `redeemed:<order>`, `returned:<order>`, `reversed:<return>`, `opening:<customer>`)
-- and is what makes each write idempotent: a movement written twice — a saga member re-run after a
-- restart, two simulator passes at once — finds its row there (`ON CONFLICT DO NOTHING`).
--
-- earned   +, a shipment delivered or collected: its share of the order's points;
-- redeemed −, an order placed with «Use N points»;
-- returned +, redeemed points given back: the order cancelled, or a return refunded;
-- reversed −, earned points taken back by a return.
CREATE TABLE points_entries (
    key         TEXT        PRIMARY KEY,
    customer_id TEXT        NOT NULL,
    kind        TEXT        NOT NULL,
    points      INT         NOT NULL,
    order_id    TEXT,
    at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT points_entries_kind_check CHECK (kind IN ('earned', 'redeemed', 'returned', 'reversed')),
    CONSTRAINT points_entries_sign_check CHECK (
        (kind IN ('earned', 'returned') AND points > 0) OR (kind IN ('redeemed', 'reversed') AND points < 0)
    )
);

CREATE INDEX points_entries_customer_id ON points_entries (customer_id);

-- Foreign keys named and written the way Exposed declares them (SchemaTest). A customer's membership and
-- points go with the customer, as their cart does.
ALTER TABLE memberships ADD CONSTRAINT fk_memberships_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE points_entries ADD CONSTRAINT fk_points_entries_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;

-- An order keeps what Plus waived on it and the points it was paid with: `delivery_waived_cents` is the
-- fee a non-member would have paid ($5.99 under $35 of items, research D7), what «saved on delivery this
-- year» adds up; `points_redeemed` is the points taken off its total (1 point = 1 cent), which a cancelled
-- order gives back. Orders placed before this migration had neither.
ALTER TABLE orders ADD COLUMN delivery_waived_cents INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN points_redeemed INT NOT NULL DEFAULT 0;

-- The checkout's points toggle (`CheckoutChoice.usePoints`): off until the customer turns it on.
ALTER TABLE checkouts ADD COLUMN use_points BOOLEAN NOT NULL DEFAULT FALSE;
