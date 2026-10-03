package edu.cit.berou.supplier;

public record ReorderResult(Long orderId,
                            String productId,
                            String buyerRef,
                            int units,
                            SupplierOrderStatus status,
                            String poNumber) {
}
