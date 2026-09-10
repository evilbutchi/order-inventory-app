package edu.cit.berou.shop.service;

import edu.cit.berou.inventory.service.InventoryService;
import edu.cit.berou.inventory.service.ProductNotFoundException;
import edu.cit.berou.inventory.service.ReservationResult;
import edu.cit.berou.shop.dto.OrderResponse;
import edu.cit.berou.shop.entity.Order;
import edu.cit.berou.shop.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Order module's business logic. This class only ever references
 * InventoryService (the interface) and types from
 * edu.cit.berou.inventory.dto/.service that are part of that public
 * contract. It cannot import InventoryServiceImpl - that class is
 * package-private to edu.cit.berou.inventory.service and simply isn't
 * visible from here. Spring wires whichever InventoryService bean exists
 * at runtime via constructor injection.
 */
@Service
public class OrderService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String REJECTED = "REJECTED";

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(String productId, int quantity) {
        try {
            ReservationResult result = inventoryService.reserve(productId, quantity);

            String status = result.success() ? CONFIRMED : REJECTED;
            Order order = new Order(productId, quantity, status, result.reason(), Instant.now());
            orderRepository.save(order);

            return new OrderResponse(status, result.reason(), result.inventory());
        } catch (ProductNotFoundException ex) {
            Order order = new Order(productId, quantity, REJECTED, ex.getMessage(), Instant.now());
            orderRepository.save(order);
            return new OrderResponse(REJECTED, ex.getMessage(), null);
        }
    }
}
