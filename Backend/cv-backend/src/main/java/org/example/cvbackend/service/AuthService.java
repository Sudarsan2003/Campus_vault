package org.example.cvbackend.service;

import org.example.cvbackend.dto.*;
import org.example.cvbackend.exception.ApiException;
import org.example.cvbackend.model.Role;
import org.example.cvbackend.model.User;
import org.example.cvbackend.repository.UserRepository;
import org.example.cvbackend.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public RegisterResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        String studentId = req.studentId().trim().toUpperCase();

        Map<String, String> conflicts = new LinkedHashMap<>();
        if (users.existsByEmail(email)) conflicts.put("email", "An account with this email already exists");
        if (users.existsByStudentId(studentId)) conflicts.put("studentId", "This ID is already registered");
        if (!conflicts.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Account already exists.", conflicts);
        }

        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setStudentId(studentId);
        user.setDepartment(req.department());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.STUDENT); // admins are never self-registered
        users.save(user);

        return new RegisterResponse("Account created.", UserResponse.from(user));
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        return new AuthResponse(jwt.generate(user), UserResponse.from(user));
    }
}