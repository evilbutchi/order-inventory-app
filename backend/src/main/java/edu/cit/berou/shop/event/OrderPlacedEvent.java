package edu.cit.berou.shop.event;

/**
 * Published by OrderService after an order is confirmed and saved.
 * Notification listens for this - OrderService never calls Notification
 * directly.
 */
public record OrderPlacedEvent(Long orderId, int itemCount) {
}
