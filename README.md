# Order / Inventory In-Process Integration Demo

A single Spring Boot application with two in-process modules — **Order**
(`edu.cit.berou.shop`) and **Inventory** (`edu.cit.berou.inventory`) — sharing
a Supabase (Postgres) database, plus a React (Vite) frontend that talks to it
over REST.

## Project structure

```
order-inventory-app/
├── backend/     Spring Boot app (Java 17, Spring Boot 3.5.5)
├── frontend/    React + Vite app
├── sql/         schema.sql - table creation + seed data
└── README.md
```

## 1. Supabase setup

1. Go to [supabase.com](https://supabase.com), sign in, and click **New project**.
2. Pick an org, name the project (e.g. `order-inventory-demo`), set a database
   password (save it — you'll need it below), pick a region, and create the
   project.
3. Once it's provisioned, open **SQL Editor → New query**, paste in the
   contents of [`sql/schema.sql`](sql/schema.sql), and run it. This creates
   `inventory` (seeded with P100/P200/P300) and `orders`.
4. Click the green **Connect** button near the top of the project dashboard,
   select the **JDBC** tab, and choose the **Session pooler** connection
   (not "Direct connection" — that requires IPv6, which most local setups
   don't have). Copy the URI. It looks like:
   ```
   jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres
   ```
5. You'll plug that, your DB username (with the pooler this is
   `postgres.<project-ref>`, not just `postgres`), and the password from
   step 2 into environment variables — never into `application.properties`
   directly.

## 2. Backend setup

Requires Java 17 and Maven.

```bash
cd backend
export SUPABASE_DB_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres"
export SUPABASE_DB_USERNAME="postgres.<project-ref>"
export SUPABASE_DB_PASSWORD="<your-db-password>"
export CORS_ALLOWED_ORIGIN="http://localhost:5173"   # optional, this is the default

./mvnw spring-boot:run
```

(On Windows PowerShell, use `$env:SUPABASE_DB_URL = "..."` etc., or set them
in your IDE's run configuration.) See `backend/.env.example` for the full list
of variables. The API comes up on `http://localhost:8080`.

## 3. Frontend setup

Requires Node.js 18+.

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173`. If your backend runs somewhere other than
`localhost:8080`, copy `frontend/.env.example` to `.env` and set
`VITE_API_BASE_URL`.

## 4. API

**POST `/api/orders`**

```json
// request
{ "productId": "P100", "quantity": 5 }

// response (200)
{ "status": "CONFIRMED", "reason": null, "inventory": { "productId": "P100", "name": "Wireless Mouse", "stock": 20 } }
```

If `quantity` exceeds available stock, `status` is `"REJECTED"` and `reason`
explains why; `inventory` reflects stock as of the rejected attempt.

**GET `/api/inventory`** — lists all products (used by the frontend dropdown).

## 5. Network tab evidence

_Screenshots captured while testing both paths end-to-end:_

**Confirmed order** (e.g. P100, quantity within stock):

`docs/network-confirmed.png`

![Confirmed order network evidence](docs/network-confirmed.png)

**Rejected order** (e.g. P300, quantity 0 stock, or quantity greater than
available stock):

`docs/network-rejected.png`

![Rejected order network evidence](docs/network-rejected.png)

> To capture these: open the browser DevTools → **Network** tab, filter by
> `orders`, submit an order from the React form, then click the `orders`
> request and screenshot the **Payload** and **Response** panels for both a
> confirmed and a rejected attempt.

## 6. Reflection

**1. In-process vs. microservices over a network.**
Calling `InventoryService` in-process means the Order module gets a handful
of things for free: a single method call is synchronous and either returns
or throws, so there's no need to handle partial failure, timeouts, or
retries — if `reserve()` returns, I know exactly what happened. Both modules
also share one transaction, so an order write and a stock decrement commit
or roll back together; there's no risk of the stock being decremented while
the order write fails. There's no serialization, no network latency, and no
separate deployment or versioning to coordinate — one JAR, one process, one
`git push`. If I split Inventory out into its own service reachable over
HTTP, I'd have to add back all of that: a client (REST or gRPC) with
timeouts and retries, error handling for the service being down or slow,
and some way to keep the "order + stock update" consistent without a shared
database transaction — likely a saga/compensation pattern (e.g. reserve
first, confirm or release afterward) or an outbox/event-based approach,
plus monitoring, service discovery, and independent versioning of the
Inventory API contract.

**2. Why package-private `InventoryServiceImpl` matters.**
Making the implementation package-private means the compiler enforces the
module boundary, not just convention. The Order module can only see the
`InventoryService` interface (plus the DTOs that interface exposes) — it
has no way to import `InventoryServiceImpl`, call implementation-specific
methods, or reach into `InventoryRepository`/`InventoryItem` directly. If
`InventoryServiceImpl` were `public`, nothing would stop `OrderService` from
depending on the concrete class, bypassing the interface, or reaching past
it into the repository layer. That would tie Order's compiled code to
Inventory's internals, so changing how Inventory is implemented (say,
swapping the locking strategy or the persistence layer) could break Order
even though the public contract never changed — exactly the coupling a
module boundary is supposed to prevent.

**3. When to extract Inventory into its own microservice.**
It's worth splitting out once Inventory needs to scale, deploy, or evolve
independently of Order — for example, if Inventory needs a much higher
read throughput (real-time stock lookups from many services), gets updated
by other systems too (a warehouse or POS integration), or needs a different
on-call/release cadence than Order. Doing it would mean: replacing the
constructor-injected `InventoryService` call with an HTTP (or messaging)
client behind the same interface shape, moving `inventory`/`InventoryItem`
into Inventory's own database, adding resilience (timeouts, retries,
circuit breaking), and redesigning `OrderService.placeOrder` so that a
reservation and an order write are no longer one local transaction — most
likely reserve-then-confirm with compensation if the order write later
fails.

---

## Submission checklist

- [ ] Push this repo to GitHub (confirm `.env` files are **not** committed —
      check with `git status` / `.gitignore`)
- [ ] Add `docs/network-confirmed.png` and `docs/network-rejected.png`
- [ ] Fill in your actual GitHub repo link when submitting
