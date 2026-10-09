package com.nimokids.config;

import com.nimokids.entity.AdminUser;
import com.nimokids.entity.enums.AdminRole;
import com.nimokids.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first SUPER_ADMIN of a database that has none, from two environment variables:
 *
 * <pre>
 *   ADMIN_BOOTSTRAP_EMAIL=...
 *   ADMIN_BOOTSTRAP_PASSWORD=...   (at least 12 characters)
 * </pre>
 *
 * Runs once in practice: as soon as a SUPER_ADMIN exists the variables are ignored, so a restart (or a leftover
 * variable) can never reset an existing account or create a second one. Remove both variables after the first start.
 * The password is never logged and only its bcrypt hash is stored. Nothing happens when the variables are not set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_BOOTSTRAP_EMAIL:}")
    private String email;

    @Value("${ADMIN_BOOTSTRAP_PASSWORD:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("Admin bootstrap skipped: set both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD");
            return;
        }
        if (adminUserRepository.existsByRole(AdminRole.SUPER_ADMIN)) {
            log.info("Admin bootstrap skipped: a SUPER_ADMIN already exists (remove the ADMIN_BOOTSTRAP_* variables)");
            return;
        }
        String normalizedEmail = email.strip().toLowerCase();
        if (!normalizedEmail.contains("@") || password.length() < MIN_PASSWORD_LENGTH) {
            log.error("Admin bootstrap refused: the email must be valid and the password at least {} characters",
                    MIN_PASSWORD_LENGTH);
            return;
        }
        if (adminUserRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            log.warn("Admin bootstrap skipped: that email already belongs to a non-SUPER_ADMIN account");
            return;
        }
        adminUserRepository.save(AdminUser.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .role(AdminRole.SUPER_ADMIN)
                .build());
        log.info("Admin bootstrap: SUPER_ADMIN created. Remove ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD now.");
    }
}
