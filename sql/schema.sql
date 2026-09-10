-- Run this in the Supabase SQL Editor (Project -> SQL Editor -> New query)
-- Creates and seeds the inventory and orders tables used by the
-- Order/Inventory Spring Boot app.

-- ─────────────────────────────
-- inventory
-- ─────────────────────────────
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id  varchar(20) primary key,
    name        varchar(100) not null,
    stock       integer not null check (stock >= 0)
);

insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0);

-- ─────────────────────────────
-- orders
-- ─────────────────────────────
create table orders (
    order_id    bigserial primary key,
    product_id  varchar(20) not null references inventory(product_id),
    quantity    integer not null check (quantity > 0),
    status      varchar(20) not null check (status in ('CONFIRMED', 'REJECTED')),
    reason      text,
    created_at  timestamptz not null default now()
);

create index idx_orders_product_id on orders(product_id);
create index idx_orders_created_at on orders(created_at);
