-- Reviews and questions (feature-reviews, research §5: `Review`, `Question`, owned by a `Product`, written
-- by a `Customer`). Answers are seed data only: there is no seller side to write one.

-- A review. `customer_id` is the author when they are a customer here; the seed's earlier reviews have an
-- author's name and no account. `author` is the name as the page writes it («Maya K.»), taken when the
-- review is written. `verified` is «Verified purchase», decided then: the author had a delivered shipment
-- holding the product. `helpful` is how many people found it helpful; `tone` is the avatar's tile and
-- `photos` the tones of its photo placeholders (research D8).
CREATE TABLE reviews (
    id          TEXT PRIMARY KEY,
    product_id  TEXT        NOT NULL,
    customer_id TEXT,
    author      TEXT        NOT NULL,
    rating      INTEGER     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title       TEXT        NOT NULL,
    body        TEXT        NOT NULL,
    verified    BOOLEAN     NOT NULL,
    helpful     INTEGER     NOT NULL DEFAULT 0,
    tone        TEXT        NOT NULL,
    photos      JSONB       NOT NULL DEFAULT '[]',
    created_at  TIMESTAMPTZ NOT NULL
);

-- One review per customer per product: the second is refused (`409 review_exists`), and this is the
-- floor under that check when two arrive at once. The seed's authors without an account have no
-- `customer_id`, and nulls never collide.
CREATE UNIQUE INDEX reviews_one_per_customer ON reviews (product_id, customer_id);

-- How many reviews gave a product each number of stars: the histogram. The catalog keeps the count and
-- the average on `products` (`reviews_count`, `rating`), which every card reads; writing a review moves
-- all three in one transaction. The canvas's counts are at marketplace scale (2,341 reviews), so a
-- product's rows here stand for reviews the seed does not hold one by one; a product with no rows has no
-- histogram to draw.
CREATE TABLE rating_counts (
    product_id TEXT    NOT NULL,
    stars      INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    count      INTEGER NOT NULL CHECK (count >= 0),
    CONSTRAINT pk_rating_counts PRIMARY KEY (product_id, stars)
);

-- A question to the seller. `answer` and `answered_at` are set together or not at all, and only by the
-- seed; a question without them is «Not answered yet».
CREATE TABLE questions (
    id          TEXT PRIMARY KEY,
    product_id  TEXT        NOT NULL,
    customer_id TEXT,
    text        TEXT        NOT NULL,
    asked_at    TIMESTAMPTZ NOT NULL,
    answer      TEXT,
    answered_at TIMESTAMPTZ,
    CONSTRAINT questions_answered_together CHECK ((answer IS NULL AND answered_at IS NULL) OR (answer IS NOT NULL AND answered_at IS NOT NULL))
);

CREATE INDEX questions_product_id ON questions (product_id);

-- Foreign keys named and written the way Exposed declares them (SchemaTest).
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE SET NULL ON UPDATE RESTRICT;
ALTER TABLE rating_counts ADD CONSTRAINT fk_rating_counts_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE questions ADD CONSTRAINT fk_questions_product_id__id FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE questions ADD CONSTRAINT fk_questions_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE SET NULL ON UPDATE RESTRICT;
