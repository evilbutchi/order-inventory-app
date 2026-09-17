package edu.cit.berou.notification.listener;

import edu.cit.berou.inventory.event.LowStockEvent;
import edu.cit.berou.shop.event.OrderCancelledEvent;
import edu.cit.berou.notification.entity.Notification;
import edu.cit.berou.notification.repository.NotificationRepository;
import edu.cit.berou.shop.event.OrderPlacedEvent;
import edu.cit.berou.shop.event.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Notification only ever depends on the event classes published by Order
 * and Inventory - it never calls OrderService or InventoryService, and
 * neither of those modules imports anything from this package. Listeners
 * run synchronously (Spring's default): each one executes inline, inside
 * the same transaction as the publisher, so a notification for a confirmed
 * order only survives if that order's transaction actually commits. Kept
 * synchronous (not @Async) for that reason - see README.
 */
@Component
class OrderEventListener {

    private final NotificationRepository notificationRepository;

    OrderEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        save("Order " + event.orderId() + " confirmed (" + event.itemCount() + " item(s))");
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        save("Order " + event.orderId() + " rejected: " + event.reason());
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        save("Order " + event.orderId() + " cancelled, items restocked");
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        save("Reorder needed: " + event.productName() + " (" + event.productId() + ") stock at "
                + event.currentStock() + ", below threshold of " + event.threshold());
    }

    private void save(String message) {
        notificationRepository.save(new Notification(message, Instant.now()));
    }
}
