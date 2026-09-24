package edu.cit.berou.supplier;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    /**
     * Reserve the next id so the BuyerRef ("RO-" + id) is known before the
     * row is inserted. Needs a read-write transaction (Postgres refuses
     * nextval() inside a read-only one), hence the explicit @Transactional.
     */
    @Transactional
    @Query(value = "select nextval('supplier_orders_id_seq')", nativeQuery = true)
    Long nextId();

    List<SupplierOrder> findAllByOrderByIdDesc();

    /** Oldest PENDING orders first: what the retry job sends. */
    List<SupplierOrder> findByStatusOrderByCreatedAtAsc(SupplierOrderStatus status, Pageable pageable);

    /** Open orders the supplier has accepted; least recently checked first, so a batch limit cannot starve anyone. */
    List<SupplierOrder> findByStatusInAndPoNumberIsNotNullOrderByUpdatedAtAsc(
            Collection<SupplierOrderStatus> statuses, Pageable pageable);

    Optional<SupplierOrder> findFirstByProductIdAndStatusInOrderByCreatedAtDesc(
            String productId, Collection<SupplierOrderStatus> statuses);
}
