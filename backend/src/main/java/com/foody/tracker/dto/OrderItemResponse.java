package com.foody.tracker.dto;

import com.foody.tracker.entity.OrderItem;
import java.math.BigDecimal;

public record OrderItemResponse(Long id, String name, Integer quantity, BigDecimal unitPrice) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getName(), item.getQuantity(), item.getUnitPrice());
    }
}
