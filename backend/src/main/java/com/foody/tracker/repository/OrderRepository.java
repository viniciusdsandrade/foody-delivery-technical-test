package com.foody.tracker.repository;

import com.foody.tracker.entity.Order;
import com.foody.tracker.entity.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "items")
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
