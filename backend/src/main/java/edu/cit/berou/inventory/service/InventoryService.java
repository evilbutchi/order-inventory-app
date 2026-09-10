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
     *
     * @param productId the product to look up
     * @return a snapshot of product id, name, and current stock
     * @throws edu.cit.berou.inventory.service.ProductNotFoundException if the product doesn't exist
     */
    InventorySnapshot getItem(String productId);

    /**
     * Attempts to reserve (decrement) stock for a product as part of placing
     * an order. Rejects the reservation if the requested quantity exceeds
     * available stock.
     *
     * @param productId the product to reserve stock for
     * @param quantity  the quantity requested
     * @return the resulting inventory snapshot after the reservation attempt,
     *         plus whether it succeeded
     */
    ReservationResult reserve(String productId, int quantity);
}
