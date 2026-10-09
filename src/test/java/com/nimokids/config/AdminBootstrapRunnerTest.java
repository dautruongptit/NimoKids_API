package com.nimokids.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimokids.entity.AdminUser;
import com.nimokids.entity.enums.AdminRole;
import com.nimokids.repository.AdminUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminBootstrapRunnerTest {

    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        runner = new AdminBootstrapRunner(repository, encoder);
    }

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(runner, "email", email);
        ReflectionTestUtils.setField(runner, "password", password);
    }

    @Test
    void doesNothingWhenTheVariablesAreNotSet() {
        configure("", "");
        runner.run(null);
        verify(repository, never()).save(any());
    }

    @Test
    void createsTheFirstSuperAdminWithAHashedPassword() {
        configure("Owner@Example.com", "a-long-secret-123");
        when(repository.existsByRole(AdminRole.SUPER_ADMIN)).thenReturn(false);
        when(repository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.empty());

        runner.run(null);

        ArgumentCaptor<AdminUser> saved = ArgumentCaptor.forClass(AdminUser.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("owner@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("a-long-secret-123");
        assertThat(encoder.matches("a-long-secret-123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void neverCreatesASecondSuperAdminOrResetsOne() {
        configure("owner@example.com", "a-long-secret-123");
        when(repository.existsByRole(AdminRole.SUPER_ADMIN)).thenReturn(true);

        runner.run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void refusesAShortPassword() {
        configure("owner@example.com", "short");
        when(repository.existsByRole(AdminRole.SUPER_ADMIN)).thenReturn(false);

        runner.run(null);

        verify(repository, never()).save(any());
    }
}
