package edu.cit.berou.inventory.service;

import edu.cit.berou.inventory.dto.InventorySnapshot;

import java.util.List;


public interface InventoryService {

    
    InventorySnapshot getItem(String productId);

    
    InventorySnapshot getItemForUpdate(String productId);

    
    List<InventorySnapshot> listAll();

    
    ReservationResult reserve(String productId, int quantity);

    
    InventorySnapshot restock(String productId, int quantity);
}
