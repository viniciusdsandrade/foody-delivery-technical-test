package com.foody.tracker.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuthenticatedUserTest {

    @Test
    void wrapsUserExposingDetailsAndRoleAuthority() {
        User user = new User("Ana Souza", "ana@example.com", "hashed", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 7L);

        AuthenticatedUser authenticated = new AuthenticatedUser(user);

        assertThat(authenticated.getId()).isEqualTo(7L);
        assertThat(authenticated.getEmail()).isEqualTo("ana@example.com");
        assertThat(authenticated.getUsername()).isEqualTo("ana@example.com");
        assertThat(authenticated.getPassword()).isEqualTo("hashed");
        assertThat(authenticated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(authenticated.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }
}
