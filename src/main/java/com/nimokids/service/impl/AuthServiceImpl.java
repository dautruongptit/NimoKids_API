package com.nimokids.service.impl;

import com.nimokids.dto.request.LoginRequest;
import com.nimokids.dto.response.LoginResponse;
import com.nimokids.entity.AppUser;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.repository.AppUserRepository;
import com.nimokids.security.JwtService;
import com.nimokids.service.AuthService;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    /** Compared against when the user does not exist, so unknown users cost the same time as wrong passwords. */
    private final String dummyHash;

    public AuthServiceImpl(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(AppUser::isActive)
                .orElse(null);

        boolean passwordMatches = passwordEncoder.matches(
                request.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !passwordMatches) {
            log.warn("Failed login attempt");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, BAD_CREDENTIALS);
        }

        user.setLastLoginAt(clock.instant());
        String token = jwtService.generateToken(user.getId().toString(), user.getRole().name());
        log.info("User {} logged in with role {}", user.getUsername(), user.getRole());
        return new LoginResponse(token, "Bearer", jwtService.getExpiresInSeconds(), user.getUsername(), user.getRole());
    }
}
