-- The headline over a product's description (screen-product, `Product_Description`: «Silence, *tuned
-- to you*»), and the part of it drawn in the accent face. The accent is optional, and when there is one
-- it is a piece of the headline: the client finds it in the headline to draw it, so an accent that does
-- not occur there would be drawn nowhere.
--
-- A catalogue seeded before this migration has no headline to keep; its rows take their title, and
-- only a fresh seed carries the headlines the seed gives (B-33). V4 and V5 belong to items on parallel
-- branches, so on main this file may for a while be the only migration after V3.

ALTER TABLE products ADD COLUMN headline TEXT;
UPDATE products SET headline = title;
ALTER TABLE products ALTER COLUMN headline SET NOT NULL;

ALTER TABLE products ADD COLUMN headline_accent TEXT;
ALTER TABLE products ADD CONSTRAINT products_headline_accent_in_headline
    CHECK (headline_accent IS NULL OR strpos(headline, headline_accent) > 0);
