package edu.cit.berou.inventory.repository;

import edu.cit.berou.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Pessimistic write lock so concurrent reservations against the same
     * product_id can't both read stale stock and both succeed. Fine for an
     * academic project's scale; a real system might use optimistic locking
     * with a @Version column instead.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findByIdForUpdate(String productId);
}
