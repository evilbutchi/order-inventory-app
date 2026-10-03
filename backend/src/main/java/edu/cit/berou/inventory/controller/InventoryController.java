package edu.cit.berou.inventory.controller;

import edu.cit.berou.inventory.dto.InventorySnapshot;
import edu.cit.berou.inventory.repository.InventoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


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
