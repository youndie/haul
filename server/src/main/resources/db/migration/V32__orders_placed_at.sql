-- The public feed of live orders (B-164) reads the last orders placed on the whole store, newest first, on every load
-- and every move it is drawn again for: an index in that order, so the page reads twenty rows of it, not every order
-- the synthetic shoppers ever placed. The order's id breaks a tie of the same moment, as the feed sorts.
-- V31 is B-166's.
CREATE INDEX orders_placed_at ON orders (placed_at, id);
