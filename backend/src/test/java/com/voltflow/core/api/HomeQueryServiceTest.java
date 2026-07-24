package com.voltflow.core.api;

import com.voltflow.core.domain.TariffState;
import com.voltflow.core.live.HomeLiveState;
import com.voltflow.core.live.LiveStateInitializer;
import com.voltflow.core.live.LiveStateStore;
import com.voltflow.core.persistence.repository.AnomalyEventRepository;
import com.voltflow.core.persistence.repository.HomeRepository;
import com.voltflow.core.persistence.repository.QuotaEventRepository;
import com.voltflow.core.persistence.repository.RecommendationRepository;
import com.voltflow.core.persistence.repository.TariffChangeEventRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HomeQueryServiceTest {
    @Test
    void liveStatusListingSortsAndPagesWithoutReadingPostgres() {
        HomeRepository homes = mock(HomeRepository.class);
        LiveStateInitializer initializer = mock(LiveStateInitializer.class);
        LiveStateStore liveStates = fixedStore(List.of(state(2L), state(1L)));
        HomeQueryService service = new HomeQueryService(homes, initializer, liveStates,
                mock(QuotaEventRepository.class), mock(AnomalyEventRepository.class),
                mock(TariffChangeEventRepository.class), mock(RecommendationRepository.class));

        var result = service.statuses(0, 1);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting(HomeDtos.HomeStatusResponse::homeId).containsExactly(1L);
        verifyNoInteractions(homes, initializer);
    }

    private HomeLiveState state(long id) {
        return new HomeLiveState(id, "Home " + id, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), BigDecimal.ZERO, TariffState.NORMAL, Instant.EPOCH, Map.of());
    }

    private LiveStateStore fixedStore(List<HomeLiveState> states) {
        return new LiveStateStore() {
            @Override public Optional<HomeLiveState> get(Long homeId) { return Optional.empty(); }
            @Override public List<HomeLiveState> getAll() { return states; }
            @Override public HomeLiveState putIfAbsent(Long homeId, HomeLiveState initialState) { throw new UnsupportedOperationException(); }
            @Override public HomeLiveState update(Long homeId, UnaryOperator<HomeLiveState> updater) { throw new UnsupportedOperationException(); }
            @Override public boolean compareAndSet(Long homeId, HomeLiveState expected, HomeLiveState replacement) { throw new UnsupportedOperationException(); }
        };
    }
}
