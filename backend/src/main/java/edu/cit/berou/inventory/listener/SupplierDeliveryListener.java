package edu.cit.berou.inventory.listener;

import edu.cit.berou.inventory.service.InventoryService;
import edu.cit.berou.supplier.SupplierOrderDelivered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Restocks when a supplier order arrives. Inventory only knows the event
 * (product id + units, our own terms) - it never calls the supplier module,
 * and nothing about LegacySupply appears here.
 *
 * Runs synchronously inside the publisher's transaction, so "order marked
 * DELIVERED" and "stock increased" commit together or not at all.
 */
@Component
class SupplierDeliveryListener {

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onSupplierOrderDelivered(SupplierOrderDelivered event) {
        inventoryService.restock(event.productId(), event.units());
    }
}
