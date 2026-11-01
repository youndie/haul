-- Demo shoppers (B-162, research D5 «Decided in B-162»): the customers «Try as a demo shopper» makes, one per press,
-- each a person of the realm whose `sub` is `demo-` and a random UUID. The row says when one was made: the day's
-- limit counts them, and the sweep forgets those made more than a day ago — the customer, and with it (the
-- cascades below and the sweep's own statements) everything that was theirs. A customer with no row here is not a
-- demo shopper, whatever their id.
CREATE TABLE demo_shoppers (
    customer_id TEXT        PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL
);

-- Indexes and foreign keys named and written the way Exposed declares them (SchemaTest).
CREATE INDEX demo_shoppers_created_at ON demo_shoppers (created_at);
ALTER TABLE demo_shoppers ADD CONSTRAINT fk_demo_shoppers_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
