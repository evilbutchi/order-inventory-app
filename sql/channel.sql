
create table if not exists channel_orders (
    id                          bigserial primary key,
    tiangge_order_id            varchar(40)  not null unique,
    order_id                    bigint,
    status                      varchar(20)  not null
                                check (status in ('ACCEPTED','REJECTED','BACKORDERED','CANCELLED')),
    pending_resolution          varchar(20)
                                check (pending_resolution in ('ACCEPTED','CANCELLED')),
    decision_sent_at            timestamptz,
    cancellation_confirmed_at   timestamptz,
    resolved_at                 timestamptz,
    created_at                  timestamptz  not null default now()
);

create index if not exists idx_channel_orders_status on channel_orders(status);

create table if not exists channel_order_items (
    id                 bigserial primary key,
    channel_order_id   bigint      not null references channel_orders(id) on delete cascade,
    product_id         varchar(20) not null,
    quantity           integer     not null check (quantity > 0)
);

create index if not exists idx_channel_order_items_channel_order on channel_order_items(channel_order_id);

create table if not exists channel_events (
    event_id      varchar(80) primary key,
    seq           bigint      not null,
    type          varchar(30) not null,
    processed_at  timestamptz not null default now()
);

create index if not exists idx_channel_events_seq on channel_events(seq);

create table if not exists channel_stock_updates (
    id          bigserial primary key,
    product_id  varchar(20) not null,
    stock       integer not null check (stock >= 0),
    created_at  timestamptz not null default now()
);
