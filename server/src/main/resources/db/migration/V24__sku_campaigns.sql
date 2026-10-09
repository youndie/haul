-- A SKU's campaign (B-53): the campaign whose price `price_cents` is, `old_price_cents` being the SKU's
-- regular price. Until the campaign opens — at its `starts_at`, or at its `plus_early_access_at` for a
-- Haul Plus member — the SKU sells at that regular price (`CampaignPricing`). NULL is a SKU at its own
-- price, which is every SKU of a catalogue seeded before this migration: only a fresh seed carries the
-- links the seed gives, as V6 did with the headlines.
--
-- V24 because V22 and V23 are taken by B-29 and B-52 on parallel branches.

ALTER TABLE skus ADD COLUMN campaign_slug TEXT;
ALTER TABLE skus ADD CONSTRAINT fk_skus_campaign_slug__slug
    FOREIGN KEY (campaign_slug) REFERENCES campaigns (slug) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE skus ADD CONSTRAINT skus_campaign_price_is_a_markdown
    CHECK (campaign_slug IS NULL OR old_price_cents > price_cents);
