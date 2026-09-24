# Lab 3 Reflection

## 1. 503 after PO-100140 was already created

At 20:27:21, my request for BuyerRef `LAB3-TEST-001` and X-Request-Id `LAB3-REQ-001` received a 503 response from LegacySupply, but the supplier had already created PO-100140. My adapter retried the request using the same X-Request-Id instead of generating a new request ID. LegacySupply recognized the repeated request and safely replayed the existing purchase order instead of creating another one. This is why the integration record shows safe replays and zero duplicate orders.

## 2. PO-100205 returned undocumented StatusCode 90

PO-100205 for BuyerRef `RO-1` eventually returned StatusCode 90, which is not one of the documented LegacySupply statuses. I determined that my system should treat it as an unknown status because `SupplierTranslator` only maps 10, 20, 30, and 40, while any other code returns an empty result. `DeliveryTracker` then leaves the order unchanged, does not publish the delivery event, and does not restock inventory. Therefore, the 24 units for P300 were not added to inventory because the system cannot safely assume that StatusCode 90 means the order was delivered.

## 3. RO-2 during the outage

At 20:54:19, LegacySupply was unavailable and the reorder for BuyerRef `RO-2` could not be placed. The reorder remained stored in my local `supplier_orders` table with a PENDING status and its generated request ID, so the request was not lost. My scheduled pending-retry job periodically checked pending supplier orders and tried the request again. When LegacySupply became available, the retry successfully created PO-100208, and the integration record confirms that the previously blocked reference was eventually placed.
