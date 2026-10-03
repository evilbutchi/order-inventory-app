package edu.cit.berou.channel;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "channel_orders")
class ChannelOrder {

    static final String ACCEPTED = "ACCEPTED";
    static final String REJECTED = "REJECTED";
    static final String BACKORDERED = "BACKORDERED";
    static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tiangge_order_id", nullable = false, unique = true)
    private String tiangeOrderId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "decision_sent_at")
    private Instant decisionSentAt;

    @Column(name = "cancellation_confirmed_at")
    private Instant cancellationConfirmedAt;

    
    @Column(name = "pending_resolution")
    private String pendingResolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "channelOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ChannelOrderItem> items = new ArrayList<>();

    protected ChannelOrder() {
        
    }

    ChannelOrder(String tiangeOrderId, Long orderId, String status, Instant createdAt) {
        this.tiangeOrderId = tiangeOrderId;
        this.orderId = orderId;
        this.status = status;
        this.createdAt = createdAt;
    }

    void addItem(String productId, int quantity) {
        items.add(new ChannelOrderItem(this, productId, quantity));
    }

    Long getId() {
        return id;
    }

    String getTiangeOrderId() {
        return tiangeOrderId;
    }

    Long getOrderId() {
        return orderId;
    }

    void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    Instant getDecisionSentAt() {
        return decisionSentAt;
    }

    String getPendingResolution() {
        return pendingResolution;
    }

    void setPendingResolution(String pendingResolution) {
        this.pendingResolution = pendingResolution;
    }

    void setDecisionSentAt(Instant decisionSentAt) {
        this.decisionSentAt = decisionSentAt;
    }

    Instant getCancellationConfirmedAt() {
        return cancellationConfirmedAt;
    }

    void setCancellationConfirmedAt(Instant cancellationConfirmedAt) {
        this.cancellationConfirmedAt = cancellationConfirmedAt;
    }

    Instant getResolvedAt() {
        return resolvedAt;
    }

    void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    List<ChannelOrderItem> getItems() {
        return items;
    }
}
