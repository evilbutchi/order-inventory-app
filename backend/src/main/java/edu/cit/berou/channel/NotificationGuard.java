package edu.cit.berou.channel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Component
class NotificationGuard {

    private static final long HOLD_MS = 30_000;

    private final ConcurrentMap<Long, Long> busyUntil = new ConcurrentHashMap<>();

    @EventListener
    public void onDecisionReady(ChannelInternalEvents.DecisionReady event) {
        hold(event.channelOrderId());
    }

    @EventListener
    public void onCancellationHandled(ChannelInternalEvents.CancellationHandled event) {
        hold(event.channelOrderId());
    }

    @EventListener
    public void onResolutionReady(ChannelInternalEvents.ResolutionReady event) {
        hold(event.channelOrderId());
    }

    void hold(Long channelOrderId) {
        if (channelOrderId != null) {
            busyUntil.put(channelOrderId, System.currentTimeMillis() + HOLD_MS);
        }
    }

    void release(Long channelOrderId) {
        if (channelOrderId != null) {
            busyUntil.remove(channelOrderId);
        }
    }

    boolean isBusy(Long channelOrderId) {
        Long until = busyUntil.get(channelOrderId);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            busyUntil.remove(channelOrderId, until);
            return false;
        }
        return true;
    }

    boolean hasBusyNotifications() {
        return busyUntil.keySet().stream().anyMatch(this::isBusy);
    }
}