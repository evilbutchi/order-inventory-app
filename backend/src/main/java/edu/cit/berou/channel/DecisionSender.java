package edu.cit.berou.channel;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;


@Component
class DecisionSender {

    private static final Logger log = LoggerFactory.getLogger(DecisionSender.class);

    private final TiangeClient tiangeClient;
    private final ChannelOrderRepository channelOrderRepository;
    private final StockPublisher stockPublisher;
    private final NotificationGuard guard;
    private final TransactionTemplate newTx;

    DecisionSender(TiangeClient tiangeClient, ChannelOrderRepository channelOrderRepository,
                   StockPublisher stockPublisher, NotificationGuard guard,
                   PlatformTransactionManager txManager) {
        this.tiangeClient = tiangeClient;
        this.channelOrderRepository = channelOrderRepository;
        this.stockPublisher = stockPublisher;
        this.guard = guard;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private ChannelOrder load(Long id) {
        return newTx.execute(s -> channelOrderRepository.findById(id).orElse(null));
    }

    private void persist(ChannelOrder channelOrder) {
        newTx.executeWithoutResult(s -> channelOrderRepository.save(channelOrder));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDecisionReady(ChannelInternalEvents.DecisionReady event) {
        try {
            ChannelOrder channelOrder = load(event.channelOrderId());
            if (channelOrder == null) {
                log.warn("DecisionReady references missing ChannelOrder {}", event.channelOrderId());
                return;
            }
            if (channelOrder.getDecisionSentAt() != null) {
                return; 
            }

            boolean sent = tiangeClient.decide(channelOrder.getTiangeOrderId(), event.decision(),
                    "SO-" + channelOrder.getOrderId(), event.reason());
            if (sent) {
                log.info("Tiangge decision sent: order={} decision={}",
                        channelOrder.getTiangeOrderId(), event.decision());
                channelOrder.setDecisionSentAt(Instant.now());
                persist(channelOrder);
                guard.release(event.channelOrderId());
                stockPublisher.flushDirty(); 
            } else {
                log.warn("Decision for Tiangge order {} not yet confirmed; flush job will retry.",
                        channelOrder.getTiangeOrderId());
            }
        } finally {
            guard.release(event.channelOrderId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCancellationHandled(ChannelInternalEvents.CancellationHandled event) {
        try {
            ChannelOrder channelOrder = load(event.channelOrderId());
            if (channelOrder == null) {
                log.warn("CancellationHandled references missing ChannelOrder {}", event.channelOrderId());
                return;
            }
            if (channelOrder.getCancellationConfirmedAt() != null) {
                return; 
            }

            boolean confirmed = tiangeClient.confirmCancellation(channelOrder.getTiangeOrderId());
            if (confirmed) {
                log.info("Tiangge cancellation confirmed: {}", channelOrder.getTiangeOrderId());
                channelOrder.setCancellationConfirmedAt(Instant.now());
                persist(channelOrder);
                guard.release(event.channelOrderId());
                stockPublisher.flushDirty(); 
            } else {
                log.warn("Cancellation confirm for Tiangge order {} not yet sent; flush job will retry.",
                        channelOrder.getTiangeOrderId());
            }
        } finally {
            guard.release(event.channelOrderId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onResolutionReady(ChannelInternalEvents.ResolutionReady event) {
        try {
            ChannelOrder channelOrder = load(event.channelOrderId());
            if (channelOrder == null) {
                log.warn("ResolutionReady references missing ChannelOrder {}", event.channelOrderId());
                return;
            }

            boolean sent = tiangeClient.resolve(channelOrder.getTiangeOrderId(), event.status());
            if (sent) {
                log.info("Tiangge resolution sent: order={} status={}",
                        channelOrder.getTiangeOrderId(), event.status());
                channelOrder.setStatus(event.status());
                channelOrder.setPendingResolution(null);
                channelOrder.setResolvedAt(Instant.now());
                persist(channelOrder);
                guard.release(event.channelOrderId());
                stockPublisher.flushDirty(); 
            } else {
                log.warn("Resolution for Tiangge order {} not yet sent; flush job will retry.",
                        channelOrder.getTiangeOrderId());
            }
        } finally {
            guard.release(event.channelOrderId());
        }
    }
}