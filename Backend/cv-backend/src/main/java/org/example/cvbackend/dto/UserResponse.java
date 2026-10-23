package org.example.cvbackend.dto;

import org.example.cvbackend.model.User;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String studentId,
        String department,
        String role
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(),
                u.getStudentId(), u.getDepartment(), u.getRole().name());
    }
}