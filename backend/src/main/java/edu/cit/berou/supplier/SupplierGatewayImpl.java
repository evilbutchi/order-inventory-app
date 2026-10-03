package edu.cit.berou.supplier;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;


@Component
class SupplierGatewayImpl implements SupplierGateway {

    
    private static final Set<SupplierOrderStatus> OPEN_SUPPLIER_ORDERS =
            EnumSet.of(
                    SupplierOrderStatus.ACCEPTED,
                    SupplierOrderStatus.PICKING,
                    SupplierOrderStatus.SHIPPED
            );

    private record Recorded(SupplierOrder order, boolean created) {
    }

    private final SupplierOrderRepository orders;
    private final SupplierItemRepository items;
    private final ReorderSubmitter submitter;
    private final TransactionTemplate newTx;
    private final boolean dedupeOpenOrders;

    SupplierGatewayImpl(
            SupplierOrderRepository orders,
            SupplierItemRepository items,
            ReorderSubmitter submitter,
            PlatformTransactionManager txManager,
            @Value("${supplier.reorder.dedupe-open-orders:true}")
            boolean dedupeOpenOrders) {

        this.orders = orders;
        this.items = items;
        this.submitter = submitter;

        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        this.dedupeOpenOrders = dedupeOpenOrders;
    }

    @Override
    public ReorderResult requestReorder(
            String productId,
            int unitsNeeded) {

        if (unitsNeeded <= 0) {
            throw new IllegalArgumentException(
                    "unitsNeeded must be positive"
            );
        }

        SupplierItem item = items.findById(productId)
                .orElseThrow(
                        () -> new UnknownSupplierProductException(productId)
                );

        Recorded recorded = newTx.execute(status -> {

            
            if (dedupeOpenOrders) {

                Set<SupplierOrderStatus> localOpenStatuses =
                        EnumSet.of(
                                SupplierOrderStatus.PENDING,
                                SupplierOrderStatus.ACCEPTED,
                                SupplierOrderStatus.PICKING,
                                SupplierOrderStatus.SHIPPED
                        );

                Optional<SupplierOrder> open =
                        orders.findFirstByProductIdAndStatusInOrderByCreatedAtDesc(
                                productId,
                                localOpenStatuses
                        );

                if (open.isPresent()) {
                    return new Recorded(open.get(), false);
                }
            }

            int cases = SupplierTranslator.casesFor(
                    unitsNeeded,
                    item.getPackSize()
            );

            long id = orders.nextId();

            SupplierOrder order = new SupplierOrder(
                    id,
                    productId,
                    "RO-" + id,
                    UUID.randomUUID().toString(),
                    cases,
                    cases * item.getPackSize()
            );

            return new Recorded(
                    orders.save(order),
                    true
            );
        });

        
        if (recorded.created()) {
            submitter.enqueue(recorded.order().getId());
        }

        return recorded.order().toResult();
    }

    @Override
    public boolean hasOpenReorder(String productId) {

        
        return orders
                .findFirstByProductIdAndStatusInAndPoNumberIsNotNullOrderByCreatedAtDesc(
                        productId,
                        OPEN_SUPPLIER_ORDERS
                )
                .isPresent();
    }

    @Override
    public Optional<String> supplierSkuFor(String productId) {
        return items.findById(productId)
                .map(SupplierItem::getSupplierSku);
    }
}