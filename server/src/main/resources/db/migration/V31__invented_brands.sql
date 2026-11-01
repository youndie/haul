-- The sample catalogue sells invented brands from invented sellers (B-166): its headphones were real companies'
-- products under their own names, from sellers named after them, beside invented reviews and prices. The seed
-- (`SampleCatalog`, `SampleHeadphones`, `SampleReviews`) now names them otherwise, ids included, since an id is the
-- product's address (`/p/<id>`); this renames a catalogue seeded before it the same way, so a stand that was already
-- running sells what a fresh one does. `InventedBrandsMigrationTest` holds the two together: a catalogue seeded the
-- old way and migrated has the hash of a fresh seed.
--
-- Each new product id keeps its old id's `String.hashCode()` modulo 2,991 (the 3 picture variants of `SeedArt` times
-- the 997 salts of `SeedReviews`), so a renamed product keeps its picture and the copy of its generated reviews, and
-- the generated rows renamed here are the rows a fresh seed writes. Everything that names a renamed row follows it:
-- the SKUs (`<product>-<n>`) and what names them, the reviews and questions with their ids (`rf-<product>-…`,
-- `qf-<product>-…`) and votes, the sellers, the order lines' copied names, and the order sagas' payloads. A photo the
-- seed stored under the old id's folder is let go (`image_key` NULL): the seed stores it again under the new one at
-- the next start. A catalogue without the old ids — a fresh one — is left as it is.

CREATE TEMPORARY TABLE renamed_products (
    old_id      TEXT PRIMARY KEY,
    new_id      TEXT NOT NULL,
    old_brand   TEXT NOT NULL,
    new_brand   TEXT NOT NULL,
    old_title   TEXT NOT NULL,
    new_title   TEXT NOT NULL,
    old_listing TEXT,
    new_listing TEXT
) ON COMMIT DROP;

INSERT INTO renamed_products VALUES
    ('p-sony-wh-1000xm6', 'p-sonora-sh-718n', 'Sony', 'Sonora',
     'WH-1000XM6 Wireless Noise Cancelling Headphones', 'SH-718N Wireless Noise Cancelling Headphones',
     'Sony WH-1000XM6 Wireless Noise Cancelling Headphones', 'Sonora SH-718N Wireless Noise Cancelling Headphones'),
    ('p-bose-qc-ultra', 'p-bellwave-hush-ultra-165a', 'Bose', 'Bellwave',
     'Bose QuietComfort Ultra Headphones', 'Bellwave Hush Ultra 165A Headphones', NULL, NULL),
    ('p-sony-wh-ch720n', 'p-sonora-sh-c388n', 'Sony', 'Sonora',
     'Sony WH-CH720N Wireless Noise Cancelling', 'Sonora SH-C388N Wireless Noise Cancelling', NULL, NULL),
    ('p-bose-qc-sandstone', 'p-bellwave-hush-h597c-sandstone', 'Bose', 'Bellwave',
     'Bose QuietComfort Headphones, Sandstone', 'Bellwave Hush H597C Headphones, Sandstone', NULL, NULL),
    ('p-sony-ult-wear', 'p-sonora-pulse-208t', 'Sony', 'Sonora',
     'Sony ULT Wear Wireless Bass Headphones', 'Sonora Pulse 208T Wireless Bass Headphones', NULL, NULL),
    ('p-bose-qc-earbuds-ii', 'p-bellwave-hush-buds-938a', 'Bose', 'Bellwave',
     'Bose QuietComfort Earbuds II', 'Bellwave Hush Buds 938A', NULL, NULL),
    ('p-sony-wf-1000xm5', 'p-sonora-se-188', 'Sony', 'Sonora',
     'Sony WF-1000XM5 Noise Cancelling Earbuds', 'Sonora SE-188 Noise Cancelling Earbuds', NULL, NULL),
    ('p-bose-soundlink-ii', 'p-bellwave-roam-580p', 'Bose', 'Bellwave',
     'Bose SoundLink Around-Ear Wireless II', 'Bellwave Roam 580P Around-Ear Wireless', NULL, NULL),
    ('p-sony-mdr-7506', 'p-sonora-monitor-630r', 'Sony', 'Sonora',
     'Sony MDR-7506 Professional Studio Headphones', 'Sonora Monitor 630R Professional Studio Headphones', NULL, NULL),
    ('p-bose-qc45-refurbished', 'p-bellwave-hush120k-refurbished', 'Bose', 'Bellwave',
     'Bose QuietComfort 45, Refurbished', 'Bellwave Hush 120K, Refurbished', NULL, NULL),
    ('p-sony-inzone-h9', 'p-sonora-arena-519', 'Sony', 'Sonora',
     'Sony INZONE H9 Wireless Gaming Headset', 'Sonora Arena 519 Wireless Gaming Headset', NULL, NULL),
    ('p-sony-wh-xb910n', 'p-sonora-sh-x527b', 'Sony', 'Sonora',
     'Sony WH-XB910N Extra Bass Noise Cancelling', 'Sonora SH-X527B Extra Bass Noise Cancelling', NULL, NULL),
    ('p-marshall-major-v', 'p-mallard-mode-146', 'Marshall', 'Mallard',
     'Marshall Major V Wireless Headphones', 'Mallard Mode 146 Wireless Headphones', NULL, NULL);

