package edu.cit.berou.shop.dto;

import edu.cit.berou.inventory.dto.InventorySnapshot;

import java.util.List;

/**
 * { status, reason, items, inventory } as required by the spec. "items"
 * reports the per-line-item outcome; "inventory" is the updated snapshot
 * of every product touched by this order (empty on rejection since
 * nothing was reserved).
 */
public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        List<OrderItemOutcome> items,
        List<InventorySnapshot> inventory
) {
}
