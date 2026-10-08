-- Checkout (feature-checkout, research §5): customers' addresses, pickup points and lockers, the
-- courier's delivery windows with their capacity, and what each customer chose at checkout.

-- A customer's saved addresses; the form's fields (research §5, `Address`).
CREATE TABLE addresses (
    id           TEXT PRIMARY KEY,
    customer_id  TEXT        NOT NULL,
    street       TEXT        NOT NULL,
    apt          TEXT,
    city         TEXT        NOT NULL,
    zip          TEXT        NOT NULL,
    door_code    TEXT,
    courier_note TEXT,
    created_at   TIMESTAMPTZ NOT NULL
);

-- Seed data, like the catalog (research §6): `kind` is the delivery method the point serves;
-- `distance_m` is from the store's default place, absent where the sample data gives none.
CREATE TABLE pickup_points (
    id         TEXT PRIMARY KEY,
    kind       TEXT    NOT NULL CHECK (kind IN ('pickup_point', 'parcel_locker')),
    name       TEXT    NOT NULL,
    distance_m INTEGER,
    hours      TEXT    NOT NULL,
    position   INTEGER NOT NULL
);

-- A courier's window — a store day and the hour it starts — from the first place taken in it; a window
-- with no row has the server's capacity and nothing taken. `taken` only moves by a conditional update
-- (`taken < capacity`), and the check is the floor under it: no window ever holds more than it takes.
CREATE TABLE delivery_slots (
    day        DATE    NOT NULL,
    start_hour INTEGER NOT NULL,
    capacity   INTEGER NOT NULL,
    taken      INTEGER NOT NULL,
    CONSTRAINT pk_delivery_slots PRIMARY KEY (day, start_hour),
    CONSTRAINT delivery_slots_taken_within_capacity CHECK (taken >= 0 AND taken <= capacity)
);

-- Who holds each place taken (an order, B-16), so placement's compensation gives back exactly its own.
CREATE TABLE slot_reservations (
    day         DATE        NOT NULL,
    start_hour  INTEGER     NOT NULL,
    holder      TEXT        NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_slot_reservations PRIMARY KEY (day, start_hour, holder)
);

-- What a customer chose: every choice may be absent and is then the quote's default. `slot` is a
-- window's id (`2025-10-08T15`), not a key: windows have rows only once a place in them is taken.
-- `address_draft` is the last address form refused, drawn again until one is saved.
CREATE TABLE checkouts (
    customer_id   TEXT PRIMARY KEY,
    method        TEXT NOT NULL CHECK (method IN ('courier', 'pickup_point', 'parcel_locker')),
    address_id    TEXT,
    point_id      TEXT,
    slot          TEXT,
    payment       TEXT,
    address_draft JSONB
);

-- Indexes and foreign keys named and written the way Exposed declares them (SchemaTest).
CREATE INDEX addresses_customer_id ON addresses (customer_id);
ALTER TABLE addresses ADD CONSTRAINT fk_addresses_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE checkouts ADD CONSTRAINT fk_checkouts_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE checkouts ADD CONSTRAINT fk_checkouts_address_id__id FOREIGN KEY (address_id) REFERENCES addresses (id) ON DELETE SET NULL ON UPDATE RESTRICT;
ALTER TABLE checkouts ADD CONSTRAINT fk_checkouts_point_id__id FOREIGN KEY (point_id) REFERENCES pickup_points (id) ON DELETE SET NULL ON UPDATE RESTRICT;
