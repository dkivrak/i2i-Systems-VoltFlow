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
                .range("idle", 25, 45)
                .range("compressor", 120, 240)
                .range("startup", 220, 380)
                .probability("compressor-start", 0.45)
                .duration("startup", 1)
                .duration("compressor", 8));

        profiles.put(ApplianceType.KETTLE, new ApplianceProfile()
                .range("off", 0, 0)
                .range("active", 1500, 2400)
                .probability("start", 0.25)
                .duration("active", 4));

        profiles.put(ApplianceType.OVEN, new ApplianceProfile()
                .range("standby", 15, 35)
                .range("heating", 1800, 3000)
                .range("thermostat-on", 1400, 2600)
                .range("thermostat-off", 20, 50)
                .probability("start", 0.30)
                .probability("stop", 0.10)
                .duration("preheat", 6)
                .duration("thermostat-on", 5)
                .duration("thermostat-off", 2));

        profiles.put(ApplianceType.TELEVISION, new ApplianceProfile()
                .range("standby", 10, 25)
                .range("on", 80, 220)
                .probability("turn-on", 0.55)
                .probability("turn-off", 0.08));

        profiles.put(ApplianceType.WASHING_MACHINE, new ApplianceProfile()
                .range("idle", 5, 15)
                .range("filling", 25, 60)
                .range("washing", 250, 550)
                .range("heating", 1500, 2200)
                .range("spinning", 450, 950)
                .probability("start", 0.35)
                .duration("filling", 2)
                .duration("washing", 6)
                .duration("heating", 4)
                .duration("rinse", 3)
                .duration("spinning", 4));

        profiles.put(ApplianceType.AIR_CONDITIONER, new ApplianceProfile()
                .range("standby", 15, 30)
                .range("fan", 80, 180)
                .range("compressor", 800, 2400)
                .probability("turn-on", 0.50)
                .probability("turn-off", 0.08)
                .duration("fan", 3)
                .duration("compressor", 8));

        profiles.put(ApplianceType.MICROWAVE, new ApplianceProfile()
                .range("off", 0, 0)
                .range("active", 850, 1600)
                .probability("start", 0.28)
                .duration("active", 3));

        profiles.put(ApplianceType.LAMP, new ApplianceProfile()
                .range("off", 0, 0)
                .range("on", 15, 75)
                .probability("turn-on", 0.65)
                .probability("turn-off", 0.12));

        profiles.put(ApplianceType.COMPUTER, new ApplianceProfile()
                .range("off", 0, 0)
                .range("standby", 10, 25)
                .range("idle", 70, 210)
                .range("high-load", 250, 750)
                .probability("power-on", 0.50)
                .probability("wake", 0.45)
                .probability("sleep", 0.05)
                .probability("power-off", 0.05)
                .probability("high-load", 0.25)
                .duration("boot", 2)
                .duration("high-load", 5));

        profiles.put(ApplianceType.UNKNOWN, new ApplianceProfile()
                .range("off", 0, 0)
                .range("standby", 2, 8)
                .range("active", 100, 300)
                .probability("start", 0.05)
                .duration("active", 1800));

        return profiles;
    }
}
