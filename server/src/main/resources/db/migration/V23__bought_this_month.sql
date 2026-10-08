-- «12K bought this month» on the product page (B-52, screen-product): the units of a product in the orders
-- placed in the last 30 store days that were placed and not cancelled, plus `bought_base`.
--
-- `bought_base` is the month of sales the store never held as orders: a fixed number the seed gives the
-- sample products so the stand reads like the canvas, and 0 for every other product. It is a stand-in, not a
-- record — it does not age out of the window as orders do — and seeded orders were the alternative refused:
-- research §6 seeds no orders, an order needs a customer, a saga and shipments, and orders dated at the
-- canvas's «now» would leave the 30-day window on the stand's wall clock within a month.
--
-- V22 is another item's (B-29); this one follows it.

ALTER TABLE products ADD COLUMN bought_base INTEGER NOT NULL DEFAULT 0;
ALTER TABLE products ADD CONSTRAINT products_bought_base_check CHECK (bought_base >= 0);

-- A catalogue seeded before this migration gets the bases the seed gives its sample products
-- (`SampleCatalog`), so a stand that was already running reads like a fresh one. On a fresh database the
-- catalog is still empty here and these touch nothing; the seed writes the same numbers after.
-- `BoughtBaseMigrationTest` holds the two lists together.
UPDATE products SET bought_base = 12340 WHERE id = 'p-sony-wh-1000xm6';
UPDATE products SET bought_base = 2180 WHERE id = 'p-linen-duvet-cover-set';
UPDATE products SET bought_base = 840 WHERE id = 'p-stoneware-mug';

-- The count reads a product's order lines through its SKUs; without this every product page scans every
-- line ever ordered. Named the way Exposed declares it (SchemaTest).
CREATE INDEX order_lines_sku_id ON order_lines (sku_id);
