package com.voltwise.simulator.runtime;

public record RegistrationResult(boolean duplicateEvent, int addedAppliances, int updatedAppliances) {

    public static RegistrationResult duplicate() {
        return new RegistrationResult(true, 0, 0);
    }
}
