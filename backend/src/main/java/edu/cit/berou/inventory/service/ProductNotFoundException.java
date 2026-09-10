package edu.cit.berou.inventory.service;

/**
 * Thrown by InventoryService when a productId doesn't exist. Public because
 * the Order module needs to be able to catch it, even though it can't touch
 * the class that throws it.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}
