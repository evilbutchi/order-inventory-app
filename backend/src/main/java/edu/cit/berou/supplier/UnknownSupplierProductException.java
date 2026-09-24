package edu.cit.berou.supplier;

/** Thrown when a product has no mapping to a supplier item. */
public class UnknownSupplierProductException extends RuntimeException {

    private final String productId;

    public UnknownSupplierProductException(String productId) {
        super("No supplier mapping for product " + productId);
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}
