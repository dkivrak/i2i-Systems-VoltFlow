package com.voltflow.core.telemetry;

import com.voltflow.core.domain.QuotaThreshold;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class QuotaRule {
    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public List<QuotaThreshold> crossedThresholds(BigDecimal previousUsage, BigDecimal currentUsage) {
        List<QuotaThreshold> result = new ArrayList<>(2);
        if (crossed(previousUsage, currentUsage, EIGHTY)) result.add(QuotaThreshold.EIGHTY_PERCENT);
        if (crossed(previousUsage, currentUsage, ONE_HUNDRED)) result.add(QuotaThreshold.ONE_HUNDRED_PERCENT);
        return List.copyOf(result);
    }

    private boolean crossed(BigDecimal previous, BigDecimal current, BigDecimal threshold) {
        return previous.compareTo(threshold) < 0 && current.compareTo(threshold) >= 0;
    }
}
