package com.foody.tracker.dto;

import com.foody.tracker.entity.Role;
import com.foody.tracker.entity.User;

public record UserResponse(Long id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
