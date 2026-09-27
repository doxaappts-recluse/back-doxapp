package pe.dcs.app.features.dashboard.service;

import org.springframework.stereotype.Component;
import pe.dcs.app.features.dashboard.dto.WidgetResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** M01 · caché de 5 minutos por (nivel, organización, sede, filtros). Un cambio de contrato la vacía (ContractGate.invalidate). */
@Component
public class DashboardCache {

    static final Duration TTL = Duration.ofMinutes(5);

    private record Entry(Instant loadedAt, List<WidgetResult> widgets) {
    }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final Clock clock;

    public DashboardCache(Clock clock) {
        this.clock = clock;
    }

    public List<WidgetResult> get(String key) {
        Entry e = cache.get(key);
        if (e != null && e.loadedAt().plus(TTL).isAfter(clock.instant())) {
            return e.widgets();
        }
        return null;
    }

    public void put(String key, List<WidgetResult> widgets) {
        if (cache.size() > 500) {
            cache.clear();
        }
        cache.put(key, new Entry(clock.instant(), widgets));
    }

    public void clear() {
        cache.clear();
    }
}
