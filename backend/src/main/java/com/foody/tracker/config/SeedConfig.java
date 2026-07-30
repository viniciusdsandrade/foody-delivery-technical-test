package com.foody.tracker.config;

import com.foody.tracker.entity.Address;
import com.foody.tracker.entity.Order;
import com.foody.tracker.entity.OrderItem;
import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.entity.OrderStatusHistory;
import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import com.foody.tracker.repository.OrderRepository;
import com.foody.tracker.repository.OrderStatusHistoryRepository;
import com.foody.tracker.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SeedConfig {

    @Bean
    CommandLineRunner seedData(UserRepository userRepository, OrderRepository orderRepository,
            OrderStatusHistoryRepository historyRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }
            User admin = userRepository.save(new User("Admin Foody", "admin@foody.com",
                    passwordEncoder.encode("admin123"), Role.ADMIN));
            User client = userRepository.save(new User("Cliente Foody", "client@foody.com",
                    passwordEncoder.encode("client123"), Role.CLIENT));

            Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);

            Order received = order(client, OrderStatus.RECEBIDO,
                    item("Pizza Margherita", 1, "49.90"), item("Refrigerante", 2, "4.95"));
            persist(orderRepository, historyRepository, received,
                    List.of(entry(received, null, OrderStatus.RECEBIDO, client, base)));

            Order preparing = order(client, OrderStatus.EM_PREPARO,
                    item("Hambúrguer Artesanal", 2, "32.50"));
            persist(orderRepository, historyRepository, preparing,
                    List.of(entry(preparing, null, OrderStatus.RECEBIDO, client, base.minus(2, ChronoUnit.HOURS)),
                            entry(preparing, OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO, admin,
                                    base.minus(90, ChronoUnit.MINUTES))));

            Order delivered = order(client, OrderStatus.ENTREGUE,
                    item("Sushi Combo 20 peças", 1, "89.90"));
            persist(orderRepository, historyRepository, delivered,
                    List.of(entry(delivered, null, OrderStatus.RECEBIDO, client, base.minus(1, ChronoUnit.DAYS)),
                            entry(delivered, OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO, admin,
                                    base.minus(23, ChronoUnit.HOURS)),
                            entry(delivered, OrderStatus.EM_PREPARO, OrderStatus.SAIU_PARA_ENTREGA, admin,
                                    base.minus(22, ChronoUnit.HOURS)),
                            entry(delivered, OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.ENTREGUE, admin,
                                    base.minus(21, ChronoUnit.HOURS))));
        };
    }

    private static Order order(User owner, OrderStatus status, OrderItem... items) {
        BigDecimal total = Arrays.stream(items)
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order(owner.getName(), new Address("Rua das Flores", "123", null, "Centro",
                "São Paulo", "SP", "01001-000"), status, total, owner);
        Arrays.stream(items).forEach(order::addItem);
        return order;
    }

    private static OrderItem item(String name, int quantity, String unitPrice) {
        return new OrderItem(name, quantity, new BigDecimal(unitPrice));
    }

    private static OrderStatusHistory entry(Order order, OrderStatus from, OrderStatus to, User changedBy,
            Instant changedAt) {
        OrderStatusHistory entry = new OrderStatusHistory(order, from, to, changedBy);
        entry.setChangedAt(changedAt);
        return entry;
    }

    private static void persist(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
            Order order, List<OrderStatusHistory> history) {
        orderRepository.save(order);
        historyRepository.saveAll(history);
    }
}
