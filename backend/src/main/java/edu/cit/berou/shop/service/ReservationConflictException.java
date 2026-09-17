package edu.cit.berou.shop.service;

/**
 * Thrown when a reserve() call fails after this order's items already
 * passed the up-front validation pass - i.e. another order won the race
 * for the same stock in between. Throwing (rather than returning a
 * rejected result) is what makes the whole @Transactional placeOrder()
 * roll back any reservations already made earlier in the loop.
 */
public class ReservationConflictException extends RuntimeException {
    public ReservationConflictException(String message) {
        super(message);
    }
}
