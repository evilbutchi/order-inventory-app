package edu.cit.berou.supplier;

import java.util.Optional;

/**
 * The translation rules between our world and LegacySupply's: units <-> cases
 * and LegacySupply status codes -> our enum. Pure functions, easy to test.
 */
final class SupplierTranslator {

    /** LegacySupply accepts Qty 1..99. */
    static final int MAX_CASES = 99;

    private SupplierTranslator() {
    }

    /** Whole cases needed to cover unitsNeeded, rounded up, clamped to 1..99. */
    static int casesFor(int unitsNeeded, int packSize) {
        if (unitsNeeded <= 0 || packSize <= 0) {
            throw new IllegalArgumentException("unitsNeeded and packSize must be positive");
        }
        int cases = (unitsNeeded + packSize - 1) / packSize;
        return Math.min(Math.max(cases, 1), MAX_CASES);
    }

    /** Empty = a code we do not know. The caller decides what to do (see INTEGRATION.md). */
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
