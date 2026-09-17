package edu.cit.berou.shop.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.berou.inventory.dto.InventorySnapshot;
import edu.cit.berou.inventory.service.InventoryService;
import edu.cit.berou.inventory.service.ProductNotFoundException;
import edu.cit.berou.inventory.service.ReservationResult;
import edu.cit.berou.shop.dto.OrderItemOutcome;
import edu.cit.berou.shop.dto.OrderItemRequest;
import edu.cit.berou.shop.dto.OrderItemView;
import edu.cit.berou.shop.dto.OrderResponse;
import edu.cit.berou.shop.dto.OrderSummary;
import edu.cit.berou.shop.entity.Order;
import edu.cit.berou.shop.entity.OrderItem;
import edu.cit.berou.shop.event.OrderCancelledEvent;
import edu.cit.berou.shop.event.OrderPlacedEvent;
import edu.cit.berou.shop.event.OrderRejectedEvent;
import edu.cit.berou.shop.repository.OrderRepository;

@Service
public class OrderService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher events;

    public OrderService(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            ApplicationEventPublisher events
    ) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.events = events;
    }

    @Transactional
    public OrderResponse placeOrder(List<OrderItemRequest> requestItems) {

        // ------------------------------------------------------------
        // 1. Validate the requested items before reserving anything.
        // ------------------------------------------------------------
        Map<String, String> failures = new LinkedHashMap<>();

        for (OrderItemRequest item : requestItems) {
            try {
                InventorySnapshot snapshot =
                        inventoryService.getItem(item.productId());

                int availableStock = snapshot.stock();
                int requestedQuantity = item.quantity();

                if (availableStock <= 0) {
                    failures.put(
                            item.productId(),
                            "Out of stock"
                    );
                } else if (availableStock < requestedQuantity) {
                    failures.put(
                            item.productId(),
                            "Insufficient stock: requested "
                                    + requestedQuantity
                                    + ", available "
                                    + availableStock
                    );
                }

            } catch (ProductNotFoundException ex) {
                failures.put(
                        item.productId(),
                        ex.getMessage()
                );
            }
        }

        // ------------------------------------------------------------
        // 2. If any item cannot be fulfilled, reject the ENTIRE order.
        //    No inventory is reserved or deducted.
        // ------------------------------------------------------------
        if (!failures.isEmpty()) {
            return rejectOrder(requestItems, failures);
        }

        // ------------------------------------------------------------
        // 3. All items have enough stock.
        //    Reserve them now.
        // ------------------------------------------------------------
        Order order = new Order(
                CONFIRMED,
                null,
                Instant.now()
        );

        List<OrderItemOutcome> outcomes = new ArrayList<>();
        List<InventorySnapshot> touchedInventory = new ArrayList<>();

        for (OrderItemRequest item : requestItems) {

            ReservationResult result =
                    inventoryService.reserve(
                            item.productId(),
                            item.quantity()
                    );

            // This means stock changed between validation and reservation.
            // Keep this as an actual conflict because it is different from
            // a normal "out of stock" order rejection.
            if (!result.success()) {
                throw new ReservationConflictException(
                        "Stock for "
                                + item.productId()
                                + " changed before the order could be completed: "
                                + result.reason()
                );
            }

            order.addItem(
                    item.productId(),
                    item.quantity()
            );

            outcomes.add(
                    new OrderItemOutcome(
                            item.productId(),
                            "RESERVED"
                    )
            );

            touchedInventory.add(
                    result.inventory()
            );
        }

        // ------------------------------------------------------------
        // 4. Save confirmed order.
        // ------------------------------------------------------------
        orderRepository.save(order);

        events.publishEvent(
                new OrderPlacedEvent(
                        order.getOrderId(),
                        outcomes.size()
                )
        );

        return new OrderResponse(
                order.getOrderId(),
                CONFIRMED,
                null,
                outcomes,
                touchedInventory
        );
    }

    /**
     * Creates a normal REJECTED order.
     *
     * Important:
     * - No inventory is deducted.
     * - The rejection is stored in the orders table.
     * - The order can still appear in the order history.
     */
    private OrderResponse rejectOrder(
            List<OrderItemRequest> requestItems,
            Map<String, String> failures
    ) {

        String reason = String.join(
                "; ",
                failures.values()
        );

        Order order = new Order(
                REJECTED,
                reason,
                Instant.now()
        );

        List<OrderItemOutcome> outcomes = new ArrayList<>();

        for (OrderItemRequest item : requestItems) {

            order.addItem(
                    item.productId(),
                    item.quantity()
            );

            String outcome =
                    failures.getOrDefault(
                            item.productId(),
                            "NOT_RESERVED"
                    );

            outcomes.add(
                    new OrderItemOutcome(
                            item.productId(),
                            outcome
                    )
            );
        }

        orderRepository.save(order);

        events.publishEvent(
                new OrderRejectedEvent(
                        order.getOrderId(),
                        reason
                )
        );

        return new OrderResponse(
                order.getOrderId(),
                REJECTED,
                reason,
                outcomes,
                List.of()
        );
    }

    @Transactional
    public OrderSummary cancelOrder(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        if (CANCELLED.equals(order.getStatus())) {
            throw new OrderNotCancellableException(
                    "Order " + orderId + " is already cancelled"
            );
        }

        if (!CONFIRMED.equals(order.getStatus())) {
            throw new OrderNotCancellableException(
                    "Order "
                            + orderId
                            + " cannot be cancelled from status "
                            + order.getStatus()
            );
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.restock(
                    item.getProductId(),
                    item.getQuantity()
            );
        }

        order.setStatus(CANCELLED);

        orderRepository.save(order);

        events.publishEvent(
                new OrderCancelledEvent(
                        order.getOrderId()
                )
        );

        return toSummary(order);
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> listOrders() {

        return orderRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private OrderSummary toSummary(Order order) {

        List<OrderItemView> items =
                order.getItems()
                        .stream()
                        .map(
                                i -> new OrderItemView(
                                        i.getProductId(),
                                        i.getQuantity()
                                )
                        )
                        .toList();

        return new OrderSummary(
                order.getOrderId(),
                order.getStatus(),
                order.getReason(),
                order.getCreatedAt(),
                items
        );
    }
}

