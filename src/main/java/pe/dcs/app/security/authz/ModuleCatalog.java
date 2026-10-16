package pe.dcs.app.security.authz;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.features.module.domain.AppModuleRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lectura cacheada (60 s) del catálogo de módulos. M03 invalida al modificar el catálogo. */
@Component
@RequiredArgsConstructor
public class ModuleCatalog {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final AppModuleRepository repo;
    private final Clock clock;

    private volatile Map<String, AppModule> byCode = Map.of();
    private volatile Instant loadedAt = Instant.MIN;

    public AppModule find(String code) {
        return all().get(code);
    }

    public List<AppModule> published() {
        return all().values().stream()
                .filter(AppModule::isPublished)
                .sorted(Comparator.comparingInt(AppModule::getSortOrder).thenComparing(AppModule::getCode))
                .toList();
    }

    public void invalidate() {
        loadedAt = Instant.MIN;
    }

    private Map<String, AppModule> all() {
        Instant now = clock.instant();
        if (loadedAt.plus(TTL).isBefore(now)) {
            synchronized (this) {
                if (loadedAt.plus(TTL).isBefore(now)) {
                    byCode = repo.findAll().stream().collect(Collectors.toUnmodifiableMap(AppModule::getCode, Function.identity()));
                    loadedAt = now;
                }
            }
        }
        return byCode;
    }
}
