package com.foody.tracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foody.tracker.dto.AddressDto;
import com.foody.tracker.dto.OrderItemRequest;
import com.foody.tracker.dto.OrderRequest;
import com.foody.tracker.dto.OrderResponse;
import com.foody.tracker.dto.StatusHistoryEntry;
import com.foody.tracker.entity.Order;
import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.entity.OrderStatusHistory;
import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import com.foody.tracker.exception.InvalidStatusTransitionException;
import com.foody.tracker.exception.OrderNotFoundException;
import com.foody.tracker.repository.OrderRepository;
import com.foody.tracker.repository.OrderStatusHistoryRepository;
import com.foody.tracker.repository.UserRepository;
import com.foody.tracker.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, historyRepository, userRepository, new OrderStateMachine());
    }

    @Test
    void createPersistsOrderWithServerCalculatedTotalAndInitialHistory() {
        User user = user(1L, Role.CLIENT);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        OrderResponse response = orderService.create(orderRequest(), new AuthenticatedUser(user));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order persisted = orderCaptor.getValue();
        assertThat(persisted.getCustomerName()).isEqualTo("Ana Souza");
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(persisted.getTotal()).isEqualByComparingTo("59.80");
        assertThat(persisted.getUser()).isSameAs(user);
        assertThat(persisted.getItems()).extracting("name")
                .containsExactly("Pizza Margherita", "Refrigerante");

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        OrderStatusHistory history = historyCaptor.getValue();
        assertThat(history.getOrder()).isSameAs(persisted);
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(history.getChangedBy()).isSameAs(user);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.total()).isEqualByComparingTo("59.80");
        assertThat(response.status()).isEqualTo(OrderStatus.RECEBIDO);
    }

    @Test
    void createNormalizesTotalToScaleTwo() {
        User user = user(1L, Role.CLIENT);
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRequest request = new OrderRequest("Ana Souza", addressDto(),
                List.of(new OrderItemRequest("Pizza Margherita", 3, new BigDecimal("10"))));

        OrderResponse response = orderService.create(request, new AuthenticatedUser(user));

        assertThat(response.total()).isEqualTo(new BigDecimal("30.00"));
        assertThat(response.total().scale()).isEqualTo(2);
    }

    @Test
    void listReturnsOwnOrdersForClient() {
        Order order = orderOwnedBy(user(1L, Role.CLIENT));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(order));

        List<OrderResponse> result = orderService.list(principal(1L, Role.CLIENT), null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().customerName()).isEqualTo("Ana Souza");
    }

    @Test
    void listFiltersOwnOrdersByStatusForClient() {
        when(orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(1L, OrderStatus.EM_PREPARO))
                .thenReturn(List.of());

        List<OrderResponse> result = orderService.list(principal(1L, Role.CLIENT), OrderStatus.EM_PREPARO);

        assertThat(result).isEmpty();
        verify(orderRepository).findByUserIdAndStatusOrderByCreatedAtDesc(1L, OrderStatus.EM_PREPARO);
    }

    @Test
    void listReturnsAllOrdersForAdmin() {
        when(orderRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(orderOwnedBy(user(1L, Role.CLIENT)), orderOwnedBy(user(2L, Role.CLIENT))));

        List<OrderResponse> result = orderService.list(principal(99L, Role.ADMIN), null);

        assertThat(result).hasSize(2);
    }

    @Test
    void listFiltersAllOrdersByStatusForAdmin() {
        when(orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.ENTREGUE))
                .thenReturn(List.of(orderOwnedBy(user(1L, Role.CLIENT))));

        List<OrderResponse> result = orderService.list(principal(99L, Role.ADMIN), OrderStatus.ENTREGUE);

        assertThat(result).hasSize(1);
        verify(orderRepository).findByStatusOrderByCreatedAtDesc(OrderStatus.ENTREGUE);
    }

    @Test
    void findByIdReturnsOrderForOwner() {
        Order order = orderOwnedBy(user(1L, Role.CLIENT));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.findById(10L, principal(1L, Role.CLIENT));

        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void findByIdReturnsOrderOfOtherUserForAdmin() {
        Order order = orderOwnedBy(user(1L, Role.CLIENT));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.findById(10L, principal(99L, Role.ADMIN));

        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void findByIdThrowsNotFoundWhenClientDoesNotOwnOrder() {
        Order order = orderOwnedBy(user(2L, Role.CLIENT));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findById(10L, principal(1L, Role.CLIENT)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void findByIdThrowsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(10L, principal(99L, Role.ADMIN)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void updateStatusAppliesValidTransitionAndRecordsHistory() {
        User admin = user(99L, Role.ADMIN);
        Order order = orderOwnedBy(user(1L, Role.CLIENT));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(userRepository.getReferenceById(99L)).thenReturn(admin);

        OrderResponse response = orderService.updateStatus(10L, OrderStatus.EM_PREPARO, new AuthenticatedUser(admin));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EM_PREPARO);
        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        OrderStatusHistory history = captor.getValue();
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getFromStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(history.getToStatus()).isEqualTo(OrderStatus.EM_PREPARO);
        assertThat(history.getChangedBy()).isSameAs(admin);
        assertThat(response.status()).isEqualTo(OrderStatus.EM_PREPARO);
    }

    @Test
    void updateStatusRejectsInvalidTransition() {
        Order order = orderOwnedBy(user(1L, Role.CLIENT));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(10L, OrderStatus.ENTREGUE, principal(99L, Role.ADMIN)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Cannot transition from RECEBIDO to ENTREGUE");
        verify(historyRepository, never()).save(any());
    }

    @Test
    void updateStatusThrowsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(10L, OrderStatus.EM_PREPARO, principal(99L, Role.ADMIN)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void findHistoryReturnsTimelineForOwner() {
        User owner = user(1L, Role.CLIENT);
        User admin = user(99L, Role.ADMIN);
        Order order = orderOwnedBy(owner);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        OrderStatusHistory created = historyEntry(order, null, OrderStatus.RECEBIDO, owner, "2026-07-30T12:00:00Z");
        OrderStatusHistory progressed = historyEntry(order, OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO, admin,
                "2026-07-30T12:05:00Z");
        when(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(10L)).thenReturn(List.of(created, progressed));

        List<StatusHistoryEntry> history = orderService.findHistory(10L, principal(1L, Role.CLIENT));

        assertThat(history).hasSize(2);
        assertThat(history.get(0).fromStatus()).isNull();
        assertThat(history.get(0).toStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(history.get(0).changedBy().name()).isEqualTo("Ana Souza");
        assertThat(history.get(0).changedAt()).isEqualTo(Instant.parse("2026-07-30T12:00:00Z"));
        assertThat(history.get(1).fromStatus()).isEqualTo(OrderStatus.RECEBIDO);
        assertThat(history.get(1).toStatus()).isEqualTo(OrderStatus.EM_PREPARO);
    }

    @Test
    void findHistoryReturnsTimelineOfOtherUsersOrderForAdmin() {
        Order order = orderOwnedBy(user(1L, Role.CLIENT));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(10L)).thenReturn(List.of());

        assertThat(orderService.findHistory(10L, principal(99L, Role.ADMIN))).isEmpty();
        verify(historyRepository).findByOrderIdOrderByChangedAtAscIdAsc(10L);
    }

    @Test
    void findHistoryThrowsNotFoundWhenClientDoesNotOwnOrder() {
        Order order = orderOwnedBy(user(2L, Role.CLIENT));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findHistory(10L, principal(1L, Role.CLIENT)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found");
        verify(historyRepository, never()).findByOrderIdOrderByChangedAtAscIdAsc(any());
    }

    @Test
    void findHistoryThrowsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findHistory(10L, principal(99L, Role.ADMIN)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found");
    }

    private OrderStatusHistory historyEntry(Order order, OrderStatus from, OrderStatus to, User changedBy,
            String changedAt) {
        OrderStatusHistory entry = new OrderStatusHistory(order, from, to, changedBy);
        entry.setChangedAt(Instant.parse(changedAt));
        return entry;
    }

    private OrderRequest orderRequest() {
        return new OrderRequest("Ana Souza", addressDto(), List.of(
                new OrderItemRequest("Pizza Margherita", 1, new BigDecimal("49.90")),
                new OrderItemRequest("Refrigerante", 2, new BigDecimal("4.95"))));
    }

    private AddressDto addressDto() {
        return new AddressDto("Rua das Flores", "123", "ap 12", "Centro", "São Paulo", "SP", "01001-000");
    }

    private Order orderOwnedBy(User owner) {
        Order order = new Order("Ana Souza", addressDto().toAddress(), OrderStatus.RECEBIDO,
                new BigDecimal("59.80"), owner);
        ReflectionTestUtils.setField(order, "id", 10L);
        return order;
    }

    private AuthenticatedUser principal(Long id, Role role) {
        return new AuthenticatedUser(user(id, role));
    }

    private User user(Long id, Role role) {
        User user = new User("Ana Souza", "ana@example.com", "hashed", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
