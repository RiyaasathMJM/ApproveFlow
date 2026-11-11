package com.neirah.approveflow.seed;

import com.neirah.approveflow.user.Role;
import com.neirah.approveflow.user.User;
import com.neirah.approveflow.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;

@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      @Value("${app.seed.password}") String seedPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(String... args) {
        seed("Admin", "admin@demo.local", Role.ADMIN);
        seed("Requester", "requester@demo.local", Role.REQUESTER);
        seed("Manager", "manager@demo.local", Role.MANAGER);
        seed("Finance", "finance@demo.local", Role.FINANCE);
        seed("Manager Two", "manager2@demo.local", Role.MANAGER);   // negative demo: unassigned approver
    }

    private void seed(String name, String email, Role... roles) {
        if (userRepository.existsByEmail(email)) return;   // idempotent
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(seedPassword));
        user.setRoles(new HashSet<>(Arrays.asList(roles)));
        userRepository.save(user);
    }
}