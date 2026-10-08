-- The Saved list (B-20, feature-account): the products a customer hearted or saved for later from the
-- cart, one row each (research §5, `SavedItem`). `saved_price_cents` is the product's price on the day it
-- was saved — its cheapest SKU in stock then, as a card shows it — which «Price dropped» compares with the
-- cheapest in stock now. `saved_at` orders the list, newest first.

-- The primary key is the unique index a save is written against (`ON CONFLICT DO NOTHING`): a product
-- saved twice is in the list once, at the price and the day of the first save.
CREATE TABLE saved_items (
    customer_id       TEXT        NOT NULL,
    product_id        TEXT        NOT NULL,
    saved_price_cents INT         NOT NULL,
    saved_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_saved_items PRIMARY KEY (customer_id, product_id)
);

-- Foreign keys named and written the way Exposed declares them (SchemaTest). A customer's list goes with
-- the customer, as their cart does.
ALTER TABLE saved_items ADD CONSTRAINT fk_saved_items_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE saved_items ADD CONSTRAINT fk_saved_items_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
