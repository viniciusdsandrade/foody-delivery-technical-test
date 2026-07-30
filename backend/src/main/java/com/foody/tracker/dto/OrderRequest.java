package com.foody.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderRequest(
        @NotBlank String customerName,
        @NotNull @Valid AddressDto address,
        @NotEmpty List<@Valid OrderItemRequest> items) {
}
