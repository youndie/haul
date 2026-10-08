-- What the category facets filter on (feature-browse): the features a product has, its kind within the
-- category (the chips above the grid), and how many days its seller takes to dispatch it (the delivery
-- day on a card).

ALTER TABLE products ADD COLUMN features JSONB NOT NULL DEFAULT '[]';
ALTER TABLE products ADD COLUMN kind TEXT;
ALTER TABLE products ADD COLUMN dispatch_days INTEGER NOT NULL DEFAULT 0;
