# Lab 3 Reflection

## 1. 503 after PO-100140 was already created

At 20:27:21, my request for BuyerRef `LAB3-TEST-001` and X-Request-Id `LAB3-REQ-001` received a 503 response from LegacySupply, but the supplier had already created PO-100140. My adapter retried the request using the same X-Request-Id instead of generating a new request ID. LegacySupply recognized the repeated request and safely replayed the existing purchase order instead of creating another one. This is why the integration record shows safe replays and zero duplicate orders.

## 2. PO-100205 returned undocumented StatusCode 90

PO-100205 for BuyerRef `RO-1` eventually returned StatusCode 90, which is not one of the documented LegacySupply statuses. I determined that my system should treat it as an unknown status because `SupplierTranslator` only maps 10, 20, 30, and 40, while any other code returns an empty result. `DeliveryTracker` then leaves the order unchanged, does not publish the delivery event, and does not restock inventory. Therefore, the 24 units for P300 were not added to inventory because the system cannot safely assume that StatusCode 90 means the order was delivered.

## 3. RO-2 during the outage

At 20:54:19, LegacySupply was unavailable and the reorder for BuyerRef `RO-2` could not be placed. The reorder remained stored in my local `supplier_orders` table with a PENDING status and its generated request ID, so the request was not lost. My scheduled pending-retry job periodically checked pending supplier orders and tried the request again. When LegacySupply became available, the retry successfully created PO-100208, and the integration record confirms that the previously blocked reference was eventually placed.




Tiangge - Reflection

### Marketplace reflection questions

1. Tiangge order TG-GFEUGW (2 x P100) was accepted at 21:49:20. At that moment your last published stock for P100 was 0, and the stock Tiangge worked out from your own decisions, cancellations and deliveries was 0. Where did your application's stock figure come from, and why did it disagree?

At 21:49:20, the marketplace record showed TG-GFEUGW accepted for two P100 units even though both Tiangge's last published stock and its own calculated stock were zero. The application gets its available-stock figure from the P100 row in Supabase: `OrderService.placeOrder` calls `InventoryService.getItemForUpdate`, checks availability under a database row lock, and then reserves the requested quantity. It uses that local database value, not Tiangge's last published stock, to decide whether to accept the order. The supplied record establishes that the local and marketplace figures disagreed, but the available local logs do not include the timestamped P100 database value or the preceding stock events needed to prove which event caused the discrepancy.

2. Event evt_59139f2c499a4b98 (order TG-KZXVUL) reached your application twice, as seq 856 and seq 857, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.

`FeedEventProcessor.process` checks the event ID in `channel_events` before doing any order work, and claims a new event by inserting and flushing its row inside the same transaction as processing the order:

```java
if (eventRepository.existsById(event.eventId())) {
    return;
}
eventRepository.saveAndFlush(
        new ChannelEvent(event.eventId(), event.seq(), event.type(), Instant.now()));
```

The marketplace record says event `evt_59139f2c499a4b98` was first received at seq 856 and redelivered at seq 857, but processed only once. `channel_events.event_id` is the primary key, and each row stores the event ID, sequence, type, and processing time; `FeedEventProcessor.process` checks that key and inserts the claim before handling the order. Thus the second delivery finds the existing event ID and skips the order, while a concurrent duplicate is also stopped by the primary-key constraint. Because the event claim and order processing share a transaction, a restart after commit finds the row in Supabase and skips the replay; a crash before commit rolls back both the claim and the order work so the event can be retried safely.

3. Order TG-J3PCDQ was backordered at 21:50:35 and accepted at 21:55:10, after PO-103008 was delivered at 21:54:45. Trace how the delivery reached your Inventory and what then resumed the backordered order.

The marketplace record places PO-103008's delivery at 21:54:45, after TG-J3PCDQ was backordered at 21:50:35. `DeliveryTracker` polls open LegacySupply purchase orders and, when it detects the DELIVERED status, saves the transition and publishes `SupplierOrderDelivered`. `SupplierDeliveryListener` handles that event by calling `InventoryService.restock` with the supplier order's product and units, which updates Supabase inventory and emits an `InventoryChangedEvent` for Tiangge stock publication. After the delivery transaction commits, `BackorderResolver` finds backordered channel orders for that product and retries TG-J3PCDQ through `OrderService.placeOrder`, reserving the newly available stock. It records the accepted pending resolution and publishes `ResolutionReady`, which `DecisionSender` sends to Tiangge; the marketplace record shows the order accepted at 21:55:10.
