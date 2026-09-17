package edu.cit.berou.shop.service;

public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(String message) {
        super(message);
    }
}
