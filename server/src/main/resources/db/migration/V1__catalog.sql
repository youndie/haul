-- The catalog: what the browse half reads (feature-browse, feature-product). Orders, carts and
-- customers arrive with the features that own them, each in a migration of its own.

CREATE TABLE categories (
    slug        TEXT PRIMARY KEY,
    parent_slug TEXT,
    name        TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    tone        TEXT    NOT NULL,
    label       TEXT    NOT NULL
);

CREATE TABLE sellers (
    id                 TEXT PRIMARY KEY,
    name               TEXT          NOT NULL,
    rating             NUMERIC(2, 1) NOT NULL,
    positive_percent   INTEGER       NOT NULL,
    years_on_haul      INTEGER       NOT NULL
);

CREATE TABLE products (
    id              TEXT PRIMARY KEY,
    seller_id       TEXT          NOT NULL,
    category_slug   TEXT          NOT NULL,
    title           TEXT          NOT NULL,
    brand           TEXT          NOT NULL,
    description     TEXT          NOT NULL,
    specifications  JSONB         NOT NULL,
    rating          NUMERIC(2, 1) NOT NULL,
    reviews_count   INTEGER       NOT NULL,
    questions_count INTEGER       NOT NULL,
    tone            TEXT          NOT NULL,
    label           TEXT          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX products_category ON products (category_slug);

CREATE TABLE skus (
    id              TEXT PRIMARY KEY,
    product_id      TEXT    NOT NULL,
    position        INTEGER NOT NULL,
    options         JSONB   NOT NULL,
    price_cents     INTEGER NOT NULL,
    old_price_cents INTEGER,
    stock           INTEGER NOT NULL
);

CREATE INDEX skus_product ON skus (product_id);

CREATE TABLE campaigns (
    slug                  TEXT PRIMARY KEY,
    title                 TEXT        NOT NULL,
    subtitle              TEXT        NOT NULL,
    position              INTEGER     NOT NULL,
    tone                  TEXT        NOT NULL,
    starts_at             TIMESTAMPTZ NOT NULL,
    ends_at               TIMESTAMPTZ NOT NULL,
    plus_early_access_at  TIMESTAMPTZ
);

CREATE TABLE deals (
    id          TEXT PRIMARY KEY,
    sku_id      TEXT        NOT NULL,
    price_cents INTEGER     NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL
);

-- Foreign keys named and written the way Exposed declares them, so SchemaTest sees no difference.
ALTER TABLE categories ADD CONSTRAINT fk_categories_parent_slug__slug FOREIGN KEY (parent_slug) REFERENCES categories (slug) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE products ADD CONSTRAINT fk_products_seller_id__id FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE products ADD CONSTRAINT fk_products_category_slug__slug FOREIGN KEY (category_slug) REFERENCES categories (slug) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE skus ADD CONSTRAINT fk_skus_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE deals ADD CONSTRAINT fk_deals_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
