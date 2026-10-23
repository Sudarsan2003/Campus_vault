package org.example.cvbackend.dto;

public record AuthResponse(String token, UserResponse user) {}