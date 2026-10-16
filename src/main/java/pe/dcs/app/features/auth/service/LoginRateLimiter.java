package pe.dcs.app.features.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos fallidos por IP+usuario (ventana deslizante en memoria). Complementa el bloqueo por cuenta
 * (5 fallos → 15 min): frena el barrido de usuarios inexistentes. En despliegue con varias instancias debe moverse a un almacén compartido.
 */
@Component
public class LoginRateLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.security.rate-limit-attempts:10}") int maxFailures,
                            @Value("${app.security.rate-limit-window-minutes:15}") long windowMinutes,
                            Clock clock) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.clock = clock;
    }

    /** Lanza 429 si la clave superó el máximo de fallos dentro de la ventana. */
    public void assertAllowed(String key) {
        Deque<Instant> q = failures.get(key);
        if (q == null) {
            return;
        }
        synchronized (q) {
            prune(q);
            if (q.size() >= maxFailures) {
                throw new Exceptions("error.common.rateLimited", HttpStatus.TOO_MANY_REQUESTS, window.toMinutes());
            }
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.instant());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> q) {
        Instant limit = clock.instant().minus(window);
        while (!q.isEmpty() && q.peekFirst().isBefore(limit)) {
            q.removeFirst();
        }
    }
}
