package com.foody.tracker.repository;

import com.foody.tracker.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<OrderStatusHistory> findByOrderIdOrderByChangedAtAscIdAsc(Long orderId);
}
