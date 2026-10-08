-- Search (feature-search): full text over a product's title, brand and kind, a trigram index under the
-- substring match that catches what the word stems miss, and the recent searches of a customer.
--
-- The two indexes are expressions, not columns: the search repository writes the same expressions in
-- its queries, and PostgreSQL uses an expression index only for the identical expression.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX products_search ON products
    USING GIN (to_tsvector('english', title || ' ' || brand || ' ' || coalesce(kind, '')));

CREATE INDEX products_title_trgm ON products USING GIN (lower(title) gin_trgm_ops);

-- The last ten queries of a signed-in customer, one row per distinct query, the newest by searched_at.
CREATE TABLE recent_searches (
    customer_id TEXT        NOT NULL,
    query       TEXT        NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_recent_searches PRIMARY KEY (customer_id, query)
);
