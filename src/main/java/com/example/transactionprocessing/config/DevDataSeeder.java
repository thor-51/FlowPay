package com.example.transactionprocessing.config;

import com.example.transactionprocessing.user.entity.Role;
import com.example.transactionprocessing.user.entity.User;
import com.example.transactionprocessing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single ADMIN user on startup when running under the "dev" profile, so the admin
 * endpoints (Part 5) and Swagger UI are exercisable immediately without a manual SQL insert.
 * Guarded by app.seed.enabled in addition to @Profile("dev") as a belt-and-braces measure:
 * a misconfigured environment that accidentally activates the dev profile in something
 * resembling production still needs an explicit opt-in flag to create this account.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Order(1)
@Slf4j
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.seed.admin-email:admin@tps.local}")
    private String adminEmail;

    @Value("${app.seed.admin-password:Admin@12345}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.debug("app.seed.enabled=false, skipping dev admin seed");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Dev seed admin already present ({}), skipping", adminEmail);
            return;
        }

        User admin = User.builder()
                .name("Development Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
        log.warn(
                "Seeded development admin user email={} password={} -- DEV PROFILE ONLY, never enable app.seed.enabled in production",
                adminEmail,
                adminPassword);
    }
}
