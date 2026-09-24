package edu.cit.berou.supplier;

/**
 * Domain event: a supplier order arrived. Published by the supplier module;
 * Inventory listens and restocks. Carries only our own terms - "units" is
 * the number of individual items that arrived, never cases.
 */
public record SupplierOrderDelivered(Long supplierOrderId, String buyerRef, String productId, int units) {
}
