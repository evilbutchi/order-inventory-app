package edu.cit.berou.shop.controller;

import edu.cit.berou.shop.dto.OrderResponse;
import edu.cit.berou.shop.dto.OrderSummary;
import edu.cit.berou.shop.dto.PlaceOrderRequest;
import edu.cit.berou.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse response = orderService.placeOrder(request.items());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    public ResponseEntity<OrderSummary> cancelOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }

    @GetMapping("/api/orders")
    public List<OrderSummary> listOrders() {
        return orderService.listOrders();
    }
}
