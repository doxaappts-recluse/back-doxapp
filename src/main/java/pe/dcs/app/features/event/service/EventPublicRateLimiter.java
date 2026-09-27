package pe.dcs.app.features.event.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** M14 [V18]: límite de envíos del formulario público de inscripción por IP (mismo patrón que M07, en memoria de este proceso). */
@Component
public class EventPublicRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int max;

    public EventPublicRateLimiter(Clock clock, @Value("${event.public-rate-limit:5}") int max) {
        this.clock = clock;
        this.max = Math.max(1, max);
    }

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
