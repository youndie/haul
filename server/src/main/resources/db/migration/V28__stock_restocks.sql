-- The units a return put back on the shelf (B-88): written when the returns' simulator refunds a return, one
-- row per SKU under the return's key (`return:<order>`), in the transaction that adds them to `skus.stock`.
-- The key is what makes it once: a pass that died after the restock and before the move finds its rows and
-- adds nothing more. The order's own `stock_reservations` rows stay what placement took.
CREATE TABLE stock_restocks (
    key          TEXT        NOT NULL,
    sku_id       TEXT        NOT NULL,
    quantity     INTEGER     NOT NULL,
    restocked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_stock_restocks PRIMARY KEY (key, sku_id),
    CONSTRAINT stock_restocks_quantity_check CHECK (quantity > 0)
);

ALTER TABLE stock_restocks ADD CONSTRAINT fk_stock_restocks_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
