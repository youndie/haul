-- Product views (B-25, feature-recommendations): the products a signed-in customer opened, which «Picked for
-- you» on the home page is computed from (research §5, `ProductView`: the last 20). A guest's views are not
-- kept — a guest gets no block.
--
-- One row per customer and product, `viewed_at` the latest time the page was opened: the product tree is
-- fetched again on a tab, a variant or a refresh, and each of those is the same view, not another vote for
-- the product's category. The repository keeps the newest twenty by `viewed_at` (then by product id) and
-- deletes the rest on the write that pushed them out.
CREATE TABLE product_views (
    customer_id TEXT        NOT NULL,
    product_id  TEXT        NOT NULL,
    viewed_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_product_views PRIMARY KEY (customer_id, product_id)
);

-- Foreign keys named and written the way Exposed declares them (SchemaTest). A customer's views go with the
-- customer, as their Saved list does.
ALTER TABLE product_views ADD CONSTRAINT fk_product_views_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
ALTER TABLE product_views ADD CONSTRAINT fk_product_views_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
