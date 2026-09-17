package edu.cit.berou.inventory.event;

public record LowStockEvent(String productId, String productName, int currentStock, int threshold) {
}
