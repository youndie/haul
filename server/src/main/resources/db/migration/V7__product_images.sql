-- A product's stored photo (B-30, research D8): the key of the object in the photo bucket. NULL is «no
-- photo», and the client draws the placeholder tile, as it does when the server has no object storage.
--
-- V7, the first free number when B-30 was rebased: V4 (the cart) and V6 (the product headline) were on
-- main by then, and Flyway refuses a version below the highest applied one — a V5 would have stopped
-- every database already at V6 from starting.

ALTER TABLE products ADD COLUMN image_key TEXT;
