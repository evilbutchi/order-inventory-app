package edu.cit.berou.shop.dto;

import java.time.Instant;
import java.util.List;

public record OrderSummary(
        Long orderId,
        String status,
        String reason,
        Instant createdAt,
        List<OrderItemView> items
) {
}
