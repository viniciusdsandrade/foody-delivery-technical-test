package com.foody.tracker.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.foody.tracker.entity.Order;
import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import com.foody.tracker.repository.OrderRepository;
import com.foody.tracker.repository.OrderStatusHistoryRepository;
import com.foody.tracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SeedDataTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CommandLineRunner seedData;

    @Test
    void seedsDefaultUsersAndOrdersOnEmptyDatabase() {
        User admin = userRepository.findByEmail("admin@foody.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("admin123", admin.getPasswordHash())).isTrue();

        User client = userRepository.findByEmail("client@foody.com").orElseThrow();
        assertThat(client.getRole()).isEqualTo(Role.CLIENT);
        assertThat(passwordEncoder.matches("client123", client.getPasswordHash())).isTrue();

        assertThat(orderRepository.findAll()).extracting(Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.RECEBIDO, OrderStatus.EM_PREPARO, OrderStatus.ENTREGUE);
        assertThat(historyRepository.count()).isEqualTo(7);
    }

    @Test
    void seedIsIdempotentWhenDatabaseIsNotEmpty() throws Exception {
        seedData.run();

        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(orderRepository.count()).isEqualTo(3);
        assertThat(historyRepository.count()).isEqualTo(7);
    }
}
