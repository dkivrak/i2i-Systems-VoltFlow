package com.voltwise.core.snapshot;

import com.voltwise.core.api.HomeDtos.HistoryPoint;
import com.voltwise.core.api.HomeDtos.PagedResponse;
import com.voltwise.core.domain.HistoryBucket;
import com.voltwise.core.persistence.entity.ConsumptionSnapshotEntity;
import com.voltwise.core.persistence.repository.ConsumptionSnapshotRepository;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.registration.ResourceNotFoundException;
import com.voltwise.core.registration.HomeAccessDeniedException;
import com.voltwise.core.auth.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
public class HistoryService {
    private final HomeRepository homes;
    private final ConsumptionSnapshotRepository snapshots;

    public HistoryService(HomeRepository homes, ConsumptionSnapshotRepository snapshots) {
        this.homes = homes;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public PagedResponse<HistoryPoint> history(Long homeId, Instant from, Instant to,
                                               HistoryBucket bucket, int page, int size) {
        if (!homes.existsById(homeId)) {
            throw new ResourceNotFoundException("Home not found: " + homeId);
        }
        String ownerEmail = UserContext.getCurrentUserEmail();
        if (ownerEmail != null) {
            var home = homes.findById(homeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + homeId));
            if (!home.getOwnerEmail().equalsIgnoreCase(ownerEmail)) {
                throw new HomeAccessDeniedException();
            }
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        var raw = snapshots.findByHomeIdAndApplianceIsNullAndPeriodStartLessThanAndPeriodEndGreaterThanOrderByPeriodStartAsc(
                homeId, to, from);
        var grouped = new LinkedHashMap<Instant, BucketAccumulator>();
        for (ConsumptionSnapshotEntity snapshot : raw) {
            distribute(snapshot, from, to, bucket, grouped);
        }
        var points = new ArrayList<HistoryPoint>(grouped.size());
        grouped.forEach((start, accumulator) -> points.add(accumulator.toPoint(start, bucket)));
        return PagedResponse.slice(points, page, size);
    }

    private Instant bucketStart(Instant value, HistoryBucket bucket) {
        ZonedDateTime utc = value.atZone(ZoneOffset.UTC);
        return (bucket == HistoryBucket.HOUR
                ? utc.truncatedTo(ChronoUnit.HOURS)
                : utc.toLocalDate().atStartOfDay(ZoneOffset.UTC)).toInstant();
    }

    private void distribute(ConsumptionSnapshotEntity snapshot, Instant from, Instant to, HistoryBucket bucket,
                            LinkedHashMap<Instant, BucketAccumulator> grouped) {
        Instant cursor = snapshot.getPeriodStart().isBefore(from) ? from : snapshot.getPeriodStart();
        Instant clippedEnd = snapshot.getPeriodEnd().isAfter(to) ? to : snapshot.getPeriodEnd();
        while (cursor.isBefore(clippedEnd)) {
            Instant key = bucketStart(cursor, bucket);
            Instant bucketEnd = bucket == HistoryBucket.HOUR
                    ? key.plus(1, ChronoUnit.HOURS)
                    : key.plus(1, ChronoUnit.DAYS);
            Instant segmentEnd = clippedEnd.isBefore(bucketEnd) ? clippedEnd : bucketEnd;
            grouped.computeIfAbsent(key, ignored -> new BucketAccumulator())
                    .add(snapshot, cursor, segmentEnd);
            cursor = segmentEnd;
        }
    }

    private static class BucketAccumulator {
        private BigDecimal durationWeightedPower = BigDecimal.ZERO;
        private BigDecimal totalDurationWeight = BigDecimal.ZERO;
        private BigDecimal maximumPower = BigDecimal.ZERO;
        private BigDecimal energy = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private Instant lastEnd;

        void add(ConsumptionSnapshotEntity value, Instant segmentStart, Instant segmentEnd) {
            long fullDurationNanos = Duration.between(value.getPeriodStart(), value.getPeriodEnd()).toNanos();
            long segmentDurationNanos = Duration.between(segmentStart, segmentEnd).toNanos();
            if (fullDurationNanos <= 0 || segmentDurationNanos <= 0) {
                return;
            }
            BigDecimal duration = BigDecimal.valueOf(segmentDurationNanos);
            BigDecimal fraction = duration.divide(BigDecimal.valueOf(fullDurationNanos), 15, RoundingMode.HALF_UP);
            durationWeightedPower = durationWeightedPower.add(value.getAveragePowerWatts().multiply(duration));
            totalDurationWeight = totalDurationWeight.add(duration);
            maximumPower = maximumPower.max(value.getMaximumPowerWatts());
            energy = energy.add(value.getEnergyKwh().multiply(fraction));
            cost = cost.add(value.getCost().multiply(fraction));
            if (lastEnd == null || segmentEnd.isAfter(lastEnd)) {
                lastEnd = segmentEnd;
            }
        }

        HistoryPoint toPoint(Instant start, HistoryBucket bucket) {
            Instant end = bucket == HistoryBucket.HOUR ? start.plus(1, ChronoUnit.HOURS) : start.plus(1, ChronoUnit.DAYS);
            if (lastEnd != null && lastEnd.isBefore(end)) end = lastEnd;
            return new HistoryPoint(start, end, bucket, energy,
                    durationWeightedPower.divide(totalDurationWeight, 3, RoundingMode.HALF_UP),
                    maximumPower, cost);
        }
    }
}
