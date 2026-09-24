# INTEGRATION.md — LegacySupply

> This document records the actual LegacySupply integration behavior observed during Lab 3.

## 1. Product mapping

Mapping lives in the supplier module's own table `supplier_item_map`, not in Inventory.

| Our product ID | Our name | SupplierSku | PackSize |
|---|---|---|---:|
| P100 | Wireless Mouse | ZQA-9900 | 6 |
| P200 | Mechanical Keyboard | ZQA-9983 | 10 |
| P300 | USB-C Hub | ZQA-3765 | 24 |

These values came from `GET /catalog` using my LegacySupply session.

## 2. Sessions

- Sign in: `POST /auth/token` with an `AuthRequest` containing `ClientId` and `ApiKey`, which returns a `SessionToken`.
- Every authenticated call sends the token in the `X-LS-Session` header.
- I observed that a session remained valid at approximately 2 minutes after sign-in. A later request at approximately 3 minutes 22 seconds received a session-related failure, but that request returned HTTP 503, so I could not conclusively determine the exact expiration time from that timing test alone.
- The integration `/verify` record later provided stronger evidence of expiration: an authenticated request received `E-AUTH-07` with HTTP 401, after which the adapter signed in again successfully.
- Therefore, the exact fixed lifetime was not conclusively measured, and I do not assume that the lifetime resets simply because the session is being used.
- In my adapter (`SessionManager` + `LegacySupplyClient`), the token is created lazily on first use, kept in memory, and dropped when an authenticated call returns 401. The same call is then replayed with a fresh session. Nobody pastes tokens manually. The optional `supplier.session-max-age-seconds` setting can also renew the session proactively.

## 3. Error codes I actually received

| Code | HTTP | What the manual says | What actually caused it for me |
|---|---:|---|---|
| E-AUTH-07 | 401 | Session not valid | An authenticated request was made with an expired LegacySupply session. The `/verify` record shows the adapter then signed in again successfully. |
| E-QTY-11 | 422 | Quantity invalid | I submitted a purchase order with `Qty = 0`. LegacySupply rejected the quantity because supplier quantity must be a positive whole number. |
| E-SYS-50 | 503 | Processing error | A valid purchase-order request for BuyerRef `LAB3-TEST-001` received a 503. The important result was that LegacySupply had already created PO-100140, so the adapter retried using the same `X-Request-Id` and received the existing order rather than creating a duplicate. |

These are the error codes I personally observed during the manual/API testing and integration verification. I did not include error codes that I did not actually receive.

How my adapter reacts:

| Situation | Reaction |
|---|---|
| Timeout, connection refused, or 5xx | Retry with backoff, up to 3 attempts per call; if the order cannot be submitted, leave it `PENDING`. |
| 401 on a normal authenticated call | Drop the session, sign in again, and replay the call using the same request ID. |
| 401 on sign-in (`E-AUTH-01`) | Configuration problem; do not create a retry storm. |
| 429 | The adapter observes the quota protection and avoids repeatedly hammering the supplier. |
| Other 4xx | The same request is not blindly retried because changing the request is normally required to correct the problem. |

## 4. Qty and Uom

`Qty` is how many supplier units I order, where the supplier's unit of measure for these products is `CS` (case). `PackSize` tells me how many individual items are contained in one case.

For example, P200 (Mechanical Keyboard) has a `PackSize` of 10. If my application needs 6 units, it calculates:

`6 / 10 = 0.6`

The adapter rounds up to `Qty = 1` case, so LegacySupply receives 1 case and the application records `units = 10`. Therefore, 10 keyboards are expected to arrive rather than only 6.

Another example from my test was P300. I requested 20 units, but its PackSize is 24. The adapter therefore created 1 case, resulting in 24 units:

`20 / 24 = 0.833... -> 1 case -> 1 x 24 = 24 units`

The `supplier_orders` row stored `cases = 1` and `units = 24`. On delivery, Inventory is restocked by 24 units, not 20 and not 1.

## 5. Design decisions

- **Never duplicate a PO.** The `supplier_orders` row is committed before any HTTP call and already contains `request_id` (a random UUID) and `cases`. That same `X-Request-Id` and the same Qty are sent on every attempt, retry, scheduled re-send, and restart. LegacySupply does not process a repeated request ID twice.
- **Never lose a reorder.** If LegacySupply is down, the row stays `PENDING`; the `@Scheduled` job (`ReorderSubmitter.retryPending`, every 15 seconds) sends it later. It stops at the first failure so it does not hammer a service that is down.
- **One open reorder per product** (`supplier.reorder.dedupe-open-orders`): low-stock events can fire on every sale below the threshold, so this keeps one reorder in flight instead of creating a stream of duplicate reorders.
- **Reorder runs after the customer's order commits** (`@TransactionalEventListener(AFTER_COMMIT)`), in its own transaction. A rolled-back customer order never causes a reorder, and a supplier problem cannot fail the customer's order.
- **Boundary.** Only `SupplierGateway`, `ReorderResult`, `SupplierOrderStatus`, `SupplierOrderDelivered`, `UnknownSupplierProductException`, and the small JSON DTOs are public. XML, HTTP client, session, translator, and scheduled jobs are package-private. Order and Inventory do not import LegacySupply concepts. Inventory only listens to the application's own `SupplierOrderDelivered` event.

## 6. Status mapping and unexpected statuses

| LegacySupply StatusCode | My enum |
|---|---|
| 10 Accepted | `ACCEPTED` |
| 20 Picking | `PICKING` |
| 30 Shipped | `SHIPPED` |
| 40 Delivered | `DELIVERED` |
| Unknown / unexpected code | No status change |

When status 40 (`Delivered`) is observed, the supplier order becomes `DELIVERED`, a `SupplierOrderDelivered` event is published, and Inventory restocks the recorded number of units.

I also encountered LegacySupply StatusCode `90` on PO-100205 (BuyerRef `RO-1`). This code is not documented in the LegacySupply manual. My `SupplierTranslator` only maps 10, 20, 30, and 40; any other code is treated as unknown.

For an unexpected status code, the order's status is left unchanged, a warning is logged with the code and PO number, and nothing is restocked. This prevents the system from incorrectly adding inventory when it cannot prove that the supplier delivered the order. The order remains eligible for later checking.

A status that would move an order backwards is also ignored and logged. Delivered orders leave the polling set. The DELIVERED transition and delivery event are processed transactionally so a delivery cannot cause repeated restocking.

## 7. Staying within the quota

- Tracking runs every 30 seconds and checks at most 10 open supplier orders per cycle.
- Orders are checked least-recently-updated first.
- A cycle stops at the first supplier problem instead of continuing to hammer the service.
- The adapter does not repeatedly sign in for every request; it reuses a valid session and renews it when the supplier rejects the session.
- The `/verify` record showed 58 status checks with 0 rate-limit (`429`) responses.
