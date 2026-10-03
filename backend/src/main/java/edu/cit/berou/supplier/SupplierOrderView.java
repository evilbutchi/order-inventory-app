package edu.cit.berou.supplier;

import java.time.Instant;


public record SupplierOrderView(Long id,
                                String productId,
                                String buyerRef,
                                String requestId,
                                String poNumber,
                                int cases,
                                int units,
                                SupplierOrderStatus status,
                                Instant createdAt,
                                Instant updatedAt) {
}
