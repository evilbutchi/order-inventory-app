package edu.cit.berou.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Part E: periodically asks LegacySupply how our open purchase orders are
 * doing, translates its status codes into our enum, and announces deliveries
 * with a SupplierOrderDelivered event. Inventory listens to that event and
 * restocks; nobody calls the supplier module directly.
 *
 * Quota discipline: at most supplier.tracking-batch-size orders per cycle,
 * least-recently-checked first, and the cycle stops at the first supplier
 * problem instead of hammering it.
 */
@Component
class DeliveryTracker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTracker.class);
    private static final Set<SupplierOrderStatus> TRACKED =
            EnumSet.of(SupplierOrderStatus.ACCEPTED, SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);

    private final SupplierOrderRepository orders;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final int batchSize;

    DeliveryTracker(SupplierOrderRepository orders,
                    LegacySupplyClient client,
                    ApplicationEventPublisher events,
                    PlatformTransactionManager txManager,
                    @Value("${supplier.tracking-batch-size:10}") int batchSize) {
        this.orders = orders;
        this.client = client;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${supplier.tracking-initial-delay-ms:10000}",
            fixedDelayString = "${supplier.tracking-interval-ms:30000}")
    public void poll() {
        List<SupplierOrder> open = orders.findByStatusInAndPoNumberIsNotNullOrderByUpdatedAtAsc(
                TRACKED, PageRequest.of(0, batchSize));
        if (open.isEmpty()) {
            return;
        }
        log.info("Tracking job: checking {} open supplier order(s)", open.size());

        for (SupplierOrder o : open) {
            try {
                LegacyXml.PoDoc current = client.getOrder(o.getPoNumber());
                apply(o.getId(), current.statusCode());
            } catch (LegacySupplyException e) {
                if (e.kind() == LegacySupplyException.Kind.REJECTED) {
                    // e.g. E-PO-04 (order not found). Keep the row as it is and look at the next one.
                    log.error("Cannot track {} ({}): {}", o.getBuyerRef(), o.getPoNumber(), e.getMessage());
                    touch(o.getId());
                } else {
                    log.warn("Tracking paused: {}", e.getMessage());
                    return; // supplier trouble: stop this cycle, try again next interval
                }
            } catch (RuntimeException e) {
                // e.g. restock failed inside the transaction: the order stays as it was and is re-checked later
                log.error("Failed to process update for {}", o.getBuyerRef(), e);
            }
        }
    }

    /**
     * Status change + delivery event in ONE transaction: an order is only
     * ever marked DELIVERED together with the restock it triggers, and only
     * the transition into DELIVERED publishes the event (no double restock).
     */
    private void apply(long orderId, int legacyCode) {
        tx.executeWithoutResult(txStatus -> {
            SupplierOrder order = orders.findById(orderId).orElseThrow();
            SupplierOrderStatus before = order.getStatus();

            Optional<SupplierOrderStatus> mapped = SupplierTranslator.toStatus(legacyCode);
            if (mapped.isEmpty()) {
                // Unknown code: change nothing, restock nothing. See INTEGRATION.md.
                log.warn("Unexpected LegacySupply status code {} for {} ({}); leaving it {} and never restocking on an unknown status",
                        legacyCode, order.getBuyerRef(), order.getPoNumber(), before);
                order.touch();
                orders.save(order);
                return;
            }

            SupplierOrderStatus next = mapped.get();
            if (next.ordinal() < before.ordinal()) {
                log.warn("Ignoring status regression {} -> {} for {}", before, next, order.getBuyerRef());
                order.touch();
                orders.save(order);
                return;
            }

            if (next == before) {
                order.touch(); // remember we checked, so the next batch looks at someone else
                orders.save(order);
                return;
            }

            order.setStatus(next);
            orders.save(order);
            log.info("Supplier order {} ({}): {} -> {}", order.getBuyerRef(), order.getPoNumber(), before, next);

            if (next == SupplierOrderStatus.DELIVERED) {
                events.publishEvent(new SupplierOrderDelivered(
                        order.getId(), order.getBuyerRef(), order.getProductId(), order.getUnits()));
            }
        });
    }

    private void touch(long orderId) {
        tx.executeWithoutResult(s -> orders.findById(orderId).ifPresent(o -> {
            o.touch();
            orders.save(o);
        }));
    }
}
