package edu.cit.berou.inventory.dto;

/**
 * Read-only snapshot of a product's inventory state. This (not the JPA
 * entity) is what crosses the module boundary into the Order module, so
 * Order never touches Inventory's persistence types directly.
 */
public record InventorySnapshot(String productId, String name, int stock) {
}
