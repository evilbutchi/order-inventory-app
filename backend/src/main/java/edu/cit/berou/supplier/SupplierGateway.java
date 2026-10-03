package edu.cit.berou.supplier;


public interface SupplierGateway {

    
    ReorderResult requestReorder(String productId, int unitsNeeded);

    
    boolean hasOpenReorder(String productId);

    
    java.util.Optional<String> supplierSkuFor(String productId);
}
