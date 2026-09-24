package edu.cit.berou.reorder;

import edu.cit.berou.inventory.event.LowStockEvent;
import edu.cit.berou.supplier.ReorderResult;
import edu.cit.berou.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The Low-Stock Auto-Reorder Rule. In Lab 2 this only logged "reorder needed";
 * now it places a real reorder through SupplierGateway - and it knows nothing
 * about LegacySupply, only "product id" and "units".
 *
 * Runs AFTER the customer's order transaction has committed, so:
 *  - a rolled-back order (all-or-nothing rejection) never triggers a reorder, and
 *  - nothing that goes wrong here can roll back or fail a customer's order.
 */
@Component
class AutoReorderRule {

    private static final Logger log = LoggerFactory.getLogger(AutoReorderRule.class);

    private final SupplierGateway supplierGateway;
    private final int targetStock;

    AutoReorderRule(SupplierGateway supplierGateway,
                    @Value("${app.reorder.target-stock:20}") int targetStock) {
        this.supplierGateway = supplierGateway;
        this.targetStock = targetStock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLowStock(LowStockEvent event) {
        int unitsNeeded = Math.max(1, targetStock - event.currentStock());
        try {
            ReorderResult result = supplierGateway.requestReorder(event.productId(), unitsNeeded);
            log.info("Low stock on {} ({} left, threshold {}): reorder {} for {} units is {}",
                    event.productId(), event.currentStock(), event.threshold(),
                    result.buyerRef(), result.units(), result.status());
        } catch (RuntimeException e) {
            log.error("Low stock on {} but the reorder could not be recorded: {}", event.productId(), e.getMessage(), e);
        }
    }
}
