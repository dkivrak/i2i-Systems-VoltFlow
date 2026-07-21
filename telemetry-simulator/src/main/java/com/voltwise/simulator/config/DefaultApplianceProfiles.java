package com.voltwise.simulator.config;

import com.voltwise.simulator.domain.ApplianceType;
import java.util.EnumMap;
import java.util.Map;

public final class DefaultApplianceProfiles {

    private DefaultApplianceProfiles() {
    }

    public static Map<ApplianceType, ApplianceProfile> create() {
        Map<ApplianceType, ApplianceProfile> profiles = new EnumMap<>(ApplianceType.class);

        profiles.put(ApplianceType.REFRIGERATOR, new ApplianceProfile()
                .range("idle", 5, 15)
                .range("compressor", 70, 180)
                .range("startup", 181, 350)
                .probability("compressor-start", 0.18)
                .duration("startup", 1)
                .duration("compressor", 5));

        profiles.put(ApplianceType.KETTLE, new ApplianceProfile()
                .range("off", 0, 0)
                .range("active", 1500, 2400)
                .probability("start", 0.035)
                .duration("active", 2));

        profiles.put(ApplianceType.OVEN, new ApplianceProfile()
                .range("standby", 2, 8)
                .range("heating", 1800, 3000)
                .range("thermostat-on", 1400, 2600)
                .range("thermostat-off", 8, 40)
                .probability("start", 0.018)
                .probability("stop", 0.12)
                .duration("preheat", 6)
                .duration("thermostat-on", 3)
                .duration("thermostat-off", 2));

        profiles.put(ApplianceType.TELEVISION, new ApplianceProfile()
                .range("standby", 1, 5)
                .range("on", 60, 200)
                .probability("turn-on", 0.06)
                .probability("turn-off", 0.025));

        profiles.put(ApplianceType.WASHING_MACHINE, new ApplianceProfile()
                .range("idle", 0, 3)
                .range("filling", 5, 30)
                .range("washing", 200, 500)
                .range("heating", 1500, 2200)
                .range("spinning", 400, 900)
                .probability("start", 0.015)
                .duration("filling", 2)
                .duration("washing", 5)
                .duration("heating", 3)
                .duration("rinse", 3)
                .duration("spinning", 3));

        profiles.put(ApplianceType.AIR_CONDITIONER, new ApplianceProfile()
                .range("standby", 1, 5)
                .range("fan", 50, 150)
                .range("compressor", 700, 2500)
                .probability("turn-on", 0.04)
                .probability("turn-off", 0.05)
                .duration("fan", 3)
                .duration("compressor", 5));

        profiles.put(ApplianceType.MICROWAVE, new ApplianceProfile()
                .range("off", 0, 0)
                .range("active", 800, 1500)
                .probability("start", 0.025)
                .duration("active", 2));

        profiles.put(ApplianceType.LAMP, new ApplianceProfile()
                .range("off", 0, 0)
                .range("on", 5, 100)
                .probability("turn-on", 0.07)
                .probability("turn-off", 0.04));

        profiles.put(ApplianceType.COMPUTER, new ApplianceProfile()
                .range("off", 0, 0)
                .range("standby", 1, 10)
                .range("idle", 50, 180)
                .range("high-load", 200, 800)
                .probability("power-on", 0.045)
                .probability("wake", 0.18)
                .probability("sleep", 0.025)
                .probability("power-off", 0.035)
                .probability("high-load", 0.09)
                .duration("boot", 2)
                .duration("high-load", 4));

        return profiles;
    }
}
