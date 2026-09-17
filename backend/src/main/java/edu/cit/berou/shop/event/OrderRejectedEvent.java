package edu.cit.berou.shop.event;

public record OrderRejectedEvent(Long orderId, String reason) {
}
