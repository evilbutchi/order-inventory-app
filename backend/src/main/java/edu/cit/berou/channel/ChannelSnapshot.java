package edu.cit.berou.channel;

import java.time.Instant;


public record ChannelSnapshot(
        String instanceId,
        Instant startedAt,
        long uptimeSeconds,
        long feedCursor,
        long acceptedCount,
        long rejectedCount,
        long backorderedCount,
        long cancelledCount
) {
}
