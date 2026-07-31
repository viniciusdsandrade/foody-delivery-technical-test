package com.foody.tracker.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(1) @Max(1000) Integer quantity,
        @NotNull @Positive @Digits(integer = 5, fraction = 2) BigDecimal unitPrice) {
}
