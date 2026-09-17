-- Run this in the Supabase SQL Editor (Project -> SQL Editor -> New query)
-- Recreates the full schema from scratch, including seed data, for the
-- Order/Inventory Spring Boot app (Lab 2: multi-item orders, cancellation,
-- notifications, low-stock events).

-- Drop in dependency order (children before parents).
drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

-- ─────────────────────────────
-- inventory
-- ─────────────────────────────
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
-- product_id/quantity moved out to order_items now that an order can hold
-- multiple line items. An order's own row only tracks its overall outcome.
create table orders (
    order_id    bigserial primary key,
    status      varchar(20) not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason      text,
    created_at  timestamptz not null default now()
);

create index idx_orders_created_at on orders(created_at);

-- ─────────────────────────────
-- order_items
-- ─────────────────────────────
create table order_items (
    order_item_id  bigserial primary key,
    order_id       bigint not null references orders(order_id),
    product_id     varchar(20) not null references inventory(product_id),
    quantity       integer not null check (quantity > 0)
);

create index idx_order_items_order_id on order_items(order_id);
create index idx_order_items_product_id on order_items(product_id);

-- ─────────────────────────────
-- notifications
-- ─────────────────────────────
create table notifications (
    notification_id  bigserial primary key,
    message           text not null,
    created_at        timestamptz not null default now()
);

create index idx_notifications_created_at on notifications(created_at);
