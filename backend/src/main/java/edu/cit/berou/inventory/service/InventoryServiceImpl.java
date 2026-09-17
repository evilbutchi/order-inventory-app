package edu.cit.berou.inventory.service;

import edu.cit.berou.inventory.dto.InventorySnapshot;
import edu.cit.berou.inventory.entity.InventoryItem;
import edu.cit.berou.inventory.event.LowStockEvent;
import edu.cit.berou.inventory.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private on purpose: this class is NOT visible outside
 * edu.cit.berou.inventory.service. Spring can still find and wire it via
 * reflection, but no other module's source can import it, reference its
 * type, or new it up. The Order module only ever sees InventoryService.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher events;
    private final int lowStockThreshold;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                          ApplicationEventPublisher events,
                          @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.events = events;
        this.lowStockThreshold = lowStockThreshold;
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

        if (item.getStock() < lowStockThreshold) {
            events.publishEvent(new LowStockEvent(item.getProductId(), item.getName(),
                    item.getStock(), lowStockThreshold));
        }

        return ReservationResult.approved(toSnapshot(item));
    }

    @Override
    @Transactional
    public InventorySnapshot restock(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
        return toSnapshot(item);
    }

    private InventorySnapshot toSnapshot(InventoryItem item) {
        return new InventorySnapshot(item.getProductId(), item.getName(), item.getStock());
    }
}
