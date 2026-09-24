package edu.cit.berou.supplier;

/**
 * The ONLY door into the supplier module. Callers speak our language
 * (product id, units) and get our own result type back. Nothing about
 * LegacySupply (XML, SKUs, pack sizes, status codes, sessions) crosses
 * this boundary.
 *
 * Contract: once this method returns, the reorder is durably recorded in
 * supplier_orders. If LegacySupply is down, the order simply stays PENDING
 * and is sent later by a scheduled job - the caller never sees an outage.
 */
public interface SupplierGateway {

    /**
     * @param productId   our Inventory product id
     * @param unitsNeeded how many units we want (rounded up to whole cases internally)
     * @throws UnknownSupplierProductException if the product has no supplier mapping
     * @throws IllegalArgumentException        if unitsNeeded is not positive
     */
    ReorderResult requestReorder(String productId, int unitsNeeded);
}
