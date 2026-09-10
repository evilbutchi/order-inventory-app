package edu.cit.berou.shop.repository;

import edu.cit.berou.shop.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
