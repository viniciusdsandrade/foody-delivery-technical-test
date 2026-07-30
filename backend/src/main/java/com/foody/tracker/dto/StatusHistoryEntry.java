package com.foody.tracker.dto;

import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.entity.OrderStatusHistory;
import java.time.Instant;

public record StatusHistoryEntry(OrderStatus fromStatus, OrderStatus toStatus, ChangedByRef changedBy,
        Instant changedAt) {

    public record ChangedByRef(Long id, String name) {
    }

    public static StatusHistoryEntry from(OrderStatusHistory history) {
        return new StatusHistoryEntry(history.getFromStatus(), history.getToStatus(),
                new ChangedByRef(history.getChangedBy().getId(), history.getChangedBy().getName()),
                history.getChangedAt());
    }
}
