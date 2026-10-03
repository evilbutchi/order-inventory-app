package edu.cit.berou.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


@Entity
@Table(name = "channel_order_items")
class ChannelOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_order_id", nullable = false)
    private ChannelOrder channelOrder;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ChannelOrderItem() {
        
    }

    ChannelOrderItem(ChannelOrder channelOrder, String productId, int quantity) {
        this.channelOrder = channelOrder;
        this.productId = productId;
        this.quantity = quantity;
    }

    String getProductId() {
        return productId;
    }

    int getQuantity() {
        return quantity;
    }
}
