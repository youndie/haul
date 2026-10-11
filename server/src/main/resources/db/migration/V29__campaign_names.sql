-- A campaign's name (B-107): what the home's hero calls the sale («Autumn mega sale · Oct 7 — 14») and the empty
-- cart's line names («… in the Autumn mega sale»). Both were written into the code, so a store whose first
-- campaign was another sale announced the autumn one anyway; now the campaign row says what it is called.
--
-- A campaign written before this migration is named after its slug, the hyphens spaced and the first letter
-- capitalised — which is the name the seed gives each of its campaigns (`SampleCatalog.campaigns`), and
-- `CampaignNameMigrationTest` holds the two together, so a stand migrated from V28 and a fresh seed say the same.

ALTER TABLE campaigns ADD COLUMN name TEXT;
UPDATE campaigns SET name = upper(left(replace(slug, '-', ' '), 1)) || substr(replace(slug, '-', ' '), 2);
ALTER TABLE campaigns ALTER COLUMN name SET NOT NULL;
