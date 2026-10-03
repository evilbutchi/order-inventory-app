package edu.cit.berou.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "supplier_item_map")
class SupplierItem {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "supplier_sku", nullable = false)
    private String supplierSku;

    @Column(name = "pack_size", nullable = false)
    private int packSize;

    protected SupplierItem() {
        
    }

    String getProductId() {
        return productId;
    }

    String getSupplierSku() {
        return supplierSku;
    }

    int getPackSize() {
        return packSize;
    }
}
