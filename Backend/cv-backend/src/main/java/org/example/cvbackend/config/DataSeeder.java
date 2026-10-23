package org.example.cvbackend.config;

import org.example.cvbackend.model.Role;
import org.example.cvbackend.model.User;
import org.example.cvbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedUsers(UserRepository users, PasswordEncoder encoder,
                                @Value("${app.seed.demo-users:false}") boolean enabled) {
        return args -> {
            if (!enabled) return;
            seed(users, encoder, "Demo Student", "student@campus.edu", "CS24M001", "Computer Science", "Student@123", Role.STUDENT);
            seed(users, encoder, "Campus Admin", "admin@campus.edu", "ADM0001", "Administration", "Admin@123", Role.ADMIN);
        };
    }

    private void seed(UserRepository users, PasswordEncoder encoder, String name, String email,
                      String studentId, String dept, String password, Role role) {
        if (users.existsByEmail(email)) return;
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setStudentId(studentId);
        u.setDepartment(dept);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        users.save(u);
    }
}