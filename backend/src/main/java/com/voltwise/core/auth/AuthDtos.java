package com.voltwise.core.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record SendOtpRequest(
            @NotBlank(message = "E-posta adresi boş bırakılamaz")
            @Email(message = "Geçerli bir e-posta adresi giriniz")
            @Size(max = 320)
            String email
    ) {}

    public record SendOtpResponse(
            String message,
            int expiresSeconds
    ) {}

    public record VerifyOtpRequest(
            @NotBlank(message = "E-posta adresi boş bırakılamaz")
            @Email(message = "Geçerli bir e-posta adresi giriniz")
            String email,

            @NotBlank(message = "Doğrulama kodu boş bırakılamaz")
            @Pattern(regexp = "^[0-9]{6}$", message = "Doğrulama kodu 6 haneli rakam olmalıdır")
            String code
    ) {}

    public record AuthResponse(
            String token,
            String email,
            String message
    ) {}
}
