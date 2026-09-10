package edu.cit.berou.inventory.service;

import edu.cit.berou.inventory.dto.InventorySnapshot;
import edu.cit.berou.inventory.entity.InventoryItem;
import edu.cit.berou.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private on purpose: this class is NOT visible outside
 * edu.cit.berou.inventory.service. Spring can still find and wire it because
 * component scanning and dependency injection work by reflection, not by
 * compile-time package access - but no other module's source code can
 * import it, reference its type, or new it up directly. The Order module
 * can only ever see the InventoryService interface, which is what actually
 * enforces the module boundary at compile time.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InventorySnapshot getItem(String productId) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toSnapshot(item);
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        if (quantity <= 0) {
            InventoryItem current = inventoryRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            return ReservationResult.rejected("Quantity must be greater than zero", toSnapshot(current));
        }

        // Locked read so two concurrent orders against the same product
        // can't both pass the stock check before either commits.
        InventoryItem item = inventoryRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (item.getStock() < quantity) {
            return ReservationResult.rejected(
                    "Insufficient stock: requested " + quantity + ", available " + item.getStock(),
                    toSnapshot(item));
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        return ReservationResult.approved(toSnapshot(item));
    }

    private InventorySnapshot toSnapshot(InventoryItem item) {
        return new InventorySnapshot(item.getProductId(), item.getName(), item.getStock());
    }
}
