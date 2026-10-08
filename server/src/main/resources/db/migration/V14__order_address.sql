-- An order keeps the address it was placed to (B-40). The address form edits the address delivered to in
-- place, so `address_id` alone would make an order placed to «4F» read «5B» once the shopper changed it;
-- the order is the quote at placement, copied (V10), and its address is copied with it — the form's
-- fields as JSON (`AddressEntry`), an absent field empty. `address_id` stays: which saved address it was.
ALTER TABLE orders ADD COLUMN address JSONB;

-- Orders placed before this migration: their address was never edited — every save added a row until
-- now — so the row they name still holds what they were placed to.
UPDATE orders
SET address = jsonb_build_object(
    'street', a.street,
    'apt', COALESCE(a.apt, ''),
    'city', a.city,
    'zip', a.zip,
    'doorCode', COALESCE(a.door_code, ''),
    'courierNote', COALESCE(a.courier_note, '')
)
FROM addresses a
WHERE a.id = orders.address_id;
