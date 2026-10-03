package edu.cit.berou.supplier;


public record SupplierOrderDelivered(Long supplierOrderId, String buyerRef, String productId, int units) {
}
