-- A generated product's description names its leaf category inside the sentence, in lower case («Travel headphones
-- by Sony.»), and lowered an initialism with it: «Travel tvs by Northwind.» (B-152). The seed now keeps a word with a
-- capital past its first letter as it is written (`CatalogSeed.inSentence`); this writes the same into a catalog
-- seeded before. «TVs» is the one such leaf the seed has, and only the generated sentence is touched — an adjective,
-- the leaf, «by» and the brand — so a description written another way is left as it is. `LeafNameMigrationTest`
-- holds the two together: a stand migrated from V29 and a fresh seed describe the same products the same way.

UPDATE products
SET description = regexp_replace(description, '^([A-Za-z]+) tvs by ', '\1 TVs by ')
WHERE category_slug = 'tvs' AND description ~ '^[A-Za-z]+ tvs by [^.]+\.$';
