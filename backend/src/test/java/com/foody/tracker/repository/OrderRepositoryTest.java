package com.foody.tracker.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.foody.tracker.entity.Address;
import com.foody.tracker.entity.Order;
import com.foody.tracker.entity.OrderItem;
import com.foody.tracker.entity.OrderStatus;
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
class OrderRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-07-30T12:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private User ana;
    private User bruno;

    @BeforeEach
    void setUp() {
        ana = entityManager.persistAndFlush(new User("Ana Souza", "ana@example.com", "hash-ana", Role.CLIENT));
        bruno = entityManager.persistAndFlush(new User("Bruno Lima", "bruno@example.com", "hash-bruno", Role.CLIENT));
    }

    @Test
    void findByUserIdOrderByCreatedAtDescReturnsOnlyOwnerOrdersNewestFirst() {
        Long anaOldest = persistOrder(ana, OrderStatus.RECEBIDO, BASE).getId();
        Long brunoOrder = persistOrder(bruno, OrderStatus.RECEBIDO, BASE.plus(1, ChronoUnit.MINUTES)).getId();
        Long anaNewest = persistOrder(ana, OrderStatus.ENTREGUE, BASE.plus(2, ChronoUnit.MINUTES)).getId();
        entityManager.clear();

        var found = orderRepository.findByUserIdOrderByCreatedAtDesc(ana.getId());

        assertThat(found).extracting(Order::getId).containsExactly(anaNewest, anaOldest).doesNotContain(brunoOrder);
    }

    @Test
    void findByUserIdOrderByCreatedAtDescReturnsEmptyForUserWithoutOrders() {
        persistOrder(ana, OrderStatus.RECEBIDO, BASE);
        entityManager.clear();

        assertThat(orderRepository.findByUserIdOrderByCreatedAtDesc(bruno.getId())).isEmpty();
    }

    @Test
    void findByUserIdAndStatusOrderByCreatedAtDescFiltersByOwnerAndStatus() {
        Long anaOldReceived = persistOrder(ana, OrderStatus.RECEBIDO, BASE).getId();
        persistOrder(ana, OrderStatus.EM_PREPARO, BASE.plus(1, ChronoUnit.MINUTES));
        Long anaNewReceived = persistOrder(ana, OrderStatus.RECEBIDO, BASE.plus(2, ChronoUnit.MINUTES)).getId();
        persistOrder(bruno, OrderStatus.RECEBIDO, BASE.plus(3, ChronoUnit.MINUTES));
        entityManager.clear();

        var found = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(ana.getId(), OrderStatus.RECEBIDO);

        assertThat(found).extracting(Order::getId).containsExactly(anaNewReceived, anaOldReceived);
    }

    @Test
    void findAllByOrderByCreatedAtDescReturnsEveryOrderNewestFirst() {
        Long oldest = persistOrder(ana, OrderStatus.RECEBIDO, BASE).getId();
        Long middle = persistOrder(bruno, OrderStatus.EM_PREPARO, BASE.plus(1, ChronoUnit.MINUTES)).getId();
        Long newest = persistOrder(ana, OrderStatus.ENTREGUE, BASE.plus(2, ChronoUnit.MINUTES)).getId();
        entityManager.clear();

        var found = orderRepository.findAllByOrderByCreatedAtDesc();

        assertThat(found).extracting(Order::getId).containsExactly(newest, middle, oldest);
    }

    @Test
    void findByStatusOrderByCreatedAtDescCrossesUsers() {
        Long anaReceived = persistOrder(ana, OrderStatus.RECEBIDO, BASE).getId();
        persistOrder(ana, OrderStatus.CANCELADO, BASE.plus(1, ChronoUnit.MINUTES));
        Long brunoReceived = persistOrder(bruno, OrderStatus.RECEBIDO, BASE.plus(2, ChronoUnit.MINUTES)).getId();
        entityManager.clear();

        var found = orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.RECEBIDO);

        assertThat(found).extracting(Order::getId).containsExactly(brunoReceived, anaReceived);
    }

    @Test
    void addItemCascadesPersistAndKeepsBothSidesInSync() {
        var order = new Order("Ana Souza", address(), OrderStatus.RECEBIDO, new BigDecimal("59.80"), ana);
        var pizza = new OrderItem("Pizza Margherita", 1, new BigDecimal("49.90"));
        var soda = new OrderItem("Refrigerante", 2, new BigDecimal("4.95"));
        order.addItem(pizza);
        order.addItem(soda);

        assertThat(pizza.getOrder()).isSameAs(order);
        entityManager.persistAndFlush(order);
        Long orderId = order.getId();
        entityManager.clear();

        var found = orderRepository.findById(orderId).orElseThrow();

        assertThat(found.getItems())
                .extracting(OrderItem::getName, OrderItem::getQuantity)
                .containsExactly(tuple("Pizza Margherita", 1), tuple("Refrigerante", 2));
        assertThat(found.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getTotal()).isEqualByComparingTo("59.80");
        assertThat(found.getAddress().getStreet()).isEqualTo("Rua das Flores");
        assertThat(found.getAddress().getComplement()).isEqualTo("ap 12");
        assertThat(found.getAddress().getZipCode()).isEqualTo("01001-000");
        assertThat(found.getUser().getId()).isEqualTo(ana.getId());
        assertThat(found.getCustomerName()).isEqualTo("Ana Souza");
        assertThat(found.getStatus()).isEqualTo(OrderStatus.RECEBIDO);
    }

    @Test
    void removingItemFromOrderTriggersOrphanRemoval() {
        var order = new Order("Ana Souza", address(), OrderStatus.RECEBIDO, new BigDecimal("59.80"), ana);
        order.addItem(new OrderItem("Pizza Margherita", 1, new BigDecimal("49.90")));
        order.addItem(new OrderItem("Refrigerante", 2, new BigDecimal("4.95")));
        entityManager.persistAndFlush(order);
        Long orderId = order.getId();
        entityManager.clear();

        var managed = orderRepository.findById(orderId).orElseThrow();
        managed.getItems().removeIf(item -> item.getName().equals("Refrigerante"));
        entityManager.flush();
        entityManager.clear();

        assertThat(countItems(orderId)).isEqualTo(1L);
        assertThat(orderRepository.findById(orderId).orElseThrow().getItems())
                .extracting(OrderItem::getName)
                .containsExactly("Pizza Margherita");
    }

    private Order persistOrder(User user, OrderStatus status, Instant createdAt) {
        var order = new Order(user.getName(), address(), status, new BigDecimal("59.80"), user);
        order.addItem(new OrderItem("Pizza Margherita", 1, new BigDecimal("49.90")));
        entityManager.persistAndFlush(order);
        entityManager.getEntityManager()
                .createQuery("update Order o set o.createdAt = :createdAt where o.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", order.getId())
                .executeUpdate();
        return order;
    }

    private long countItems(Long orderId) {
        return entityManager.getEntityManager()
                .createQuery("select count(i) from OrderItem i where i.order.id = :orderId", Long.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private static Address address() {
        return new Address("Rua das Flores", "123", "ap 12", "Centro", "São Paulo", "SP", "01001-000");
    }
}
