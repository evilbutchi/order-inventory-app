package edu.cit.berou.channel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
class PendingNotificationFlusher {

    private static final Logger log = LoggerFactory.getLogger(PendingNotificationFlusher.class);

    private final TiangeClient tiangeClient;
    private final ChannelOrderRepository channelOrderRepository;
    private final StockPublisher stockPublisher;
    private final NotificationGuard guard;

    PendingNotificationFlusher(TiangeClient tiangeClient, ChannelOrderRepository channelOrderRepository,
                               StockPublisher stockPublisher, NotificationGuard guard) {
        this.tiangeClient = tiangeClient;
        this.channelOrderRepository = channelOrderRepository;
        this.stockPublisher = stockPublisher;
        this.guard = guard;
    }

    @Scheduled(fixedDelayString = "${channel.flush-interval-ms:1000}",
            initialDelayString = "${channel.flush-interval-ms:1000}")
    public void flush() {
        List<ChannelOrder> pending = channelOrderRepository.findPendingNotifications();
        for (ChannelOrder candidate : pending) {
            if (guard.isBusy(candidate.getId())) {
                continue; 
            }
            flushOne(candidate.getId());
        }
        if (channelOrderRepository.findPendingNotifications().isEmpty()) {
            stockPublisher.flushDirty();
        }
    }

    private void flushOne(Long id) {
        Optional<ChannelOrder> maybe = channelOrderRepository.findById(id); 
        if (maybe.isEmpty()) {
            return;
        }
        ChannelOrder channelOrder = maybe.get();
        boolean changed = false;

        if (channelOrder.getDecisionSentAt() == null && isDecisionStatus(channelOrder.getStatus())) {
            boolean sent = tiangeClient.decide(channelOrder.getTiangeOrderId(), channelOrder.getStatus(),
                    "SO-" + channelOrder.getOrderId(), null);
            if (sent) {
                channelOrder.setDecisionSentAt(Instant.now());
                changed = true;
                log.info("Flushed decision for Tiangge order {}", channelOrder.getTiangeOrderId());
            }
        }
        if (ChannelOrder.CANCELLED.equals(channelOrder.getStatus()) && channelOrder.getCancellationConfirmedAt() == null) {
            boolean confirmed = tiangeClient.confirmCancellation(channelOrder.getTiangeOrderId());
            if (confirmed) {
                channelOrder.setCancellationConfirmedAt(Instant.now());
                changed = true;
                log.info("Flushed cancellation confirm for Tiangge order {}", channelOrder.getTiangeOrderId());
            }
        }
        if (channelOrder.getPendingResolution() != null && channelOrder.getResolvedAt() == null) {
            boolean sent = tiangeClient.resolve(channelOrder.getTiangeOrderId(), channelOrder.getPendingResolution());
            if (sent) {
                channelOrder.setStatus(channelOrder.getPendingResolution());
                channelOrder.setPendingResolution(null);
                channelOrder.setResolvedAt(Instant.now());
                changed = true;
                log.info("Flushed resolution for Tiangge order {}", channelOrder.getTiangeOrderId());
            }
        }
        if (changed) {
            channelOrderRepository.save(channelOrder);
        }
    }

    private static boolean isDecisionStatus(String status) {
        return ChannelOrder.ACCEPTED.equals(status)
                || ChannelOrder.REJECTED.equals(status)
                || ChannelOrder.BACKORDERED.equals(status);
    }
}