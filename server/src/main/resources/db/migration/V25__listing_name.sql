-- A product's listing name (B-45): what a card, a cart line, the checkout's summary and an order line write
-- — «Sony WH-1000XM6 Wireless Noise Cancelling Headphones» — where the product page writes the brand above
-- the title («WH-1000XM6 …»). `NULL` is the default: the title is the listing name. «Brand + title» is not
-- the rule, which is why it is a column and not a concatenation: the duvet's brand is its seller's, and the
-- canvas leaves it out.
--
-- An order line copies the listing name at placement into `order_lines.title` (V10's «what was bought»), so
-- an order keeps the name it was bought under. The lines placed before this migration were bought under
-- their title, which was their listing name then, and are left as they are.
--
-- V24 is another item's (B-53); this one follows it.

ALTER TABLE products ADD COLUMN listing_name TEXT;
ALTER TABLE products ADD CONSTRAINT products_listing_name_check CHECK (listing_name <> '');

-- A catalogue seeded before this migration gets the listing names the seed gives its sample products
-- (`SampleCatalog`), so a stand that was already running writes its cards as a fresh one does. On a fresh
-- database the catalog is still empty here and this touches nothing; the seed writes the same name after.
-- `ListingNameMigrationTest` holds the two lists together.
UPDATE products SET listing_name = 'Sony WH-1000XM6 Wireless Noise Cancelling Headphones' WHERE id = 'p-sony-wh-1000xm6';

-- Search reads what the cards write (feature-search): the full text is over the listing name — the title
-- when there is none — the brand and the kind, and the substring match is over the listing name as well as
-- the title. The indexes are expressions the search repository writes character for character (V3).
DROP INDEX products_search;
CREATE INDEX products_search ON products
    USING GIN (to_tsvector('english', coalesce(listing_name, title) || ' ' || brand || ' ' || coalesce(kind, '')));

CREATE INDEX products_listing_name_trgm ON products USING GIN (lower(listing_name) gin_trgm_ops);
