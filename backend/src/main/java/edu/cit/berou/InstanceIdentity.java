package edu.cit.berou;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class InstanceIdentity {

    private static final Logger log = LoggerFactory.getLogger(InstanceIdentity.class);

    private final UUID instanceId = UUID.randomUUID();
    private final Instant startedAt = Instant.now();

    public InstanceIdentity() {
        log.info("Instance identity for this run: {}", instanceId);
    }

    public String instanceId() {
        return instanceId.toString();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long uptimeSeconds() {
        return Duration.between(startedAt, Instant.now()).getSeconds();
    }
}
