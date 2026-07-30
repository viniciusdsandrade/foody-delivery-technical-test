package com.foody.tracker.controller;

import com.foody.tracker.dto.OrderRequest;
import com.foody.tracker.dto.OrderResponse;
import com.foody.tracker.entity.OrderStatus;
import com.foody.tracker.security.AuthenticatedUser;
import com.foody.tracker.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public OrderResponse create(@Valid @RequestBody OrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return orderService.create(request, currentUser);
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return orderService.list(currentUser, status);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return orderService.findById(id, currentUser);
    }
}
