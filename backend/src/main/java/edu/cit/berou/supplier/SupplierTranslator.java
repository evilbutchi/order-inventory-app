package edu.cit.berou.supplier;

import java.util.Optional;


final class SupplierTranslator {

  
    static final int MAX_CASES = 99;

    private SupplierTranslator() {
    }

    
    static int casesFor(int unitsNeeded, int packSize) {
        if (unitsNeeded <= 0 || packSize <= 0) {
            throw new IllegalArgumentException("unitsNeeded and packSize must be positive");
        }
        int cases = (unitsNeeded + packSize - 1) / packSize;
        return Math.min(Math.max(cases, 1), MAX_CASES);
    }

    
    static Optional<SupplierOrderStatus> toStatus(int legacyStatusCode) {
        return switch (legacyStatusCode) {
            case 10 -> Optional.of(SupplierOrderStatus.ACCEPTED);
            case 20 -> Optional.of(SupplierOrderStatus.PICKING);
            case 30 -> Optional.of(SupplierOrderStatus.SHIPPED);
            case 40 -> Optional.of(SupplierOrderStatus.DELIVERED);
            default -> Optional.empty();
        };
    }
}
