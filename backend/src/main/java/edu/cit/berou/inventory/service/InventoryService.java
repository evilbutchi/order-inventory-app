package edu.cit.berou.inventory.service;

import edu.cit.berou.inventory.dto.InventorySnapshot;

/**
 * Public contract for the Inventory module. This is the ONLY inventory type
 * the Order module (edu.cit.berou.shop) is allowed to depend on. The
 * concrete implementation is package-private so it cannot be new'd up or
 * referenced by type from outside edu.cit.berou.inventory.service - callers
 * must go through Spring's constructor injection against this interface.
 */
public interface InventoryService {

    /**
     * Reads the current state of a product.
     */
    InventorySnapshot getItem(String productId);

    /**
     * Attempts to reserve (decrement) stock for a product as part of placing
     * an order. Rejects the reservation if the requested quantity exceeds
     * available stock. May also publish a LowStockEvent if the remaining
     * stock drops below the configured threshold.
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Returns quantity to stock, e.g. when an order is cancelled.
     */
    InventorySnapshot restock(String productId, int quantity);
}
