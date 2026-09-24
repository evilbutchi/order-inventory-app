package edu.cit.berou.supplier;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sends PENDING supplier orders to LegacySupply. Two triggers, one code path:
 *  1. right after a reorder is recorded (background thread, so the customer's
 *     order request is never held up by the supplier), and
 *  2. a @Scheduled job that sweeps whatever is still PENDING - that is how an
 *     outage, a timeout or an app restart never loses a reorder.
 *
 * Nothing here can create a duplicate: the row already holds the requestId
 * and the exact case count, and both are sent unchanged on every attempt.
 */
@Component
class ReorderSubmitter {

    private static final Logger log = LoggerFactory.getLogger(ReorderSubmitter.class);

    enum Outcome { DONE, RETRY_LATER }

    private final SupplierOrderRepository orders;
    private final SupplierItemRepository items;
    private final LegacySupplyClient client;
    private final int batchSize;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "supplier-submit");
        t.setDaemon(true);
        return t;
    });

    ReorderSubmitter(SupplierOrderRepository orders,
                     SupplierItemRepository items,
                     LegacySupplyClient client,
                     @Value("${supplier.pending-batch-size:5}") int batchSize) {
        this.orders = orders;
        this.items = items;
        this.client = client;
        this.batchSize = batchSize;
    }

    /** Fire-and-forget: send this order on the background thread. */
    void enqueue(long orderId) {
        try {
            executor.execute(() -> {
                try {
                    submit(orderId);
                } catch (RuntimeException e) {
                    log.error("Unexpected error submitting supplier order {}; it stays PENDING", orderId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Submitter is shutting down; supplier order {} stays PENDING for the next run", orderId);
        }
    }

    /** Safety net: retry everything still PENDING. */
    @Scheduled(initialDelayString = "${supplier.pending-initial-delay-ms:5000}",
            fixedDelayString = "${supplier.pending-retry-ms:15000}")
    public void retryPending() {
        List<SupplierOrder> pending = orders.findByStatusOrderByCreatedAtAsc(
                SupplierOrderStatus.PENDING, PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retry job: {} PENDING supplier order(s)", pending.size());
        for (SupplierOrder o : pending) {
            try {
                if (submit(o.getId()) == Outcome.RETRY_LATER) {
                    log.info("Supplier still unavailable; leaving the rest PENDING until the next run");
                    break; // do not hammer a service that is down
                }
            } catch (RuntimeException e) {
                log.error("Unexpected error submitting supplier order {}; it stays PENDING", o.getId(), e);
            }
        }
    }

    /**
     * One at a time (synchronized): the executor and the scheduler may both
     * pick up the same row, and the second one must see it already sent.
     */
    synchronized Outcome submit(long orderId) {
        SupplierOrder order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != SupplierOrderStatus.PENDING) {
            return Outcome.DONE;
        }
        SupplierItem item = items.findById(order.getProductId()).orElse(null);
        if (item == null) {
            log.error("Supplier order {} has no supplier mapping for product {}; marking FAILED",
                    order.getBuyerRef(), order.getProductId());
            order.markFailed();
            orders.save(order);
            return Outcome.DONE;
        }

        try {
            LegacyXml.PoDoc ack = client.placeOrder(item.getSupplierSku(), order.getCases(),
                    order.getBuyerRef(), order.getRequestId());
            SupplierOrderStatus status = SupplierTranslator.toStatus(ack.statusCode())
                    .orElseGet(() -> {
                        log.warn("Unexpected status code {} on the acknowledgement of {}; treating as ACCEPTED",
                                ack.statusCode(), order.getBuyerRef());
                        return SupplierOrderStatus.ACCEPTED;
                    });
            order.markSubmitted(ack.poNumber(), status);
            orders.save(order);
            log.info("Supplier order {} placed: {} ({} units) -> {}", order.getBuyerRef(), ack.poNumber(),
                    order.getUnits(), status);
            return Outcome.DONE;

        } catch (LegacySupplyException e) {
            if (e.kind() == LegacySupplyException.Kind.REJECTED) {
                log.error("Supplier order {} permanently refused: {}. Marking FAILED.", order.getBuyerRef(), e.getMessage());
                order.markFailed();
                orders.save(order);
                return Outcome.DONE;
            }
            log.warn("Supplier order {} stays PENDING: {}", order.getBuyerRef(), e.getMessage());
            return Outcome.RETRY_LATER;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
