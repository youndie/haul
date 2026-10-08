-- Fulfilment (feature-orders, research D4): the simulated world moves each shipment along, and the card
-- is charged per shipment when it ships.

-- A pickup shipment's code (feature-orders: «a pickup shipment keeps a 4-digit pickup code»), written when
-- it becomes `ready_for_pickup`; a courier shipment never has one.
ALTER TABLE shipments ADD COLUMN pickup_code TEXT;

-- When each shipment entered each status, by the simulator's clock: the shipment's history, which the order
-- page draws (its progress, «kept until», the return window) and which the simulator reads to know when the
-- next step is due. `placed` is stamped when the simulated seller first sees the shipment. One row per status
-- a shipment passes through, written once.
CREATE TABLE shipment_events (
    shipment_id TEXT        NOT NULL,
    status      TEXT        NOT NULL CHECK (status IN ('placed', 'packed', 'in_transit', 'ready_for_pickup', 'delivered', 'picked_up')),
    entered_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_shipment_events PRIMARY KEY (shipment_id, status)
);

-- The payment simulator's captures (research D4, «your card is charged when the order ships»): one row per
-- shipment that shipped, named by the key the caller captured under, taken out of one authorisation. The
-- captures of an authorisation never add up to more than it holds, and a key captured once is never
-- captured again.
CREATE TABLE payment_captures (
    key               TEXT PRIMARY KEY,
    authorisation_key TEXT        NOT NULL,
    order_id          TEXT        NOT NULL,
    amount_cents      INTEGER     NOT NULL CHECK (amount_cents > 0),
    captured_at       TIMESTAMPTZ NOT NULL
);

-- Indexes and foreign keys named and written the way Exposed declares them (SchemaTest).
ALTER TABLE shipment_events ADD CONSTRAINT fk_shipment_events_shipment_id__id FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE ON UPDATE RESTRICT;
CREATE INDEX payment_captures_authorisation_key ON payment_captures (authorisation_key);
ALTER TABLE payment_captures ADD CONSTRAINT fk_payment_captures_authorisation_key__key FOREIGN KEY (authorisation_key) REFERENCES payment_authorisations (key) ON DELETE RESTRICT ON UPDATE RESTRICT;
