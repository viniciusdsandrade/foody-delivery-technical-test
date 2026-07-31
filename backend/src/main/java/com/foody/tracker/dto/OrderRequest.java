package com.foody.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderRequest(
        @NotBlank @Size(max = 120) String customerName,
        @NotNull @Valid AddressDto address,
        @NotEmpty @Size(max = 100) List<@Valid OrderItemRequest> items) {
}
