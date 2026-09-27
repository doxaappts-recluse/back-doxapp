package pe.dcs.app.features.visitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de envíos del formulario público por IP [V11]: {@code visitor.public-rate-limit} envíos por hora (5 por defecto).
 * Es en memoria de este proceso: con varias instancias del back cada una lleva su propia cuenta.
 */
@Component
public class PublicRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int max;

    public PublicRateLimiter(Clock clock, @Value("${visitor.public-rate-limit:5}") int max) {
        this.clock = clock;
        this.max = Math.max(1, max);
    }

    /** true si el envío entra en el cupo (y lo cuenta); false si la IP ya llegó al máximo en la última hora. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        long from = now - WINDOW.toMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < from) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
        }
        if (hits.size() > 5000) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast() < from;
                }
            });
        }
        return true;
    }
}
