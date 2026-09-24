package edu.cit.berou.supplier;

/**
 * Our own lifecycle for a supplier order. LegacySupply's numeric codes are
 * translated to this (and back) only inside the supplier module.
 * Declaration order matters: the tracker only ever moves an order forward.
 */
public enum SupplierOrderStatus {
    /** Recorded locally, not yet accepted by the supplier (outage, retry pending). */
    PENDING,
    ACCEPTED,
    PICKING,
    SHIPPED,
    DELIVERED,
    /** Supplier permanently refused the order (bad item, bad quantity, ...). Needs a human. */
    FAILED;

    /** Orders we still expect something from. */
    public boolean isOpen() {
        return this == PENDING || this == ACCEPTED || this == PICKING || this == SHIPPED;
    }
}
