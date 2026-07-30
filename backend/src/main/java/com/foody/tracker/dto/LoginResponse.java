package com.foody.tracker.dto;

public record LoginResponse(String token, long expiresIn, UserResponse user) {
}
