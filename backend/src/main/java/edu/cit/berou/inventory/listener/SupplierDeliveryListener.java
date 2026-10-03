package edu.cit.berou.inventory.listener;

import edu.cit.berou.inventory.service.InventoryService;
import edu.cit.berou.supplier.SupplierOrderDelivered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


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
