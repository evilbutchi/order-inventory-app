package edu.cit.berou.shop.controller;

import edu.cit.berou.shop.dto.OrderResponse;
import edu.cit.berou.shop.dto.PlaceOrderRequest;
import edu.cit.berou.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse response = orderService.placeOrder(request.productId(), request.quantity());
        return ResponseEntity.ok(response);
    }
}
