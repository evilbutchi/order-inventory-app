package edu.cit.berou.supplier;

/** What the gateway tells callers about a reorder. Our terms only. */
public record ReorderResult(Long orderId,
                            String productId,
                            String buyerRef,
                            int units,
                            SupplierOrderStatus status,
                            String poNumber) {
}
