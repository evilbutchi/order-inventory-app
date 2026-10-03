package edu.cit.berou.shop.dto;

import edu.cit.berou.inventory.dto.InventorySnapshot;

import java.util.List;


public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        List<OrderItemOutcome> items,
        List<InventorySnapshot> inventory
) {
}
