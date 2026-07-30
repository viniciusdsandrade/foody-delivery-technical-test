package com.foody.tracker.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.foody.tracker.entity.Address;
import com.foody.tracker.entity.Order;
import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.entity.OrderStatusHistory;
import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class OrderStatusHistoryRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-07-30T12:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    private User admin;
    private Order order;
    private Order otherOrder;

    @BeforeEach
    void setUp() {
        var client = entityManager.persistAndFlush(new User("Ana Souza", "ana@example.com", "hash-ana", Role.CLIENT));
        admin = entityManager.persistAndFlush(new User("Admin", "admin@foody.com", "hash-admin", Role.ADMIN));
        order = persistOrder(client);
        otherOrder = persistOrder(client);
    }

    @Test
    void findByOrderIdOrderByChangedAtAscIdAscSortsTimelineRegardlessOfInsertionOrder() {
        persistHistory(order, OrderStatus.EM_PREPARO, OrderStatus.SAIU_PARA_ENTREGA, BASE.plus(20, ChronoUnit.MINUTES));
        persistHistory(order, null, OrderStatus.RECEBIDO, BASE);
        persistHistory(order, OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO, BASE.plus(10, ChronoUnit.MINUTES));
        entityManager.clear();

        var timeline = historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.getId());

        assertThat(timeline)
                .extracting(OrderStatusHistory::getFromStatus, OrderStatusHistory::getToStatus)
                .containsExactly(
                        tuple(null, OrderStatus.RECEBIDO),
                        tuple(OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO),
                        tuple(OrderStatus.EM_PREPARO, OrderStatus.SAIU_PARA_ENTREGA));
        assertThat(timeline).extracting(OrderStatusHistory::getChangedAt).isSorted();
        assertThat(timeline).allSatisfy(event -> {
            assertThat(event.getId()).isNotNull();
            assertThat(event.getOrder().getId()).isEqualTo(order.getId());
            assertThat(event.getChangedBy().getId()).isEqualTo(admin.getId());
        });
    }

    @Test
    void findByOrderIdOrderByChangedAtAscIdAscDoesNotLeakEventsFromOtherOrders() {
        persistHistory(order, null, OrderStatus.RECEBIDO, BASE);
        persistHistory(otherOrder, null, OrderStatus.RECEBIDO, BASE.plus(1, ChronoUnit.MINUTES));
        persistHistory(otherOrder, OrderStatus.RECEBIDO, OrderStatus.CANCELADO, BASE.plus(2, ChronoUnit.MINUTES));
        entityManager.clear();

        assertThat(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.getId())).hasSize(1);
        assertThat(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(otherOrder.getId())).hasSize(2);
    }

    @Test
    void findByOrderIdOrderByChangedAtAscIdAscBreaksTiesByInsertionId() {
        persistHistory(order, null, OrderStatus.RECEBIDO, BASE);
        persistHistory(order, OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO, BASE);
        persistHistory(order, OrderStatus.EM_PREPARO, OrderStatus.SAIU_PARA_ENTREGA, BASE);
        entityManager.clear();

        assertThat(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.getId()))
                .extracting(OrderStatusHistory::getToStatus)
                .containsExactly(OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO, OrderStatus.SAIU_PARA_ENTREGA);
    }

    @Test
    void findByOrderIdOrderByChangedAtAscIdAscReturnsEmptyForOrderWithoutHistory() {
        assertThat(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.getId())).isEmpty();
    }

    @Test
    void changedAtDefaultsToNowTruncatedToMicrosWhenNotProvided() {
        var before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        var event = entityManager.persistAndFlush(
                new OrderStatusHistory(order, null, OrderStatus.RECEBIDO, admin));
        var inMemory = event.getChangedAt();
        entityManager.clear();

        assertThat(inMemory).isNotNull().isBetween(before, Instant.now());
        assertThat(inMemory.getNano() % 1_000).isZero();
        assertThat(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.getId()))
                .singleElement()
                .extracting(OrderStatusHistory::getChangedAt)
                .isEqualTo(inMemory);
    }

    @Test
    void changedAtIsKeptWhenExplicitlyProvided() {
        var event = new OrderStatusHistory(order, null, OrderStatus.RECEBIDO, admin);
        event.setChangedAt(BASE);

        entityManager.persistAndFlush(event);
        entityManager.clear();

        assertThat(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.getId()))
                .singleElement()
                .extracting(OrderStatusHistory::getChangedAt)
                .isEqualTo(BASE);
    }

    private Order persistOrder(User owner) {
        return entityManager.persistAndFlush(new Order(
                owner.getName(),
                new Address("Rua das Flores", "123", null, "Centro", "São Paulo", "SP", "01001-000"),
                OrderStatus.RECEBIDO,
                new BigDecimal("59.80"),
                owner));
    }

    private void persistHistory(Order target, OrderStatus from, OrderStatus to, Instant changedAt) {
        var event = new OrderStatusHistory(target, from, to, admin);
        event.setChangedAt(changedAt);
        entityManager.persistAndFlush(event);
    }
}
