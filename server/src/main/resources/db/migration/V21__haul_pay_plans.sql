-- Haul Pay's plan (B-24, feature-membership; research D6): four interest-free payments two weeks apart,
-- simulated. V20 is another item's; this one follows it.
--
-- Placement authorises the whole total as before (B-16); the plan is what is charged out of that
-- authorisation, instead of each shipment's share (B-17). It starts when the order's first shipment leaves
-- for the road: the first payment is taken then, the others two weeks apart after it, by the simulator's
-- clock. A plan is written once, when it starts; before that the order's page shows the schedule the
-- order's total gives.
--
-- `reduced_cents` is what the order's return took off the payments still owed (one return per order, so
-- one reduction per plan): `NULL` until the return is refunded, so a pass that dies after the reduction and
-- before the refund finds it there and gives back only the rest.
CREATE TABLE instalment_plans (
    order_id      TEXT        PRIMARY KEY,
    total_cents   INTEGER     NOT NULL CHECK (total_cents > 0),
    started_at    TIMESTAMPTZ NOT NULL,
    reduced_cents INTEGER     CHECK (reduced_cents >= 0),
    reduced_at    TIMESTAMPTZ
);

-- The plan's four payments. `amount_cents` is the schedule's (the total ÷ 4, rounded, the last taking the
-- rest), `reduced_cents` what a return took off it. A payment is
--
--   scheduled  owed, taken at `next_attempt_at` — its due date, or a day after a declined attempt;
--   collecting claimed by a pass, with what it charges frozen in `charge_cents` — a pass that died here
--              leaves it for the next one, which asks the processor again under the same key;
--   paid       charged, at `paid_at`;
--   covered    nothing left to charge: a return took all of it off;
--   overdue    declined twice (`attempts`); the plan stops here — v1 has no collections.
CREATE TABLE instalments (
    order_id        TEXT        NOT NULL,
    number          INTEGER     NOT NULL CHECK (number IN (1, 2, 3, 4)),
    amount_cents    INTEGER     NOT NULL CHECK (amount_cents > 0),
    reduced_cents   INTEGER     NOT NULL CHECK (reduced_cents >= 0),
    due_at          TIMESTAMPTZ NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('scheduled', 'collecting', 'paid', 'covered', 'overdue')),
    attempts        INTEGER     NOT NULL CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    charge_cents    INTEGER     CHECK (charge_cents >= 0),
    paid_at         TIMESTAMPTZ,
    declined_at     TIMESTAMPTZ,
    CONSTRAINT pk_instalments PRIMARY KEY (order_id, number)
);

-- Indexes and foreign keys named and written the way Exposed declares them (SchemaTest).
CREATE INDEX instalments_status ON instalments (status);
ALTER TABLE instalment_plans ADD CONSTRAINT fk_instalment_plans_order_id__id FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE instalments ADD CONSTRAINT fk_instalments_order_id__order_id FOREIGN KEY (order_id) REFERENCES instalment_plans (order_id) ON DELETE CASCADE ON UPDATE RESTRICT;
