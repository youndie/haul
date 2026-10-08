-- «Helpful» votes on reviews (B-43, feature-reviews: one helpful vote per customer per review, and the
-- author cannot vote on their own). `reviews.helpful` stays the count the page draws — the seed's 48 stand
-- for votes this table does not hold one by one — and a vote moves it by one in the same transaction as
-- its row: the row goes in and the count goes up, or the row goes out and the count goes down.

-- One row per customer per review. The primary key is the unique index the vote is written against
-- (`ON CONFLICT DO NOTHING`): a vote sent twice, or two at once, finds the row there and counts nothing.
CREATE TABLE helpful_votes (
    review_id   TEXT        NOT NULL,
    customer_id TEXT        NOT NULL,
    voted_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_helpful_votes PRIMARY KEY (review_id, customer_id)
);

-- Foreign keys named and written the way Exposed declares them (SchemaTest).
ALTER TABLE helpful_votes ADD CONSTRAINT fk_helpful_votes_review_id__id FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE helpful_votes ADD CONSTRAINT fk_helpful_votes_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
