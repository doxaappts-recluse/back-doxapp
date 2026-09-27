package pe.dcs.app.features.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.dto.NotificationView;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * M19 base · motor único de avisos. Los demás módulos no crean avisos por su cuenta: llaman a {@link #toPersons} o {@link #toStaff}.
 * Canal disponible en esta versión: IN_APP (bandeja con campana). Correo, SMS, WhatsApp y push llegan después.
 * Las llamadas de envío se unen a la transacción del que las invoca: si esa operación se revierte, el aviso también.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final TypeReference<Map<String, String>> PARAMS = new TypeReference<>() { };
    private static final int MAX_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Value("${notification.retention-days:180}")
    private int retentionDays;

    // ---------------------------------------------------------------- envío (uso interno de los módulos)
    /** Avisa a personas de una organización. dedupeKey (opcional) evita repetir el mismo aviso a la misma persona. */
    @Transactional
    public int toPersons(NotificationType type, UUID organizationId, Collection<UUID> personIds, Map<String, String> params, String link, String dedupeKey) {
        return insert("PERSON", type, organizationId, personIds, params, link, dedupeKey);
    }

    /** Avisa al personal de plataforma. */
    @Transactional
    public int toStaff(NotificationType type, Collection<UUID> staffIds, Map<String, String> params, String link, String dedupeKey) {
        return insert("STAFF", type, null, staffIds, params, link, dedupeKey);
    }

    private int insert(String recipientType, NotificationType type, UUID orgId, Collection<UUID> owners, Map<String, String> params, String link, String dedupe) {
        if (owners == null || owners.isEmpty()) {
            return 0;
        }
        String json;
        try {
            json = mapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        Timestamp now = Timestamp.from(clock.instant());
        int created = 0;
        for (UUID owner : new LinkedHashSet<>(owners)) {
            if (owner == null) {
                continue;
            }
            created += jdbc.update("""
                    insert into notification (id, recipient_type, owner_id, organization_id, type, category, params, link, dedupe_key, created_at)
                    values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    on conflict do nothing
                    """, UUID.randomUUID(), recipientType, owner, orgId, type.name(), type.category().name(), json, link, dedupe, now);
        }
        return created;
    }

    // ---------------------------------------------------------------- destinatarios frecuentes
    /** Personas con acceso ORG_ADMIN activo en la organización. */
    public List<UUID> orgAdmins(UUID orgId) {
        return jdbc.query("select distinct person_id from user_access where organization_id = ? and role = 'ORG_ADMIN' and status = 'ACTIVE'",
                (rs, i) -> (UUID) rs.getObject(1), orgId);
    }

    /** Personas con acceso ORG_BRANCH_ADMIN activo en esa sede; si no hay ninguna, los administradores de la organización. */
    public List<UUID> branchAdmins(UUID orgId, UUID branchId) {
        List<UUID> l = jdbc.query("select distinct person_id from user_access where organization_id = ? and branch_id = ? and role = 'ORG_BRANCH_ADMIN' and status = 'ACTIVE'",
                (rs, i) -> (UUID) rs.getObject(1), orgId, branchId);
        return l.isEmpty() ? orgAdmins(orgId) : l;
    }

    /** Personal de plataforma activo con alguno de los roles dados (SYSTEM_ADMIN, SYSTEM_SUPPORT). */
    public List<UUID> staff(String... roles) {
        String in = String.join(",", Collections.nCopies(roles.length, "?"));
        return jdbc.query("select id from platform_staff where status = 'ACTIVE' and staff_role in (" + in + ")",
                (rs, i) -> (UUID) rs.getObject(1), (Object[]) roles);
    }

    public String organizationName(UUID orgId) {
        List<String> n = jdbc.queryForList("select name from organization where id = ?", String.class, orgId);
        return n.isEmpty() ? "" : n.get(0);
    }

    // ---------------------------------------------------------------- bandeja de la persona que ingresó
    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(AuthenticatedActor actor, boolean unreadOnly, int page, int size) {
        int s = Math.max(1, Math.min(size, MAX_SIZE));
        int p = Math.max(0, page);
        Scope sc = scope(actor);
        String where = sc.where + (unreadOnly ? " and read_at is null" : "");
        Long total = jdbc.queryForObject("select count(*) from notification where " + where, Long.class, sc.args());
        List<Object> args = new ArrayList<>(Arrays.asList(sc.args()));
        args.add(s);
        args.add(p * s);
        List<NotificationView> rows = jdbc.query("select id, type, category, params, link, created_at, read_at from notification where " + where
                + " order by created_at desc, id limit ? offset ?", (rs, i) -> new NotificationView((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                params(rs.getString(4)), rs.getString(5), rs.getTimestamp(6).toInstant(),
                rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()), args.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PaginationResponse((int) t, (int) Math.ceil(t / (double) s), s, p));
    }

    @Transactional(readOnly = true)
    public long unread(AuthenticatedActor actor) {
        Scope sc = scope(actor);
        Long n = jdbc.queryForObject("select count(*) from notification where " + sc.where + " and read_at is null", Long.class, sc.args());
        return n == null ? 0 : n;
    }

    @Transactional
    public void markRead(AuthenticatedActor actor, UUID id) {
        Scope sc = scope(actor);
        List<Object> args = new ArrayList<>(Arrays.asList(sc.args()));
        args.add(id);
        Integer exists = jdbc.queryForObject("select count(*) from notification where " + sc.where + " and id = ?", Integer.class, args.toArray());
        if (exists == null || exists == 0) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        List<Object> upd = new ArrayList<>();
        upd.add(Timestamp.from(clock.instant()));
        upd.addAll(args);
        jdbc.update("update notification set read_at = ? where " + sc.where + " and id = ? and read_at is null", upd.toArray());
    }

    @Transactional
    public int markAllRead(AuthenticatedActor actor) {
        Scope sc = scope(actor);
        List<Object> upd = new ArrayList<>();
        upd.add(Timestamp.from(clock.instant()));
        upd.addAll(Arrays.asList(sc.args()));
        return jdbc.update("update notification set read_at = ? where " + sc.where + " and read_at is null", upd.toArray());
    }

    // ---------------------------------------------------------------- mantenimiento
    /** Borra los avisos leídos con más de {@code notification.retention-days} días (por defecto 180). */
    @Transactional
    public int purgeOld() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        return jdbc.update("delete from notification where read_at is not null and created_at < ?", Timestamp.from(cutoff));
    }

    // ---------------------------------------------------------------- utilidades
    private record Scope(String where, Object[] args) {
    }

    /** Cada persona solo ve lo suyo, y en la organización con la que ingresó (el personal de plataforma, lo que no tiene organización). */
    private Scope scope(AuthenticatedActor actor) {
        if (actor.isStaff()) {
            return new Scope("owner_id = ? and organization_id is null", new Object[]{actor.ownerId()});
        }
        return new Scope("owner_id = ? and organization_id = ?", new Object[]{actor.ownerId(), actor.organizationId()});
    }

    private Map<String, String> params(String json) {
        try {
            return json == null ? Map.of() : mapper.readValue(json, PARAMS);
        } catch (JsonProcessingException e) {
            log.warn("Parámetros de aviso ilegibles: {}", e.getMessage());
            return Map.of();
        }
    }
}
