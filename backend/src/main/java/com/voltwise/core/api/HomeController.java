package com.voltwise.core.api;

import com.voltwise.core.api.HomeDtos.CreateHomeRequest;
import com.voltwise.core.api.HomeDtos.AddApplianceRequest;
import com.voltwise.core.api.HomeDtos.UpdateApplianceRequest;
import com.voltwise.core.api.HomeDtos.ApplianceResponse;
import com.voltwise.core.api.HomeDtos.HistoryPoint;
import com.voltwise.core.api.HomeDtos.HomeEventResponse;
import com.voltwise.core.api.HomeDtos.HomeResponse;
import com.voltwise.core.api.HomeDtos.HomeStatusResponse;
import com.voltwise.core.api.HomeDtos.PagedResponse;
import com.voltwise.core.api.HomeDtos.RecommendationResponse;
import com.voltwise.core.domain.HistoryBucket;
import com.voltwise.core.registration.HomeService;
import com.voltwise.core.snapshot.HistoryService;
import com.voltwise.core.tariff.PeakHourTariffAdvisor;
import com.voltwise.core.api.HomeDtos.PeakHourAdvisoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/homes")
@Tag(name = "Homes", description = "Registration, live state, history and audit endpoints")
@SecurityRequirement(name = "bearerAuth")
public class HomeController {
    private final HomeService homeService;
    private final HomeQueryService queryService;
    private final HistoryService historyService;
    private final PeakHourTariffAdvisor peakHourAdvisor;

    public HomeController(HomeService homeService, HomeQueryService queryService, HistoryService historyService, PeakHourTariffAdvisor peakHourAdvisor) {
        this.homeService = homeService;
        this.queryService = queryService;
        this.historyService = historyService;
        this.peakHourAdvisor = peakHourAdvisor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a home and its appliances")
    public HomeResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = CreateHomeRequest.class),
                    examples = @ExampleObject(name = "Kadikoy home", value = """
                            {"name":"Kadikoy Home","contactEmail":"owner@example.com",
                             "monthlyBudget":1500,"normalTariffPerKwh":2.5,"penaltyMultiplier":1.5,
                             "appliances":[
                               {"name":"Kitchen Kettle","type":"KETTLE","safePowerLimitWatts":2200},
                               {"name":"Spare Kettle","type":"KETTLE","safePowerLimitWatts":2100}
                             ]}
                            """)))
            @Valid @RequestBody CreateHomeRequest request) {
        return homeService.create(request);
    }

    @PostMapping("/{homeId}/appliances")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an appliance in an existing owned home")
    public ApplianceResponse addAppliance(
            @PathVariable Long homeId,
            @Valid @RequestBody AddApplianceRequest request) {
        return homeService.addAppliance(homeId, request);
    }

    @GetMapping
    @Operation(summary = "List registered homes")
    public PagedResponse<HomeResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PagedResponse.of(homeService.list(org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @GetMapping("/status")
    @Operation(summary = "List live home statuses from the live-state store",
            responses = @ApiResponse(responseCode = "200", description = "Live statuses sorted by home id",
                    content = @Content(examples = @ExampleObject(value = """
                            {"content":[{"homeId":1,"homeName":"Kadikoy Home","currentPowerWatts":1850.4,
                             "accumulatedEnergyKwh":0.0148,"currentCost":0.0414,"monthlyBudget":1500,
                             "budgetUsagePercent":0.0028,"tariffState":"NORMAL","anomalyCount":0,
                             "lastUpdatedAt":"2026-07-21T12:00:01Z","appliances":[]}],
                             "page":0,"size":50,"totalElements":1,"totalPages":1}
                            """))))
    public PagedResponse<HomeStatusResponse> statuses(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return queryService.statuses(page, size);
    }

    @GetMapping("/{homeId}/status")
    @Operation(summary = "Get detailed live home and appliance status",
            responses = @ApiResponse(responseCode = "200", description = "Detailed live status",
                    content = @Content(schema = @Schema(implementation = HomeStatusResponse.class),
                            examples = @ExampleObject(value = """
                                    {"homeId":1,"homeName":"Kadikoy Home","currentPowerWatts":1850.4,
                                     "accumulatedEnergyKwh":0.0148,"currentCost":0.0414,"monthlyBudget":1500,
                                     "budgetUsagePercent":0.0028,"tariffState":"NORMAL","anomalyCount":0,
                                     "lastUpdatedAt":"2026-07-21T12:00:01Z","appliances":[
                                       {"applianceId":10,"name":"Kitchen Kettle","type":"KETTLE",
                                        "currentPowerWatts":1850.4,"accumulatedEnergyKwh":0.0148,
                                        "accumulatedCost":0.0414,"operatingState":"ON",
                                        "safePowerLimitWatts":2200,"consecutiveBreachCount":0,
                                        "healthStatus":"NORMAL","lastUpdatedAt":"2026-07-21T12:00:01Z"}]}
                                    """))))
    public HomeStatusResponse status(@PathVariable Long homeId) {
        return queryService.status(homeId);
    }

    @GetMapping("/{homeId}/peak-hour-advisory")
    @Operation(summary = "Get Peak-Hour Tariff status and personalized shift savings advisories")
    public PeakHourAdvisoryResponse peakHourAdvisory(@PathVariable Long homeId) {
        HomeStatusResponse currentStatus = queryService.status(homeId);
        java.math.BigDecimal normalTariff = null;
        try {
            var home = homeService.findOwnedHome(homeId);
            if (home != null) normalTariff = home.getNormalTariffPerKwh();
        } catch (Exception ignored) {}
        return peakHourAdvisor.evaluate(currentStatus, normalTariff);
    }

    @GetMapping("/{homeId}/history")
    @Operation(summary = "Get PostgreSQL-backed, bucketed consumption history")
    public PagedResponse<HistoryPoint> history(@PathVariable Long homeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "HOUR") HistoryBucket bucket,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(7, ChronoUnit.DAYS) : from;
        return historyService.history(homeId, resolvedFrom, resolvedTo, bucket, page, size);
    }

    @GetMapping("/{homeId}/events")
    @Operation(summary = "Get quota, anomaly and tariff audit events")
    public PagedResponse<HomeEventResponse> events(@PathVariable Long homeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.events(homeId, page, size);
    }

    @GetMapping("/{homeId}/recommendations")
    @Operation(summary = "Get persisted Turkish recommendations")
    public PagedResponse<RecommendationResponse> recommendations(@PathVariable Long homeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.recommendations(homeId, page, size);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{homeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a home and all associated data")
    public void deleteHome(@PathVariable Long homeId) {
        homeService.deleteHome(homeId);
    }

    @PutMapping("/{homeId}/appliances/{applianceId}")
    @Operation(summary = "Update name of a registered appliance")
    public ApplianceResponse updateAppliance(
            @PathVariable Long homeId,
            @PathVariable Long applianceId,
            @Valid @RequestBody UpdateApplianceRequest request) {
        return homeService.updateApplianceName(homeId, applianceId, request);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{homeId}/appliances/{applianceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an appliance from a home")
    public void deleteAppliance(@PathVariable Long homeId, @PathVariable Long applianceId) {
        homeService.deleteAppliance(homeId, applianceId);
    }
}
