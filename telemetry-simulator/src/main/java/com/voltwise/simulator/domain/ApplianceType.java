package com.voltwise.simulator.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ApplianceType {
    REFRIGERATOR,
    KETTLE,
    OVEN,
    TELEVISION,
    WASHING_MACHINE,
    AIR_CONDITIONER,
    MICROWAVE,
    LAMP,
    COMPUTER,
    UNKNOWN;

    @JsonCreator
    public static ApplianceType fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return ApplianceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
