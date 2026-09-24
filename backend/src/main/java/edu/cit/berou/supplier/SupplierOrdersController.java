package edu.cit.berou.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Small read endpoint for your evidence (README / REFLECTION) plus a manual
 * trigger for testing the gateway without having to sell stock down first.
 * Speaks only our terms.
 */
@RestController
@RequestMapping("/api/supplier-orders")
class SupplierOrdersController {

    private final SupplierOrderRepository orders;
    private final SupplierGateway gateway;

    SupplierOrdersController(SupplierOrderRepository orders, SupplierGateway gateway) {
        this.orders = orders;
        this.gateway = gateway;
    }

    @GetMapping
    List<SupplierOrderView> list() {
        return orders.findAllByOrderByIdDesc().stream().map(SupplierOrder::toView).toList();
    }

    @PostMapping("/reorders")
    ResponseEntity<ReorderResult> reorder(@RequestBody ManualReorderRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(gateway.requestReorder(request.productId(), request.units()));
    }

    @ExceptionHandler(UnknownSupplierProductException.class)
    ResponseEntity<Map<String, String>> unknownProduct(UnknownSupplierProductException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
