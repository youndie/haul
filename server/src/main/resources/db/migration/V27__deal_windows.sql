-- A deal's window (B-57): a deal is a price of its SKU from `starts_at` up to, not including, `ends_at`,
-- and while it is live that price is what every surface draws and the cart charges (`CampaignPricing`).
-- V1 gave a deal only its end, which nothing read; with only an end a deal would be live from whenever it
-- was written, so a deal of tomorrow would already sell today.
--
-- A deal written before this migration is a deal of the day (research D7: deals of the day end at local
-- midnight), so its window opens a day before its end. A fresh database has no deals here yet; the seed
-- writes the same window after (`CatalogSeed.DEALS_START`), and `DealWindowMigrationTest` holds the two
-- together.

ALTER TABLE deals ADD COLUMN starts_at TIMESTAMPTZ;
UPDATE deals SET starts_at = ends_at - INTERVAL '1 day';
ALTER TABLE deals ALTER COLUMN starts_at SET NOT NULL;
ALTER TABLE deals ADD CONSTRAINT deals_window_check CHECK (starts_at < ends_at);
