package edu.cit.berou.channel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import edu.cit.berou.inventory.event.InventoryChangedEvent;


@Component
class StockPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockPublisher.class);

    private final TiangeClient tiangeClient;
    private final StockUpdateRepository stockUpdateRepository;
    private final ChannelOrderRepository channelOrderRepository;
    private final NotificationGuard notificationGuard;
    private final TransactionTemplate tx;

    StockPublisher(TiangeClient tiangeClient, StockUpdateRepository stockUpdateRepository,
                   ChannelOrderRepository channelOrderRepository, NotificationGuard notificationGuard,
                   PlatformTransactionManager txManager) {
        this.tiangeClient = tiangeClient;
        this.stockUpdateRepository = stockUpdateRepository;
        this.channelOrderRepository = channelOrderRepository;
        this.notificationGuard = notificationGuard;
        this.tx = new TransactionTemplate(txManager);
    }

    
    @EventListener
    public void recordChange(InventoryChangedEvent event) {
        stockUpdateRepository.saveAndFlush(
                new StockUpdate(event.productId(), event.newStock(), Instant.now()));
    }

    
    boolean flushDirty() {
        if (notificationGuard.hasBusyNotifications()
                || !channelOrderRepository.findPendingNotifications().isEmpty()) {
            return false;
        }

        while (true) {
            try {
                Boolean published = tx.execute(status -> {
                    Optional<StockUpdate> next = stockUpdateRepository.findFirstByOrderByIdAsc();
                    if (next.isEmpty()) {
                        return false;
                    }

                    StockUpdate update = next.get();
                    tiangeClient.putStock(List.of(
                            new TiangeMessages.StockEntry(update.getProductId(), update.getStock())));
                    stockUpdateRepository.delete(update);
                    log.info("Tiangge stock published: {} -> {}", update.getProductId(), update.getStock());
                    return true;
                });
                if (!Boolean.TRUE.equals(published)) {
                    return true;
                }
            } catch (RuntimeException e) {
                log.warn("Tiangge stock publish failed; queued updates will be retried: {}", e.getMessage());
                return false;
            }
        }
    }

    @Scheduled(fixedDelay = 500, initialDelay = 3000)
    void publishQueued() {
        flushDirty();
    }
}
