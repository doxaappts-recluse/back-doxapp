package pe.dcs.app.features.portal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.portal.dto.PortalDtos.PushSubscribeRequest;
import pe.dcs.app.util.Exceptions;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * M24 [D4] · registro de dispositivo para push. Sin infraestructura VAPID/web-push en el proyecto, esta entrega SOLO
 * guarda la suscripción (para cuando exista ese canal) — nunca envía un push real; el aviso in-app sigue siendo el
 * canal efectivo, igual que el resto del proyecto desde que se apagó el correo.
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public void subscribe(UUID personId, PushSubscribeRequest r) {
        if (r == null || r.endpoint() == null || r.endpoint().isBlank()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "endpoint");
        }
        String keys = "{\"p256dh\":\"" + esc(r.p256dh()) + "\",\"auth\":\"" + esc(r.auth()) + "\"}";
        jdbc.update("""
                insert into push_subscription (id, person_id, endpoint, keys, user_agent, created_at) values (:id, :p, :e, cast(:k as jsonb), :ua, :now)
                on conflict (person_id, endpoint) do update set revoked_at = null, keys = cast(:k as jsonb)
                """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("p", personId).addValue("e", r.endpoint().trim())
                .addValue("k", keys).addValue("ua", r.userAgent()).addValue("now", Timestamp.from(clock.instant())));
    }

    @Transactional
    public void revoke(UUID personId, String endpoint) {
        jdbc.update("update push_subscription set revoked_at = :now where person_id = :p and endpoint = :e",
                new MapSqlParameterSource("now", Timestamp.from(clock.instant())).addValue("p", personId).addValue("e", endpoint));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
