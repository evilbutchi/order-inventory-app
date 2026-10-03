package edu.cit.berou.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.berou.shop.dto.OrderItemRequest;
import edu.cit.berou.shop.dto.OrderResponse;
import edu.cit.berou.shop.service.OrderService;
import edu.cit.berou.supplier.SupplierOrderDelivered;

@Component
class BackorderResolver {

    private static final Logger log = LoggerFactory.getLogger(BackorderResolver.class);

    private final OrderService orderService;
    private final ChannelOrderRepository channelOrderRepository;
    private final ApplicationEventPublisher events;

    BackorderResolver(
            OrderService orderService,
            ChannelOrderRepository channelOrderRepository,
            ApplicationEventPublisher events) {

        this.orderService = orderService;
        this.channelOrderRepository = channelOrderRepository;
        this.events = events;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSupplierOrderDelivered(SupplierOrderDelivered delivered) {

        List<ChannelOrder> backordered =
                channelOrderRepository.findBackorderedFor(delivered.productId());

        for (ChannelOrder channelOrder : backordered) {

            List<OrderItemRequest> items = channelOrder.getItems().stream()
                    .map(i -> new OrderItemRequest(
                            i.getProductId(),
                            i.getQuantity()
                    ))
                    .toList();

            OrderResponse response = orderService.placeOrder(items);

            String resolution =
                    "CONFIRMED".equals(response.status())
                            ? ChannelOrder.ACCEPTED
                            : ChannelOrder.CANCELLED;

            channelOrder.setOrderId(response.orderId());
            channelOrder.setPendingResolution(resolution);

            channelOrderRepository.save(channelOrder);

            log.info(
                    "Backorder for Tiangge order {} resolved to {} after delivery of {}",
                    channelOrder.getTiangeOrderId(),
                    resolution,
                    delivered.productId()
            );

            events.publishEvent(
                    new ChannelInternalEvents.ResolutionReady(
                            channelOrder.getId(),
                            resolution
                    )
            );
        }
    }
}