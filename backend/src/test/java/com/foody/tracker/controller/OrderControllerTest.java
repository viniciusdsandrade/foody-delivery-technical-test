package com.foody.tracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foody.tracker.dto.AddressDto;
import com.foody.tracker.dto.OrderItemResponse;
import com.foody.tracker.dto.OrderResponse;
import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import com.foody.tracker.exception.OrderNotFoundException;
import com.foody.tracker.security.AuthenticatedUser;
import com.foody.tracker.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String VALID_BODY = """
            {
              "customerName": "Ana Souza",
              "address": {
                "street": "Rua das Flores", "number": "123", "complement": "ap 12",
                "district": "Centro", "city": "São Paulo", "state": "SP", "zipCode": "01001-000"
              },
              "items": [
                {"name": "Pizza Margherita", "quantity": 1, "unitPrice": 49.90},
                {"name": "Refrigerante", "quantity": 2, "unitPrice": 4.95}
              ]
            }""";

    // The @WebMvcTest slice does not register Spring Security's
    // AuthenticationPrincipalArgumentResolver, so @AuthenticationPrincipal
    // parameters would fall back to model-attribute binding.
    @TestConfiguration
    static class SecurityArgumentResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createReturnsCreatedOrderForClient() throws Exception {
        when(orderService.create(any(), any())).thenReturn(orderResponse());

        mockMvc.perform(post("/orders")
                        .with(authentication(principal(1L, Role.CLIENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerName").value("Ana Souza"))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.total").value(59.80))
                .andExpect(jsonPath("$.address.zipCode").value("01001-000"))
                .andExpect(jsonPath("$.items[0].name").value("Pizza Margherita"))
                .andExpect(jsonPath("$.items[1].quantity").value(2))
                .andExpect(jsonPath("$.createdAt").value("2026-07-30T12:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-30T12:00:00Z"));
    }

    @Test
    void createReturnsFieldErrorsForInvalidBody() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(authentication(principal(1L, Role.CLIENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "",
                                  "address": {"street": "", "number": "", "district": "", "city": "", "state": "", "zipCode": ""},
                                  "items": [{"name": "", "quantity": 0, "unitPrice": 0}]
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/orders"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(Matchers.hasItems(
                        "customerName", "address.street", "address.zipCode",
                        "items[0].name", "items[0].quantity", "items[0].unitPrice")));
    }

    @Test
    void createReturnsBadRequestForEmptyItems() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(authentication(principal(1L, Role.CLIENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "Ana Souza",
                                  "address": {
                                    "street": "Rua das Flores", "number": "123",
                                    "district": "Centro", "city": "São Paulo", "state": "SP", "zipCode": "01001-000"
                                  },
                                  "items": []
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(Matchers.hasItem("items")));
    }

    @Test
    void listReturnsOrders() throws Exception {
        when(orderService.list(any(), any())).thenReturn(List.of(orderResponse()));

        mockMvc.perform(get("/orders").with(authentication(principal(1L, Role.CLIENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("RECEBIDO"));
    }

    @Test
    void listPassesStatusFilterToService() throws Exception {
        when(orderService.list(any(), eq(OrderStatus.EM_PREPARO))).thenReturn(List.of());

        mockMvc.perform(get("/orders")
                        .param("status", "EM_PREPARO")
                        .with(authentication(principal(99L, Role.ADMIN))))
                .andExpect(status().isOk());

        verify(orderService).list(any(), eq(OrderStatus.EM_PREPARO));
    }

    @Test
    void listReturnsBadRequestForInvalidStatus() throws Exception {
        mockMvc.perform(get("/orders")
                        .param("status", "BOGUS")
                        .with(authentication(principal(1L, Role.CLIENT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid value 'BOGUS' for parameter 'status'"))
                .andExpect(jsonPath("$.path").value("/orders"));
    }

    @Test
    void findByIdReturnsOrder() throws Exception {
        when(orderService.findById(eq(1L), any())).thenReturn(orderResponse());

        mockMvc.perform(get("/orders/1").with(authentication(principal(1L, Role.CLIENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.total").value(59.80));
    }

    @Test
    void findByIdReturnsNotFound() throws Exception {
        when(orderService.findById(eq(99L), any())).thenThrow(new OrderNotFoundException());

        mockMvc.perform(get("/orders/99").with(authentication(principal(1L, Role.CLIENT))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found"))
                .andExpect(jsonPath("$.path").value("/orders/99"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(1L, "Ana Souza", OrderStatus.RECEBIDO, new BigDecimal("59.80"),
                new AddressDto("Rua das Flores", "123", "ap 12", "Centro", "São Paulo", "SP", "01001-000"),
                List.of(new OrderItemResponse(1L, "Pizza Margherita", 1, new BigDecimal("49.90")),
                        new OrderItemResponse(2L, "Refrigerante", 2, new BigDecimal("4.95"))),
                Instant.parse("2026-07-30T12:00:00Z"), Instant.parse("2026-07-30T12:00:00Z"));
    }

    private UsernamePasswordAuthenticationToken principal(Long id, Role role) {
        User user = new User("Ana Souza", "ana@example.com", "hashed", role);
        ReflectionTestUtils.setField(user, "id", id);
        AuthenticatedUser principal = new AuthenticatedUser(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
