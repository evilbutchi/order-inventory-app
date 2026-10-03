package edu.cit.berou.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;


@Entity
@Table(name = "channel_events")
class ChannelEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "seq", nullable = false)
    private long seq;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ChannelEvent() {
        
    }

    ChannelEvent(String eventId, long seq, String type, Instant processedAt) {
        this.eventId = eventId;
        this.seq = seq;
        this.type = type;
        this.processedAt = processedAt;
    }
}
