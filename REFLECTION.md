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

My application's available-stock figure came from the P100 inventory row in Supabase. `OrderService.placeOrder` reads that row under a database lock using `InventoryService.getItemForUpdate`, checks the requested quantity against the row's current stock, and then reserves the stock. It does not use Tiangge's last published value as the authority for accepting a local reservation. Therefore, accepting two units means the Supabase row had at least two units available when this order was processed. Tiangge's published stock and its own estimate were both zero, so the two systems disagreed. The supplied marketplace observation does not identify which earlier change left the Supabase quantity higher than Tiangge's ledger; I cannot reliably attribute the difference to a particular delivery, cancellation, or stock update without the corresponding timestamped records. This order shows why the app's local stock and the marketplace's view must stay synchronized before an acceptance can be considered safe.

2. Event evt_59139f2c499a4b98 (order TG-KZXVUL) reached your application twice, as seq 856 and seq 857, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.

`FeedEventProcessor.process` checks the event ID in `channel_events` before doing any order work, and claims a new event by inserting and flushing its row inside the same transaction as processing the order:

```java
if (eventRepository.existsById(event.eventId())) {
    return;
}
eventRepository.saveAndFlush(
        new ChannelEvent(event.eventId(), event.seq(), event.type(), Instant.now()));
```

The `event_id` column in `channel_events` is the primary key. The first delivery stored `evt_59139f2c499a4b98` with seq 856; when the same event ID arrived again as seq 857, the existence check found the stored row and skipped order processing. If two copies race, the primary-key constraint prevents both from claiming the event. The order operation, channel-order record, and event claim commit together, so a failure before commit rolls them back together. A restart between deliveries is harmless because the event row is in Supabase, not memory: the restarted application will find the same event ID and skip it. The feed cursor is also recovered from persisted event sequences, so it may receive seq 857 again after restart, but the duplicate event ID still prevents a second order.

3. Order TG-J3PCDQ was backordered at 21:50:35 and accepted at 21:55:10, after PO-103008 was delivered at 21:54:45. Trace how the delivery reached your Inventory and what then resumed the backordered order.

`DeliveryTracker` periodically queries LegacySupply for open purchase-order statuses. When it observed PO-103008's status change to DELIVERED, it saved that status and published a `SupplierOrderDelivered` event. `SupplierDeliveryListener` handled the event by calling `InventoryService.restock` for the supplier order's product and units; restocking updated the Supabase inventory row and emitted an `InventoryChangedEvent` for Tiangge stock publication. After the delivery transaction committed, `BackorderResolver` looked up backordered Tiangge orders for that product. It retried TG-J3PCDQ through `OrderService.placeOrder`, which reserved the now-available inventory, recorded the new local order, and set the channel order's pending resolution to ACCEPTED. It then published `ResolutionReady`; `DecisionSender` sent the resolution to Tiangge and persisted its confirmation. This is the delivery event, rather than a manual retry, that resumed the backordered order.
