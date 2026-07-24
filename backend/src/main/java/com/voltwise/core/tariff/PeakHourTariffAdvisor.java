package com.voltwise.core.tariff;

import com.voltwise.core.api.HomeDtos.HomeStatusResponse;
import com.voltwise.core.api.HomeDtos.ApplianceStatusResponse;
import com.voltwise.core.api.HomeDtos.PeakHourAdvice;
import com.voltwise.core.api.HomeDtos.PeakHourAdvisoryResponse;
import com.voltwise.core.domain.ApplianceType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PeakHourTariffAdvisor {

    public static final LocalTime PEAK_START = LocalTime.of(17, 0);
    public static final LocalTime PEAK_END = LocalTime.of(22, 0);
    public static final String PEAK_WINDOW_TEXT = "17:00 - 22:00";
    public static final BigDecimal PEAK_MULTIPLIER = new BigDecimal("1.50");

    private static final Set<ApplianceType> HIGH_CONSUMPTION_TYPES = Set.of(
            ApplianceType.WASHING_MACHINE,
            ApplianceType.OVEN,
            ApplianceType.AIR_CONDITIONER,
            ApplianceType.KETTLE,
            ApplianceType.MICROWAVE
    );

    public PeakHourAdvisoryResponse evaluate(HomeStatusResponse homeStatus, BigDecimal customNormalTariff) {
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Istanbul"));
        boolean isPeak = !now.isBefore(PEAK_START) && now.isBefore(PEAK_END);

        BigDecimal normalTariff = customNormalTariff != null 
                ? customNormalTariff 
                : new BigDecimal("2.50");
        BigDecimal peakTariff = normalTariff.multiply(PEAK_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);

        List<PeakHourAdvice> advisories = new ArrayList<>();
        BigDecimal totalSavings = BigDecimal.ZERO;

        if (homeStatus != null && homeStatus.appliances() != null) {
            for (ApplianceStatusResponse app : homeStatus.appliances()) {
                boolean isHighType = HIGH_CONSUMPTION_TYPES.contains(app.type());
                boolean isHighPower = app.currentPowerWatts() != null && app.currentPowerWatts().doubleValue() >= 800.0;

                if (isHighType || isHighPower) {
                    BigDecimal watts = app.currentPowerWatts() != null ? app.currentPowerWatts() : new BigDecimal("1200");
                    
                    BigDecimal kw = watts.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
                    BigDecimal deltaTariff = peakTariff.subtract(normalTariff);
                    BigDecimal rawSaving = kw.multiply(new BigDecimal("2.0")).multiply(deltaTariff).multiply(new BigDecimal("4.5"));
                    
                    int roundedSavingInt = Math.max(25, (int) (Math.round(rawSaving.doubleValue() / 5.0) * 5));
                    if (app.type() == ApplianceType.WASHING_MACHINE) {
                        roundedSavingInt = Math.max(45, roundedSavingInt);
                    }
                    
                    BigDecimal estimatedSavings = BigDecimal.valueOf(roundedSavingInt);
                    totalSavings = totalSavings.add(estimatedSavings);

                    String message = generateAdviceMessage(app.name(), app.type(), roundedSavingInt);

                    advisories.add(new PeakHourAdvice(
                            app.applianceId(),
                            app.name(),
                            app.type(),
                            app.currentPowerWatts(),
                            estimatedSavings,
                            message
                    ));
                }
            }
        }

        if (advisories.isEmpty() && homeStatus != null && homeStatus.appliances() != null) {
            for (ApplianceStatusResponse app : homeStatus.appliances()) {
                if (HIGH_CONSUMPTION_TYPES.contains(app.type())) {
                    int saving = app.type() == ApplianceType.WASHING_MACHINE ? 45 : 35;
                    String message = generateAdviceMessage(app.name(), app.type(), saving);
                    advisories.add(new PeakHourAdvice(
                            app.applianceId(),
                            app.name(),
                            app.type(),
                            app.currentPowerWatts(),
                            BigDecimal.valueOf(saving),
                            message
                    ));
                    totalSavings = totalSavings.add(BigDecimal.valueOf(saving));
                }
            }
        }

        Long homeId = homeStatus != null ? homeStatus.homeId() : null;

        return new PeakHourAdvisoryResponse(
                homeId,
                isPeak,
                PEAK_WINDOW_TEXT,
                normalTariff,
                peakTariff,
                totalSavings,
                advisories
        );
    }

    private String generateAdviceMessage(String name, ApplianceType type, int savingsTl) {
        if (type == ApplianceType.WASHING_MACHINE) {
            return "%s kullanımını 22:00 sonrasına erteleyerek ₺%d tasarruf edebilirsiniz.".formatted(name, savingsTl);
        } else if (type == ApplianceType.OVEN) {
            return "%s pişirme işlemini 22:00 sonrasına erteleyerek ₺%d tasarruf sağlayabilirsiniz.".formatted(name, savingsTl);
        } else if (type == ApplianceType.AIR_CONDITIONER) {
            return "%s iklimlendirmesini 17:00 - 22:00 pik saatler dışında çalıştırarak ₺%d tasarruf edebilirsiniz.".formatted(name, savingsTl);
        } else {
            return "%s kullanımını pik saatler (17:00 - 22:00) sonrasına erteleyerek ₺%d tasarruf edebilirsiniz.".formatted(name, savingsTl);
        }
    }
}
