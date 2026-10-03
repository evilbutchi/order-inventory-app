package edu.cit.berou.supplier;


public enum SupplierOrderStatus {
   
    PENDING,
    ACCEPTED,
    PICKING,
    SHIPPED,
    DELIVERED,
    
    FAILED;

    
    public boolean isOpen() {
        return this == PENDING || this == ACCEPTED || this == PICKING || this == SHIPPED;
    }
}
