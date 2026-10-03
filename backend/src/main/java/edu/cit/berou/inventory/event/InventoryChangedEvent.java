package edu.cit.berou.inventory.event;


public record InventoryChangedEvent(String productId, int newStock) {
}
