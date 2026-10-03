package edu.cit.berou.channel;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import edu.cit.berou.shop.dto.OrderItemRequest;
import edu.cit.berou.shop.dto.OrderResponse;
import edu.cit.berou.shop.service.OrderService;
import edu.cit.berou.supplier.SupplierGateway;


@Component
class FeedEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(FeedEventProcessor.class);
    private static final String ORDER_PLACED = "ORDER_PLACED";
    private static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    private final OrderService orderService;
    private final SupplierGateway supplierGateway;
    private final ChannelEventRepository eventRepository;
    private final ChannelOrderRepository channelOrderRepository;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    FeedEventProcessor(OrderService orderService, SupplierGateway supplierGateway,
                       ChannelEventRepository eventRepository, ChannelOrderRepository channelOrderRepository,
                       ApplicationEventPublisher events, PlatformTransactionManager txManager) {
        this.orderService = orderService;
        this.supplierGateway = supplierGateway;
        this.eventRepository = eventRepository;
        this.channelOrderRepository = channelOrderRepository;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
    }

    boolean process(TiangeMessages.FeedEvent event) {
        try {
            tx.executeWithoutResult(status -> {
                if (eventRepository.existsById(event.eventId())) {
                    return;
                }
                
                eventRepository.saveAndFlush(
                        new ChannelEvent(event.eventId(), event.seq(), event.type(), Instant.now()));

                if (ORDER_PLACED.equals(event.type())) {
                    handleOrderPlaced(event);
                } else if (ORDER_CANCELLED.equals(event.type())) {
                    handleOrderCancelled(event);
                } else {
                    log.warn("Unknown feed event type {} (eventId={})", event.type(), event.eventId());
                }
            });
            return true;
        } catch (DataIntegrityViolationException dup) {
            log.debug("Feed event {} already claimed by another poll", event.eventId());
            return true;
        } catch (RuntimeException e) {
            log.error("Failed to process feed event {} ({}): {}", event.eventId(), event.type(), e.getMessage(), e);
            return false;
        }
    }

    private void handleOrderPlaced(TiangeMessages.FeedEvent event) {
        if (channelOrderRepository.findByTiangeOrderId(event.orderId()).isPresent()) {
            return; 
        }

        List<OrderItemRequest> items = event.lines().stream()
                .map(line -> new OrderItemRequest(line.sellerSku(), line.qty()))
                .toList();

        OrderResponse response = orderService.placeOrder(items);

        ChannelOrder channelOrder;
        String decision;
        if ("CONFIRMED".equals(response.status())) {
            channelOrder = new ChannelOrder(event.orderId(), response.orderId(), ChannelOrder.ACCEPTED, Instant.now());
            decision = ChannelOrder.ACCEPTED;
        } else {
            Set<String> productsOrdered = new LinkedHashSet<>(items.stream().map(OrderItemRequest::productId).toList());
            boolean allHaveOpenReorder = !productsOrdered.isEmpty()
                    && productsOrdered.stream().allMatch(supplierGateway::hasOpenReorder);
            if (allHaveOpenReorder) {
                channelOrder = new ChannelOrder(event.orderId(), response.orderId(), ChannelOrder.BACKORDERED, Instant.now());
                for (OrderItemRequest item : items) {
                    channelOrder.addItem(item.productId(), item.quantity());
                }
                decision = ChannelOrder.BACKORDERED;
            } else {
                channelOrder = new ChannelOrder(event.orderId(), response.orderId(), ChannelOrder.REJECTED, Instant.now());
                decision = ChannelOrder.REJECTED;
            }
        }
        channelOrderRepository.save(channelOrder);

        events.publishEvent(new ChannelInternalEvents.DecisionReady(channelOrder.getId(), decision, response.reason()));
    }

    private void handleOrderCancelled(TiangeMessages.FeedEvent event) {
        Optional<ChannelOrder> maybe = channelOrderRepository.findByTiangeOrderId(event.orderId());
        if (maybe.isEmpty()) {
            log.warn("ORDER_CANCELLED for unknown Tiangge order {}", event.orderId());
            return;
        }
        ChannelOrder channelOrder = maybe.get();
        if (ChannelOrder.ACCEPTED.equals(channelOrder.getStatus())) {
            
            
            
            orderService.cancelOrder(channelOrder.getOrderId());
            channelOrder.setStatus(ChannelOrder.CANCELLED);
            channelOrderRepository.save(channelOrder);
        }

        events.publishEvent(new ChannelInternalEvents.CancellationHandled(channelOrder.getId()));
    }
}