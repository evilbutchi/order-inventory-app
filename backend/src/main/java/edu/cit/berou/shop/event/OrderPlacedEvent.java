package edu.cit.berou.shop.event;


public record OrderPlacedEvent(Long orderId, int itemCount) {
}
