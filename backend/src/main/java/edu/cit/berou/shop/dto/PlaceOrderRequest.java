package edu.cit.berou.shop.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PlaceOrderRequest(
        @NotBlank(message = "productId is required") String productId,
        @Min(value = 1, message = "quantity must be at least 1") int quantity
) {
}
