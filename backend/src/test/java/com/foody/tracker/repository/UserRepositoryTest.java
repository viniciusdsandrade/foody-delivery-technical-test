package com.foody.tracker.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        entityManager.persistAndFlush(new User("Ana Souza", "ana@example.com", "hash-ana", Role.CLIENT));
    }

    @Test
    void findByEmailReturnsPersistedUser() {
        var found = userRepository.findByEmail("ana@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Ana Souza");
        assertThat(found.get().getRole()).isEqualTo(Role.CLIENT);
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void findByEmailReturnsEmptyWhenEmailIsUnknown() {
        assertThat(userRepository.findByEmail("unknown@example.com")).isEmpty();
    }

    @Test
    void existsByEmailReturnsTrueForPersistedEmail() {
        assertThat(userRepository.existsByEmail("ana@example.com")).isTrue();
    }

    @Test
    void existsByEmailReturnsFalseForUnknownEmail() {
        assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
    }

    @Test
    void emailIsUnique() {
        var duplicate = new User("Ana Clone", "ana@example.com", "hash-clone", Role.ADMIN);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
