package com.foody.tracker.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.exception.InvalidStatusTransitionException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @ParameterizedTest(name = "{0} -> {1} valid={2}")
    @MethodSource("transitionMatrix")
    void validateTransitionFollowsTheMatrix(OrderStatus from, OrderStatus to, boolean expectedValid) {
        if (expectedValid) {
            assertThatCode(() -> stateMachine.validateTransition(from, to)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessage("Cannot transition from " + from + " to " + to);
        }
    }

    static Stream<Arguments> transitionMatrix() {
        Map<OrderStatus, Set<OrderStatus>> valid = Map.of(
                OrderStatus.RECEBIDO, Set.of(OrderStatus.EM_PREPARO, OrderStatus.CANCELADO),
                OrderStatus.EM_PREPARO, Set.of(OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.CANCELADO),
                OrderStatus.SAIU_PARA_ENTREGA, Set.of(OrderStatus.ENTREGUE),
                OrderStatus.ENTREGUE, Set.of(),
                OrderStatus.CANCELADO, Set.of());
        return Arrays.stream(OrderStatus.values())
                .flatMap(from -> Arrays.stream(OrderStatus.values())
                        .map(to -> Arguments.of(from, to, valid.get(from).contains(to))));
    }
}
