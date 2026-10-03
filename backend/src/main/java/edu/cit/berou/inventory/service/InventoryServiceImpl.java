package edu.cit.berou.inventory.service;

import edu.cit.berou.inventory.dto.InventorySnapshot;
import edu.cit.berou.inventory.entity.InventoryItem;
import edu.cit.berou.inventory.event.InventoryChangedEvent;
import edu.cit.berou.inventory.event.LowStockEvent;
import edu.cit.berou.inventory.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


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
    public InventorySnapshot getItemForUpdate(String productId) {
        InventoryItem item = inventoryRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toSnapshot(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventorySnapshot> listAll() {
        return inventoryRepository.findAll().stream().map(this::toSnapshot).toList();
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        if (quantity <= 0) {
            InventoryItem current = inventoryRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            return ReservationResult.rejected("Quantity must be greater than zero", toSnapshot(current));
        }

        
        
        InventoryItem item = inventoryRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (item.getStock() < quantity) {
            return ReservationResult.rejected(
                    "Insufficient stock: requested " + quantity + ", available " + item.getStock(),
                    toSnapshot(item));
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        events.publishEvent(new InventoryChangedEvent(item.getProductId(), item.getStock()));

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
        events.publishEvent(new InventoryChangedEvent(item.getProductId(), item.getStock()));
        return toSnapshot(item);
    }

    private InventorySnapshot toSnapshot(InventoryItem item) {
        return new InventorySnapshot(item.getProductId(), item.getName(), item.getStock());
    }
}
