package edu.cit.berou.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(
        @NotEmpty(message = "items must not be empty") @Valid List<OrderItemRequest> items
) {
}
