package com.neirah.approveflow.auth;

import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.user.User;
import java.util.Set;

public record UserResponse(Long id, String name, String email, Set<Role> roles) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRoles());
    }
}