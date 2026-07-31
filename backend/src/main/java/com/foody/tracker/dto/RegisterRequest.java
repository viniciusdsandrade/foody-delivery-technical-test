package com.foody.tracker.dto;

import com.foody.tracker.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 180) String email,
        // BCrypt (Spring Security 7) throws on inputs above 72 BYTES, so the
        // limit must be enforced in bytes — @Size counts chars, not bytes.
        @NotBlank @Size(min = 6) @MaxBytes(72) String password) {
}
