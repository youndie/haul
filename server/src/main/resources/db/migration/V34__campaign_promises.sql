-- The seed's campaigns promise what the store does (B-172). A shopper found the home page contradicting the store:
--
-- - the sale's hero said «Up to −70%» and «1.2 million items marked down across 32 categories» beside a deals page of
--   832 items at most −49 % off. Its copy now names its figures, `{deepest}`, `{items}` and `{categories}`, which the
--   home's hero and the empty cart's line fill from what is on sale for the viewer (`SaleFigures`);
-- - Tech week's «Laptops from $399» opened the deals page, and the store sells no laptops. It reads «From $399» under
--   «Tech week · Computers» (the word «Computers» is wider than the banner's title beside its picture) and opens the
--   computers from $399, cheapest first: the address is the campaign's new `link`, which a banner opens (NULL opens
--   the deals page, as every banner did since B-59);
-- - the free-delivery weekend's «Free delivery on everything» (over «600K items from local sellers») was a rule the
--   cart never applied: delivery is free from $35, or for Haul Plus (research D7). Its campaign is gone; the banner in
--   its place is Plus's own (`PlusOffer.banner`), which is not a campaign.
--
-- V34, not V33: V33 belongs to B-164 (`orders_placed_at`), and Flyway refuses a version below the highest applied one.
-- Only the seed's own rows are touched, by their slug and their old copy, so a store's own campaign is left as it is.
-- `CampaignPromisesMigrationTest` holds the two together: a stand migrated from before and a fresh seed have the same
-- campaigns.

ALTER TABLE campaigns ADD COLUMN link TEXT;

UPDATE campaigns
SET title = 'Up to {deepest}', subtitle = '{items} marked down across {categories}'
WHERE slug = 'autumn-mega-sale'
  AND title = 'Up to −70%'
  AND subtitle = '1.2 million items marked down across 32 categories';

UPDATE campaigns
SET title = 'From $399', subtitle = 'Tech week · Computers', link = '/c/computers?price_min=399&sort=price-asc'
WHERE slug = 'tech-week' AND title = 'Laptops from $399' AND subtitle = 'Tech week';

DELETE FROM campaigns
WHERE slug = 'free-delivery-weekend'
  AND title = 'Free delivery on everything'
  AND NOT EXISTS (SELECT 1 FROM skus WHERE skus.campaign_slug = campaigns.slug);
