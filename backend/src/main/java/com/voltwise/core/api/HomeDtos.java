package com.voltwise.core.api;

import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.HistoryBucket;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.domain.TriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class HomeDtos {
    private HomeDtos() {}

    public record CreateHomeRequest(
            @Schema(description = "Display name of the home", example = "Kadikoy Home")
            @NotBlank @Size(max = 160) String name,
            @Schema(description = "Recipient for quota and anomaly notifications", example = "owner@example.com")
            @NotBlank @Email @Size(max = 320) String contactEmail,
            @Schema(description = "Optional monthly budget; DEFAULT_MONTHLY_BUDGET is used when omitted", example = "1500.00")
            @DecimalMin(value = "0.01") BigDecimal monthlyBudget,
            @Schema(description = "Optional normal TL/kWh tariff; NORMAL_TARIFF_PER_KWH is used when omitted", example = "2.50")
            @DecimalMin(value = "0.000001") BigDecimal normalTariffPerKwh,
            @Schema(description = "Optional post-budget multiplier; PENALTY_TARIFF_MULTIPLIER is used when omitted", example = "1.50")
            @DecimalMin(value = "1.0") BigDecimal penaltyMultiplier,
            @Schema(description = "One or more appliances; duplicate appliance types are supported")
            @NotEmpty @Size(max = 100) List<@Valid ApplianceRequest> appliances
    ) {}

    public record ApplianceRequest(
            @Schema(example = "Kitchen Kettle") @NotBlank @Size(max = 160) String name,
            @Schema(example = "KETTLE") @NotNull ApplianceType type,
            @Schema(example = "2200") @NotNull @DecimalMin(value = "0.1") BigDecimal safePowerLimitWatts
    ) {}

    public record HomeResponse(
            Long id, String name, String contactEmail, BigDecimal monthlyBudget,
            BigDecimal normalTariffPerKwh, BigDecimal penaltyMultiplier,
            Instant createdAt, List<ApplianceResponse> appliances
    ) {}

    public record ApplianceResponse(
            Long id, String name, ApplianceType type, BigDecimal safePowerLimitWatts
    ) {}

    public record HomeStatusResponse(
            Long homeId, String homeName, BigDecimal currentPowerWatts,
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
}
