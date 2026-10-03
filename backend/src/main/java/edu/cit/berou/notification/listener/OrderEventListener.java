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
