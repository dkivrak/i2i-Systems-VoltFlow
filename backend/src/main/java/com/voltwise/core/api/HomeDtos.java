package com.voltwise.core.api;

import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.HistoryBucket;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.domain.TriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class HomeDtos {
    private HomeDtos() {}

    public record CreateHomeRequest(
            @Schema(description = "Display name of the home", example = "Kadıköy Evim")
            @NotBlank @Size(min = 2, max = 160, message = "Ev adı 2-160 karakter olmalıdır") String name,

            @Schema(description = "City location of the home", example = "İstanbul")
            @Size(max = 100) String city,

            @Schema(description = "Recipient for quota and anomaly notifications", example = "owner@example.com")
            @NotBlank @Email(message = "Geçerli bir e-posta adresi giriniz") @Size(max = 320) String contactEmail,

            @Schema(description = "Monthly budget in TL (1 to 1,000,000 TL)", example = "1500.00")
            @DecimalMin(value = "1.00", message = "Aylık bütçe en az 1 ₺ olmalıdır")
            @DecimalMax(value = "1000000.00", message = "Aylık bütçe en fazla 1.000.000 ₺ olabilir") BigDecimal monthlyBudget,

            @Schema(description = "Normal TL/kWh tariff (0.01 to 100 TL)", example = "2.50")
            @DecimalMin(value = "0.01", message = "Normal tarife en az 0,01 ₺/kWh olmalıdır")
            @DecimalMax(value = "100.00", message = "Normal tarife en fazla 100 ₺/kWh olabilir") BigDecimal normalTariffPerKwh,

            @Schema(description = "Post-budget penalty multiplier (1.01 to 10.0)", example = "1.50")
            @DecimalMin(value = "1.01", message = "Ek tarife çarpanı 1,01'den büyük olmalıdır")
            @DecimalMax(value = "10.00", message = "Ek tarife çarpanı en fazla 10,0 olabilir") BigDecimal penaltyMultiplier,

            @Schema(description = "Zero to 20 appliances; devices may be registered later")
            @NotNull(message = "Cihaz listesi zorunludur")
            @Size(max = 20, message = "Bir eve en fazla 20 cihaz eklenebilir")
            List<@Valid ApplianceRequest> appliances
    ) {
        public CreateHomeRequest(String name, String contactEmail, BigDecimal monthlyBudget,
                                 BigDecimal normalTariffPerKwh, BigDecimal penaltyMultiplier,
                                 List<ApplianceRequest> appliances) {
            this(name, "İstanbul", contactEmail, monthlyBudget, normalTariffPerKwh, penaltyMultiplier, appliances);
        }
    }

    public record ApplianceRequest(
            @Schema(example = "Mutfak Kettle") @NotBlank @Size(max = 160) String name,
            @Schema(example = "KETTLE") @NotNull(message = "Cihaz türü zorunludur") ApplianceType type,
            @Schema(example = "2300") @NotNull(message = "Güvenli Watt sınırı zorunludur")
            @DecimalMin(value = "1.0", message = "Güvenli Watt sınırı en az 1 W olmalıdır")
            @DecimalMax(value = "50000.0", message = "Güvenli Watt sınırı en fazla 50.000 W olabilir") BigDecimal safePowerLimitWatts
    ) {}

    public record AddApplianceRequest(
            @Schema(example = "Salon Televizyonu")
            @NotBlank(message = "Cihaz adı zorunludur")
            @Size(max = 160, message = "Cihaz adı en fazla 160 karakter olabilir") String name,
            @Schema(example = "TELEVISION")
            @NotNull(message = "Cihaz türü zorunludur") ApplianceType type,
            @Schema(example = "450")
            @NotNull(message = "Güvenli Watt sınırı zorunludur")
            @DecimalMin(value = "1.0", message = "Güvenli Watt sınırı en az 1 W olmalıdır")
            @DecimalMax(value = "50000.0", message = "Güvenli Watt sınırı en fazla 50.000 W olabilir")
            BigDecimal safePowerLimitWatts
    ) {}

    public record HomeResponse(
            Long id, String name, String city, String contactEmail, BigDecimal monthlyBudget,
            BigDecimal normalTariffPerKwh, BigDecimal penaltyMultiplier,
            Instant createdAt, List<ApplianceResponse> appliances
    ) {}

    public record ApplianceResponse(
            Long id, String name, ApplianceType type, BigDecimal safePowerLimitWatts
    ) {}

    public record HomeStatusResponse(
            Long homeId, String homeName, String city, BigDecimal currentPowerWatts,
            BigDecimal accumulatedEnergyKwh, BigDecimal currentCost, BigDecimal monthlyBudget,
            BigDecimal budgetUsagePercent, TariffState tariffState, int anomalyCount,
            Instant lastUpdatedAt, List<ApplianceStatusResponse> appliances
    ) {}

    public record ApplianceStatusResponse(
            Long applianceId, String name, ApplianceType type, BigDecimal currentPowerWatts,
            BigDecimal accumulatedEnergyKwh, BigDecimal accumulatedCost,
            OperatingState operatingState, BigDecimal safePowerLimitWatts,
            int consecutiveBreachCount, ApplianceHealthStatus healthStatus, Instant lastUpdatedAt
    ) {}

    public record HistoryPoint(
            Instant periodStart, Instant periodEnd, HistoryBucket bucket,
            BigDecimal energyKwh, BigDecimal averagePowerWatts,
            BigDecimal maximumPowerWatts, BigDecimal cost
    ) {}

    public record HomeEventResponse(
            String eventType, Long eventId, Instant occurredAt, String status,
            BigDecimal usagePercent, BigDecimal measuredPowerWatts,
            Long applianceId, String details
    ) {}

    public record RecommendationResponse(
            Long id, TriggerType triggerType, Long triggerReferenceId,
            String recommendationText, String modelName, boolean fallbackUsed, Instant createdAt
    ) {}

    public record PagedResponse<T>(
            List<T> content, int page, int size, long totalElements, int totalPages
    ) {
        public static <T> PagedResponse<T> of(org.springframework.data.domain.Page<T> result) {
            return new PagedResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }

        public static <T> PagedResponse<T> slice(List<T> all, int page, int size) {
            int from = (int) Math.min((long) page * size, all.size());
            int to = Math.min(from + size, all.size());
            int pages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / size);
            return new PagedResponse<>(all.subList(from, to), page, size, all.size(), pages);
        }
    }

    public record PeakHourAdvice(
            Long applianceId,
            String applianceName,
            ApplianceType applianceType,
            BigDecimal currentPowerWatts,
            BigDecimal estimatedSavingsTl,
            String recommendationMessage
    ) {}

    public record PeakHourAdvisoryResponse(
            Long homeId,
            boolean isPeakHour,
            String peakWindowText,
            BigDecimal normalTariffPerKwh,
            BigDecimal peakTariffPerKwh,
            BigDecimal estimatedTotalSavingsTl,
            List<PeakHourAdvice> advisories
    ) {}
}
