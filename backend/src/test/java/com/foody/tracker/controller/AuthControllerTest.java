package com.foody.tracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foody.tracker.dto.LoginRequest;
import com.foody.tracker.dto.LoginResponse;
import com.foody.tracker.dto.RegisterRequest;
import com.foody.tracker.dto.UserResponse;
import com.foody.tracker.entity.Role;
import com.foody.tracker.exception.EmailAlreadyUsedException;
import com.foody.tracker.exception.InvalidCredentialsException;
import com.foody.tracker.service.AuthService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerReturnsCreatedUser() throws Exception {
        when(authService.register(new RegisterRequest("Ana Souza", "ana@example.com", "secret1")))
                .thenReturn(new UserResponse(1L, "Ana Souza", "ana@example.com", Role.CLIENT));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Souza","email":"ana@example.com","password":"secret1"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ana Souza"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    void registerReturnsFieldErrorsForInvalidBody() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"123"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(Matchers.containsInAnyOrder("name", "email", "password")));
    }

    @Test
    void registerRejectsPasswordAboveSeventyTwoBytes() throws Exception {
        // 40 chars, 80 bytes in UTF-8: passes @Size, must fail @MaxBytes —
        // BCrypt would throw on encode and turn this into a 500 otherwise.
        String multiBytePassword = "ç".repeat(40);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Souza","email":"ana@example.com","password":"%s"}"""
                                .formatted(multiBytePassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(Matchers.hasItem("password")));
    }

    @Test
    void wrongHttpMethodReturnsMethodNotAllowedContract() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value("Method not allowed"))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void registerReturnsConflictForDuplicatedEmail() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyUsedException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Souza","email":"ana@example.com","password":"secret1"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already in use"))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login(new LoginRequest("ana@example.com", "secret1")))
                .thenReturn(new LoginResponse("jwt-token", 7200L,
                        new UserResponse(1L, "Ana Souza", "ana@example.com", Role.CLIENT)));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","password":"secret1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.role").value("CLIENT"));
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
}