CREATE TEMPORARY TABLE renamed_sellers (
    old_id   TEXT PRIMARY KEY,
    new_id   TEXT NOT NULL,
    old_name TEXT NOT NULL,
    new_name TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO renamed_sellers VALUES
    ('s-sony-official-store', 's-sonora-audio', 'Sony Official Store', 'Sonora Audio'),
    ('s-bose-store', 's-bellwave-store', 'Bose Store', 'Bellwave Store'),
    ('s-marshall-store', 's-mallard-store', 'Marshall Store', 'Mallard Store');

-- What else names a renamed row, worked out before any of them moves.
CREATE TEMPORARY TABLE renamed_skus ON COMMIT DROP AS
SELECT k.id AS old_id, m.new_id || substr(k.id, length(m.old_id) + 1) AS new_id
FROM skus k JOIN renamed_products m ON k.product_id = m.old_id
WHERE k.id LIKE m.old_id || '-%';

CREATE TEMPORARY TABLE renamed_reviews ON COMMIT DROP AS
SELECT r.id AS old_id,
       CASE WHEN r.id LIKE 'rf-' || m.old_id || '-%' THEN 'rf-' || m.new_id || substr(r.id, length(m.old_id) + 4)
            WHEN r.id LIKE 'r-sony-%' THEN 'r-sonora-' || substr(r.id, 8)
       END AS new_id
FROM reviews r JOIN renamed_products m ON r.product_id = m.old_id
WHERE r.id LIKE 'rf-' || m.old_id || '-%' OR r.id LIKE 'r-sony-%';

CREATE TEMPORARY TABLE renamed_questions ON COMMIT DROP AS
SELECT q.id AS old_id,
       CASE WHEN q.id LIKE 'qf-' || m.old_id || '-%' THEN 'qf-' || m.new_id || substr(q.id, length(m.old_id) + 4)
            WHEN q.id LIKE 'q-sony-%' THEN 'q-sonora-' || substr(q.id, 8)
       END AS new_id
FROM questions q JOIN renamed_products m ON q.product_id = m.old_id
WHERE q.id LIKE 'qf-' || m.old_id || '-%' OR q.id LIKE 'q-sony-%';

-- The keys that point at the renamed rows, off while they move: they are `ON UPDATE RESTRICT`, and put back below as
-- Exposed declares them (SchemaTest).
ALTER TABLE products DROP CONSTRAINT fk_products_seller_id__id;
ALTER TABLE order_lines DROP CONSTRAINT fk_order_lines_seller_id__id;
ALTER TABLE shipments DROP CONSTRAINT fk_shipments_seller_id__id;
ALTER TABLE skus DROP CONSTRAINT fk_skus_product_id__id;
ALTER TABLE reviews DROP CONSTRAINT fk_reviews_product_id__id;
ALTER TABLE rating_counts DROP CONSTRAINT fk_rating_counts_product_id__id;
ALTER TABLE questions DROP CONSTRAINT fk_questions_product_id__id;
ALTER TABLE saved_items DROP CONSTRAINT fk_saved_items_product_id__id;
ALTER TABLE product_views DROP CONSTRAINT fk_product_views_product_id__id;
ALTER TABLE deals DROP CONSTRAINT fk_deals_sku_id__id;
ALTER TABLE cart_lines DROP CONSTRAINT fk_cart_lines_sku_id__id;
ALTER TABLE order_lines DROP CONSTRAINT fk_order_lines_sku_id__id;
ALTER TABLE stock_reservations DROP CONSTRAINT fk_stock_reservations_sku_id__id;
ALTER TABLE stock_restocks DROP CONSTRAINT fk_stock_restocks_sku_id__id;
ALTER TABLE helpful_votes DROP CONSTRAINT fk_helpful_votes_review_id__id;

UPDATE sellers s SET id = m.new_id, name = CASE WHEN s.name = m.old_name THEN m.new_name ELSE s.name END
FROM renamed_sellers m WHERE s.id = m.old_id;
UPDATE products p SET seller_id = m.new_id FROM renamed_sellers m WHERE p.seller_id = m.old_id;
UPDATE order_lines l SET seller_id = m.new_id FROM renamed_sellers m WHERE l.seller_id = m.old_id;
UPDATE shipments s SET seller_id = m.new_id FROM renamed_sellers m WHERE s.seller_id = m.old_id;

-- A row's copy is renamed only where it is the seed's: a title, brand or listing name somebody changed is left alone.
UPDATE products p SET
    id = m.new_id,
    title = CASE WHEN p.title = m.old_title THEN m.new_title ELSE p.title END,
    brand = CASE WHEN p.brand = m.old_brand THEN m.new_brand ELSE p.brand END,
    description = replace(p.description, m.old_title, m.new_title),
    specifications = replace(
        replace(p.specifications::text, '"' || m.old_brand || '"', '"' || m.new_brand || '"'),
        ' · LDAC · ', ' · LC3 · '
    )::jsonb,
    headline = replace(p.headline, 'the ' || m.old_brand || ' way', 'the ' || m.new_brand || ' way'),
    headline_accent = replace(p.headline_accent, 'the ' || m.old_brand || ' way', 'the ' || m.new_brand || ' way'),
    listing_name = CASE WHEN p.listing_name = m.old_listing THEN m.new_listing ELSE p.listing_name END,
    image_key = CASE WHEN p.image_key LIKE 'products/' || m.old_id || '/%' THEN NULL ELSE p.image_key END
FROM renamed_products m WHERE p.id = m.old_id;

UPDATE skus k SET id = m.new_id FROM renamed_skus m WHERE k.id = m.old_id;
UPDATE skus k SET product_id = m.new_id FROM renamed_products m WHERE k.product_id = m.old_id;
UPDATE deals d SET sku_id = m.new_id FROM renamed_skus m WHERE d.sku_id = m.old_id;
UPDATE cart_lines l SET sku_id = m.new_id FROM renamed_skus m WHERE l.sku_id = m.old_id;
UPDATE order_lines l SET sku_id = m.new_id FROM renamed_skus m WHERE l.sku_id = m.old_id;
UPDATE stock_reservations r SET sku_id = m.new_id FROM renamed_skus m WHERE r.sku_id = m.old_id;
UPDATE stock_restocks r SET sku_id = m.new_id FROM renamed_skus m WHERE r.sku_id = m.old_id;

UPDATE reviews r SET id = m.new_id FROM renamed_reviews m WHERE r.id = m.old_id;
UPDATE helpful_votes v SET review_id = m.new_id FROM renamed_reviews m WHERE v.review_id = m.old_id;
UPDATE questions q SET id = m.new_id FROM renamed_questions m WHERE q.id = m.old_id;
UPDATE reviews r SET product_id = m.new_id FROM renamed_products m WHERE r.product_id = m.old_id;
UPDATE rating_counts c SET product_id = m.new_id FROM renamed_products m WHERE c.product_id = m.old_id;
UPDATE questions q SET product_id = m.new_id FROM renamed_products m WHERE q.product_id = m.old_id;
UPDATE saved_items s SET product_id = m.new_id FROM renamed_products m WHERE s.product_id = m.old_id;
UPDATE product_views v SET product_id = m.new_id FROM renamed_products m WHERE v.product_id = m.old_id;

-- An order line copies its product's listing name at placement (V25), or its title before V25 named one.
UPDATE order_lines l SET title = m.new_listing FROM renamed_products m WHERE l.title = m.old_listing;
UPDATE order_lines l SET title = m.new_title FROM renamed_products m WHERE l.title = m.old_title;

ALTER TABLE products ADD CONSTRAINT fk_products_seller_id__id FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_lines ADD CONSTRAINT fk_order_lines_seller_id__id FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE shipments ADD CONSTRAINT fk_shipments_seller_id__id FOREIGN KEY (seller_id) REFERENCES sellers (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE skus ADD CONSTRAINT fk_skus_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE rating_counts ADD CONSTRAINT fk_rating_counts_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE questions ADD CONSTRAINT fk_questions_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE saved_items ADD CONSTRAINT fk_saved_items_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE product_views ADD CONSTRAINT fk_product_views_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE deals ADD CONSTRAINT fk_deals_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE cart_lines ADD CONSTRAINT fk_cart_lines_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_lines ADD CONSTRAINT fk_order_lines_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE stock_reservations ADD CONSTRAINT fk_stock_reservations_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE stock_restocks ADD CONSTRAINT fk_stock_restocks_sku_id__id FOREIGN KEY (sku_id) REFERENCES skus (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE helpful_votes ADD CONSTRAINT fk_helpful_votes_review_id__id FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- The order sagas and their outbox carry SKU ids, seller ids and the lines' names in their JSON: the longest names
-- first, so a listing name is renamed whole before the title inside it, and a product id with its SKUs' suffixes.
CREATE TEMPORARY TABLE renamed_words ON COMMIT DROP AS
SELECT old_id AS old_word, new_id AS new_word FROM renamed_products
UNION ALL SELECT old_title, new_title FROM renamed_products
UNION ALL SELECT old_listing, new_listing FROM renamed_products WHERE old_listing IS NOT NULL
UNION ALL SELECT old_id, new_id FROM renamed_sellers
UNION ALL SELECT old_name, new_name FROM renamed_sellers;

DO $$
DECLARE
    word RECORD;
BEGIN
    FOR word IN SELECT old_word, new_word FROM renamed_words ORDER BY length(old_word) DESC, old_word LOOP
        UPDATE petiches SET
            payload = replace(payload::text, word.old_word, word.new_word)::json,
            enriched_payload = replace(enriched_payload::text, word.old_word, word.new_word)::json,
            step_records = replace(step_records::text, word.old_word, word.new_word)::json
        WHERE strpos(payload::text, word.old_word) > 0
           OR strpos(enriched_payload::text, word.old_word) > 0
           OR strpos(step_records::text, word.old_word) > 0;
        UPDATE outbox_events SET payload = replace(payload, word.old_word, word.new_word) WHERE strpos(payload, word.old_word) > 0;
    END LOOP;
END
$$;
