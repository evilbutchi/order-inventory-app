package edu.cit.berou.inventory.controller;

import edu.cit.berou.inventory.dto.InventorySnapshot;
import edu.cit.berou.inventory.repository.InventoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Not required by the assignment spec, but exposed so the React product
 * dropdown has something real to populate from instead of hardcoded values.
 * Reads straight from the repository since this is a simple listing, not a
 * business operation that needs to go through InventoryService.
 */
@RestController
public class InventoryController {

    private final InventoryRepository inventoryRepository;

    public InventoryController(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @GetMapping("/api/inventory")
    public List<InventorySnapshot> listInventory() {
        return inventoryRepository.findAll().stream()
                .map(item -> new InventorySnapshot(item.getProductId(), item.getName(), item.getStock()))
                .toList();
    }
}
