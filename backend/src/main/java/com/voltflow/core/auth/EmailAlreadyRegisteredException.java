package com.voltflow.core.auth;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Bu e-posta adresiyle bir hesap zaten mevcut.");
    }
}
