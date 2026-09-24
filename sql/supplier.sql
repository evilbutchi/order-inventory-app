-- Lab 3: supplier module tables.
-- Run in the Supabase SQL Editor AFTER sql/schema.sql.
--
-- Deliberately separate from schema.sql and deliberately WITHOUT foreign keys
-- to `inventory`:
--  * the supplier module owns these tables; it must not depend on Inventory's schema, and
--  * re-running schema.sql (which drops and recreates inventory) must not wipe your
--    supplier_orders history. LegacySupply remembers every BuyerRef / X-Request-Id you
--    ever sent, so restarting the numbering at RO-1 would collide with old orders.
-- Safe to re-run: nothing here drops anything.

create table if not exists supplier_item_map (
    product_id    varchar(20) primary key,
    supplier_sku  varchar(40) not null,
    pack_size     integer     not null check (pack_size > 0)
);

create table if not exists supplier_orders (
    id          bigserial primary key,
    product_id  varchar(20)  not null,
    buyer_ref   varchar(40)  not null unique,
    request_id  varchar(80)  not null unique,
    po_number   varchar(40),
    cases       integer      not null check (cases between 1 and 99),
    units       integer      not null check (units > 0),
    status      varchar(20)  not null
                check (status in ('PENDING','ACCEPTED','PICKING','SHIPPED','DELIVERED','FAILED')),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create index if not exists idx_supplier_orders_status on supplier_orders(status);
create index if not exists idx_supplier_orders_product on supplier_orders(product_id);

-- ---------------------------------------------------------------------
-- YOUR mapping. Every partner has its own catalog, so copy the SupplierSku and
-- PackSize values from YOUR GET /catalog response. The lines below are
-- placeholders - replace them, then run this block.
-- ---------------------------------------------------------------------
-- insert into supplier_item_map (product_id, supplier_sku, pack_size) values
--     ('P100', 'XXX-0000', 12),
--     ('P200', 'XXX-0000', 6),
--     ('P300', 'XXX-0000', 10)
-- on conflict (product_id) do update
--     set supplier_sku = excluded.supplier_sku, pack_size = excluded.pack_size;
