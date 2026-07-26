package com.voltwise.core.auth;

import com.voltwise.core.auth.AuthDtos.AuthResponse;
import com.voltwise.core.auth.AuthDtos.AuthUserResponse;
import com.voltwise.core.auth.AuthDtos.LoginRequest;
import com.voltwise.core.auth.AuthDtos.RegisterRequest;
import com.voltwise.core.persistence.entity.UserEntity;
import com.voltwise.core.persistence.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("voltwise-dummy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }
        return responseFor(user, "Hesabınız oluşturuldu.");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        String storedHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return responseFor(user, "Giriş başarılı.");
    }

    @Transactional
    public void changePassword(String email, AuthDtos.ChangePasswordRequest request) {
        if (email == null || email.isBlank()) {
            throw new InvalidCredentialsException();
        }
        UserEntity user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mevcut şifreniz hatalı.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse getProfile(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidCredentialsException();
        }
        UserEntity user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(InvalidCredentialsException::new);
        return new AuthUserResponse(user.getId(), user.getEmail());
    }

    private AuthResponse responseFor(UserEntity user, String message) {
        String token = jwtTokenProvider.generateToken(user.getEmail());
        return new AuthResponse(
                token,
                new AuthUserResponse(user.getId(), user.getEmail()),
                message
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
