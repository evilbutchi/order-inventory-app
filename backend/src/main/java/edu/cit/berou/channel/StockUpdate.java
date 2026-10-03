package edu.cit.berou.channel;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_stock_updates")
class StockUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockUpdate() {
    }

    StockUpdate(String productId, int stock, Instant createdAt) {
        this.productId = productId;
        this.stock = stock;
        this.createdAt = createdAt;
    }

    String getProductId() {
        return productId;
    }

    int getStock() {
        return stock;
    }
}
