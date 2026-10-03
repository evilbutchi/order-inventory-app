package edu.cit.berou.supplier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    
    @Transactional
    @Query(
            value = "select nextval('supplier_orders_id_seq')",
            nativeQuery = true
    )
    Long nextId();

    List<SupplierOrder> findAllByOrderByIdDesc();

    
    List<SupplierOrder> findByStatusOrderByCreatedAtAsc(
            SupplierOrderStatus status,
            Pageable pageable
    );

    
    List<SupplierOrder> findByStatusInAndPoNumberIsNotNullOrderByUpdatedAtAsc(
            Collection<SupplierOrderStatus> statuses,
            Pageable pageable
    );

    
    Optional<SupplierOrder>
    findFirstByProductIdAndStatusInOrderByCreatedAtDesc(
            String productId,
            Collection<SupplierOrderStatus> statuses
    );

    
    Optional<SupplierOrder>
    findFirstByProductIdAndStatusInAndPoNumberIsNotNullOrderByCreatedAtDesc(
            String productId,
            Collection<SupplierOrderStatus> statuses
    );
}