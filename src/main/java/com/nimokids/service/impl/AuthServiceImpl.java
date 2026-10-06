package com.nimokids.service.impl;

import com.nimokids.dto.request.LoginRequest;
import com.nimokids.dto.response.LoginResponse;
import com.nimokids.entity.AdminUser;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.repository.AdminUserRepository;
import com.nimokids.security.JwtService;
import com.nimokids.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Compared against when the account does not exist, so unknown emails cost the same time as wrong passwords. */
    private final String dummyHash;

    public AuthServiceImpl(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AdminUser admin = adminUserRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(AdminUser::isActive)
                .orElse(null);

        boolean passwordMatches = passwordEncoder.matches(request.password(), admin != null ? admin.getPasswordHash() : dummyHash);
        if (admin == null || !passwordMatches) {
            log.warn("Failed login attempt");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }

        // The role goes into the token payload; the filter turns it into ROLE_<role> on every admin request.
        String token = jwtService.generateToken(admin.getId().toString(), admin.getRole().name());
        log.info("Admin {} logged in with role {}", admin.getId(), admin.getRole());
        return new LoginResponse(token, "Bearer", jwtService.getExpiresInSeconds(), admin.getEmail(), admin.getRole());
    }
}
