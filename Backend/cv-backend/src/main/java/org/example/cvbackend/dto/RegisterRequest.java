package org.example.cvbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2 to 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Student or staff ID is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{4,20}$", message = "Use 4 to 20 letters, numbers or hyphens")
        String studentId,

        @NotBlank(message = "Department is required")
        String department,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$",
                message = "Password needs 8+ characters with upper, lower, a number and a symbol")
        String password
) {}