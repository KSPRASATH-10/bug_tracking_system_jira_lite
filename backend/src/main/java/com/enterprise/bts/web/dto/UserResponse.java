package com.enterprise.bts.web.dto;

import com.enterprise.bts.domain.SystemRole;
import com.enterprise.bts.domain.User;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, SystemRole systemRole) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getSystemRole());
    }
}
