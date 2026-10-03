package edu.cit.berou.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final TiangeClient tiangeClient;

    HeartbeatScheduler(TiangeClient tiangeClient) {
        this.tiangeClient = tiangeClient;
    }

    @Scheduled(fixedDelayString = "${channel.heartbeat-interval-ms:30000}",
            initialDelayString = "${channel.heartbeat-interval-ms:30000}")
    public void sendHeartbeat() {
        try {
            tiangeClient.heartbeat();
        } catch (ChannelException e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }
}
