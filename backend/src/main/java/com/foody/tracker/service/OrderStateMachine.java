package com.foody.tracker.service;

import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.exception.InvalidStatusTransitionException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.RECEBIDO, EnumSet.of(OrderStatus.EM_PREPARO, OrderStatus.CANCELADO),
            OrderStatus.EM_PREPARO, EnumSet.of(OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.CANCELADO),
            OrderStatus.SAIU_PARA_ENTREGA, EnumSet.of(OrderStatus.ENTREGUE),
            OrderStatus.ENTREGUE, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELADO, EnumSet.noneOf(OrderStatus.class));

    public void validateTransition(OrderStatus from, OrderStatus to) {
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new InvalidStatusTransitionException(from, to);
        }
    }
}
