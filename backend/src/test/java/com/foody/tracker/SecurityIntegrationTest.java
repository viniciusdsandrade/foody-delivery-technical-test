package com.foody.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;
import com.foody.tracker.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:security-integration-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String VALID_ORDER_BODY = """
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorizedErrorContract() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/orders"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void protectedRouteWithGarbageTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void registeredUserReachesProtectedRouteWithIssuedToken() throws Exception {
        String token = registerAndLogin("ana@example.com");

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCannotCreateOrdersAndGetsForbiddenContract() throws Exception {
        userRepository.save(new User("Admin", "admin@example.com",
                passwordEncoder.encode("admin123"), Role.ADMIN));
        String token = login("admin@example.com", "admin123");

        mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/orders"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void clientCreatesAndReadsOwnOrderEndToEnd() throws Exception {
        String token = registerAndLogin("bia@example.com");

        String created = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.total").value(59.8))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Number orderId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderId.longValue()));

        mockMvc.perform(get("/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ana Souza"));
    }

    @Test
    void clientCannotSeeAnotherClientsOrderNorItsHistory() throws Exception {
        String ownerToken = registerAndLogin("owner@example.com");
        String intruderToken = registerAndLogin("intruder@example.com");

        String created = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number orderId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order not found"));

        mockMvc.perform(get("/orders/" + orderId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void clientCannotUpdateOrderStatusAndGetsForbiddenContract() throws Exception {
        String token = registerAndLogin("carol@example.com");

        mockMvc.perform(patch("/orders/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/orders/1/status"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void adminUpdatesOrderStatusFollowingTheStateMachine() throws Exception {
        String clientToken = registerAndLogin("diego@example.com");
        String created = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number orderId = JsonPath.read(created, "$.id");

        userRepository.save(new User("Admin Two", "admin2@example.com",
                passwordEncoder.encode("admin123"), Role.ADMIN));
        String adminToken = login("admin2@example.com", "admin123");

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PREPARO"));

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENTREGUE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Content"))
                .andExpect(jsonPath("$.message").value("Cannot transition from EM_PREPARO to ENTREGUE"))
                .andExpect(jsonPath("$.path").value("/orders/" + orderId + "/status"));

        mockMvc.perform(get("/orders/" + orderId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fromStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].toStatus").value("RECEBIDO"))
                .andExpect(jsonPath("$[1].fromStatus").value("RECEBIDO"))
                .andExpect(jsonPath("$[1].toStatus").value("EM_PREPARO"))
                .andExpect(jsonPath("$[1].changedBy.name").value("Admin Two"));
    }

    @Test
    void publicRoutesAreReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        int loginStatus = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}"""))
                .andReturn().getResponse().getStatus();

        assertThat(loginStatus).isEqualTo(400);
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Souza","email":"%s","password":"secret1"}""".formatted(email)))
                .andExpect(status().isCreated());
        return login(email, "secret1");
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
