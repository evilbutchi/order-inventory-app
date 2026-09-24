package edu.cit.berou.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per reorder we ever decided to send to the supplier. This table is
 * the source of truth: a reorder exists the moment its row is committed, and
 * requestId is fixed at that moment so every retry, restart or scheduled
 * re-send uses the same X-Request-Id.
 */
@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, unique = true)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "cases", nullable = false)
    private int cases;

    @Column(name = "units", nullable = false)
    private int units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupplierOrder() {
        // required by JPA
    }

    SupplierOrder(Long id, String productId, String buyerRef, String requestId, int cases, int units) {
        this.id = id;
        this.productId = productId;
        this.buyerRef = buyerRef;
        this.requestId = requestId;
        this.cases = cases;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    Long getId() {
        return id;
    }

    String getProductId() {
        return productId;
    }

    String getBuyerRef() {
        return buyerRef;
    }

    String getRequestId() {
        return requestId;
    }

    String getPoNumber() {
        return poNumber;
    }

    int getCases() {
        return cases;
    }

    int getUnits() {
        return units;
    }

    SupplierOrderStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void markSubmitted(String poNumber, SupplierOrderStatus status) {
        this.poNumber = poNumber;
        this.status = status;
        touch();
    }

    void markFailed() {
        this.status = SupplierOrderStatus.FAILED;
        touch();
    }

    void setStatus(SupplierOrderStatus status) {
        this.status = status;
        touch();
    }

    void touch() {
        this.updatedAt = Instant.now();
    }

    SupplierOrderView toView() {
        return new SupplierOrderView(id, productId, buyerRef, requestId, poNumber, cases, units,
                status, createdAt, updatedAt);
    }

    ReorderResult toResult() {
        return new ReorderResult(id, productId, buyerRef, units, status, poNumber);
    }
}
