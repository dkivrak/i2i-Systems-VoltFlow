package com.voltwise.core.auth;

import com.voltwise.core.auth.AuthDtos.AuthResponse;
import com.voltwise.core.auth.AuthDtos.LoginRequest;
import com.voltwise.core.auth.AuthDtos.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication API", description = "Email and password authentication endpoints")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and receive a JWT Bearer token")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password and receive a JWT Bearer token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @org.springframework.web.bind.annotation.GetMapping("/profile")
    @Operation(summary = "Get current logged in user profile")
    public ResponseEntity<AuthDtos.AuthUserResponse> getProfile() {
        String email = UserContext.getCurrentUserEmail();
        return ResponseEntity.ok(authService.getProfile(email));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for logged in user")
    public ResponseEntity<java.util.Map<String, String>> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        String email = UserContext.getCurrentUserEmail();
        authService.changePassword(email, request);
        return ResponseEntity.ok(java.util.Map.of("message", "Şifreniz başarıyla değiştirildi."));
    }
}
