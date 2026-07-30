package com.foody.tracker.service;

import com.foody.tracker.dto.OrderItemRequest;
import com.foody.tracker.dto.OrderRequest;
import com.foody.tracker.dto.OrderResponse;
import com.foody.tracker.dto.StatusHistoryEntry;
import com.foody.tracker.entity.Order;
import com.foody.tracker.entity.OrderItem;
import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.entity.OrderStatusHistory;
import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import com.foody.tracker.exception.OrderNotFoundException;
import com.foody.tracker.repository.OrderRepository;
import com.foody.tracker.repository.OrderStatusHistoryRepository;
import com.foody.tracker.repository.UserRepository;
import com.foody.tracker.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final OrderStateMachine stateMachine;

    public OrderService(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
            UserRepository userRepository, OrderStateMachine stateMachine) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public OrderResponse create(OrderRequest request, AuthenticatedUser currentUser) {
        User user = userRepository.getReferenceById(currentUser.getId());
        Order order = new Order(request.customerName(), request.address().toAddress(),
                OrderStatus.RECEBIDO, calculateTotal(request.items()), user);
        request.items().forEach(item -> order.addItem(new OrderItem(item.name(), item.quantity(), item.unitPrice())));
        Order saved = orderRepository.save(order);
        historyRepository.save(new OrderStatusHistory(saved, null, OrderStatus.RECEBIDO, user));
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(AuthenticatedUser currentUser, OrderStatus status) {
        List<Order> orders;
        if (currentUser.getRole() == Role.ADMIN) {
            orders = status == null
                    ? orderRepository.findAllByOrderByCreatedAtDesc()
                    : orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = status == null
                    ? orderRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                    : orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status);
        }
        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id, AuthenticatedUser currentUser) {
        Order order = findVisibleOrder(id, currentUser);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus newStatus, AuthenticatedUser currentUser) {
        Order order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
        OrderStatus previous = order.getStatus();
        stateMachine.validateTransition(previous, newStatus);
        order.setStatus(newStatus);
        User changedBy = userRepository.getReferenceById(currentUser.getId());
        historyRepository.save(new OrderStatusHistory(order, previous, newStatus, changedBy));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryEntry> findHistory(Long id, AuthenticatedUser currentUser) {
        findVisibleOrder(id, currentUser);
        return historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(id).stream()
                .map(StatusHistoryEntry::from)
                .toList();
    }

    private Order findVisibleOrder(Long id, AuthenticatedUser currentUser) {
        Order order = orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
        if (currentUser.getRole() != Role.ADMIN && !order.getUser().getId().equals(currentUser.getId())) {
            throw new OrderNotFoundException();
        }
        return order;
    }

    private BigDecimal calculateTotal(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
