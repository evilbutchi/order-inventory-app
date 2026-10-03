package edu.cit.berou.channel;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

interface StockUpdateRepository extends JpaRepository<StockUpdate, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockUpdate> findFirstByOrderByIdAsc();
}
