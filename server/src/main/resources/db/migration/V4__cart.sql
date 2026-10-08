-- Guests, carts and promo codes (feature-cart, feature-identity, research D5).
--
-- A cart belongs to exactly one owner: a guest the server issued, or a customer by the shildik `sub`
-- (customers arrive with sign-in, B-12, so `customer_id` has no table to reference yet). A line is
-- keyed by its SKU and remembers the price and the stock it was added (or last acknowledged) at,
-- which is how a changed line is told apart (feature-cart); `position` is the order lines were added
-- in, which is the order the cart shows them (a clock cannot say it: two adds can share an instant).

CREATE TABLE guests (
    id         TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);

-- Seed data, like the catalog: `percent_off` of the selected items at their price, capped at
-- `cap_cents` when it is set, valid from `starts_at` until `ends_at` (exclusive).
CREATE TABLE promo_codes (
    code        TEXT PRIMARY KEY,
    percent_off INTEGER     NOT NULL CHECK (percent_off BETWEEN 1 AND 100),
    cap_cents   INTEGER,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL
);

-- `promo_attempt` and `promo_error` are the last refused code and why (`Cart_PromoError`): the tree
-- draws them until the next promo command or line change.
CREATE TABLE carts (
    id            TEXT PRIMARY KEY,
    guest_id      TEXT,
    customer_id   TEXT,
    promo_code    TEXT,
    promo_attempt TEXT,
    promo_error   TEXT,
    CONSTRAINT carts_one_owner CHECK ((guest_id IS NULL) <> (customer_id IS NULL))
);

CREATE TABLE cart_lines (
    cart_id          TEXT        NOT NULL,
    sku_id           TEXT        NOT NULL,
    quantity         INTEGER     NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    selected         BOOLEAN     NOT NULL,
    seen_price_cents INTEGER     NOT NULL,
    seen_in_stock    BOOLEAN     NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL,
    position         INTEGER     NOT NULL,
    CONSTRAINT pk_cart_lines PRIMARY KEY (cart_id, sku_id)
);

-- Unique indexes and foreign keys named and written the way Exposed declares them (SchemaTest).
CREATE UNIQUE INDEX carts_guest_id_unique ON carts (guest_id);
CREATE UNIQUE INDEX carts_customer_id_unique ON carts (customer_id);
ALTER TABLE carts ADD CONSTRAINT fk_carts_guest_id__id FOREIGN KEY (guest_id) REFERENCES guests (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE carts ADD CONSTRAINT fk_carts_promo_code__code FOREIGN KEY (promo_code) REFERENCES promo_codes (code) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE cart_lines ADD CONSTRAINT fk_cart_lines_cart_id__id FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE cart_lines ADD CONSTRAINT fk_cart_lines_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
