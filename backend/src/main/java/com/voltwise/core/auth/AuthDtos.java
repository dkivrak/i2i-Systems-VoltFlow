package com.voltwise.core.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "E-posta adresi boş bırakılamaz")
            @Email(message = "Geçerli bir e-posta adresi giriniz")
            @Size(max = 320)
            String email,
            @NotBlank(message = "Şifre boş bırakılamaz")
            @Size(min = 8, max = 72, message = "Şifre 8 ile 72 karakter arasında olmalıdır")
            String password
    ) {}

    public record LoginRequest(
            @NotBlank(message = "E-posta adresi boş bırakılamaz")
            @Email(message = "Geçerli bir e-posta adresi giriniz")
            @Size(max = 320)
            String email,
            @NotBlank(message = "Şifre boş bırakılamaz")
            @Size(max = 72, message = "Şifre en fazla 72 karakter olabilir")
            String password
    ) {}

    public record AuthUserResponse(Long id, String email) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Mevcut şifre boş bırakılamaz")
            String currentPassword,
            @NotBlank(message = "Yeni şifre boş bırakılamaz")
            @Size(min = 8, max = 72, message = "Yeni şifre 8 ile 72 karakter arasında olmalıdır")
            String newPassword
    ) {}

    public record AuthResponse(
            String token,
            AuthUserResponse user,
            String message
    ) {}
}
