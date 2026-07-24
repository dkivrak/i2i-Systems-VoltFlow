package com.voltwise.core.auth;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("E-posta adresi veya şifre hatalı.");
    }
}
