-- Customers (feature-identity, research §5 and D5). A customer is the shildik `sub` of whoever signed
-- in; the row is created by the first request their token makes, named by the token's `name` claim.
-- `plus` is the Haul Plus membership the cart's points and delivery read; its trial, renewal and
-- points ledger are feature-membership's (B-23).

CREATE TABLE customers (
    id         TEXT PRIMARY KEY,
    name       TEXT        NOT NULL,
    plus       BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- V4 left `carts.customer_id` without a table to reference; now it has one. A customer's cart goes
-- with the customer, as a guest's goes with the guest.
ALTER TABLE carts ADD CONSTRAINT fk_carts_customer_id__id FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE ON UPDATE RESTRICT;
