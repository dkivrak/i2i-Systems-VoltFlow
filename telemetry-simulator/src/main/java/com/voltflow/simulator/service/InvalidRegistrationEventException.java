package com.voltflow.simulator.service;

public class InvalidRegistrationEventException extends RuntimeException {

    public InvalidRegistrationEventException(String message) {
        super(message);
    }

    public InvalidRegistrationEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
