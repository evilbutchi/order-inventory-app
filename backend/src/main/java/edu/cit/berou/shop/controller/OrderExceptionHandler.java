package edu.cit.berou.shop.controller;

import edu.cit.berou.shop.dto.OrderResponse;
import edu.cit.berou.shop.service.OrderNotCancellableException;
import edu.cit.berou.shop.service.OrderNotFoundException;
import edu.cit.berou.shop.service.ReservationConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Turns @Valid failures and order-specific exceptions into readable JSON
 * bodies instead of raw error pages.
 */
@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OrderResponse> handleValidation(MethodArgumentNotValidException ex) {
        String reason = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new OrderResponse(null, "REJECTED", reason, List.of(), List.of()));
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<OrderResponse> handleReservationConflict(ReservationConflictException ex) {
        // The whole placeOrder() transaction already rolled back - nothing
        // was persisted or reserved, so this is still a plain REJECTED
        // response, just triggered by a race instead of an up-front check.
        return ResponseEntity.ok(new OrderResponse(null, "REJECTED", ex.getMessage(), List.of(), List.of()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("reason", ex.getMessage()));
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<Map<String, String>> handleNotCancellable(OrderNotCancellableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", ex.getMessage()));
    }
}
