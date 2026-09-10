package edu.cit.berou.shop.dto;

import edu.cit.berou.inventory.dto.InventorySnapshot;

/**
 * { status, reason, inventory } as required by the spec. "inventory" reuses
 * InventorySnapshot directly - that record is part of InventoryService's
 * public contract, so the Order module is allowed to depend on it.
 */
public record OrderResponse(String status, String reason, InventorySnapshot inventory) {
}
