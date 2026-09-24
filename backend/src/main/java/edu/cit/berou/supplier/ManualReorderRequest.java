package edu.cit.berou.supplier;

/** Body for the manual test endpoint POST /api/supplier-orders/reorders. */
public record ManualReorderRequest(String productId, int units) {
}
