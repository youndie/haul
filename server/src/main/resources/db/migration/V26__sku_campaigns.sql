-- A SKU's campaign (B-53): the campaign whose price `price_cents` is, `old_price_cents` being the SKU's
-- regular price. Until the campaign opens — at its `starts_at`, or at its `plus_early_access_at` for a
-- Haul Plus member — the SKU sells at that regular price (`CampaignPricing`). NULL is a SKU at its own
-- price, which is every SKU of a catalogue seeded before this migration: only a fresh seed carries the
-- links the seed gives, as V6 did with the headlines.
--
-- V26, not V24: V22, V23 and V25 belong to B-29, B-52 and B-45, and Flyway refuses a version below the
-- highest applied one, so a V24 landing after B-45's V25 would stop every database at V25 from starting.
-- V24 stays unused.

ALTER TABLE skus ADD COLUMN campaign_slug TEXT;
ALTER TABLE skus ADD CONSTRAINT fk_skus_campaign_slug__slug
    FOREIGN KEY (campaign_slug) REFERENCES campaigns (slug) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE skus ADD CONSTRAINT skus_campaign_price_is_a_markdown
    CHECK (campaign_slug IS NULL OR old_price_cents > price_cents);
