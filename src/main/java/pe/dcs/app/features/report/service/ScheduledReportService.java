package pe.dcs.app.features.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.features.person.service.PersonLookupService;
import pe.dcs.app.features.report.dto.ReportDtos;
import pe.dcs.app.features.report.spi.ReportProvider;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M20 · Programaciones de envío. [D2] sin SMTP en el proyecto: en vez de correo, se genera el export vía
 * {@link ExportJobService} y se avisa a cada destinatario por la campana in-app, con enlace de descarga.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledReportService {

    private static final String SELECT = "select * from scheduled_report ";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ReportCatalogService catalog;
    private final ExportJobService exportJobs;
    private final PersonLookupService persons;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ReportDtos.ScheduledReportView> list(AccessScope scope) {
        return jdbc.query(SELECT + "where organization_id = :o order by created_at desc", new MapSqlParameterSource("o", scope.organizationId()), (rs, i) -> map(rs));
    }

    @Transactional
    public ReportDtos.ScheduledReportView create(AuthenticatedActor actor, AccessScope scope, ReportDtos.ScheduledReportRequest req) {
        Integer count = jdbc.queryForObject("select count(*) from scheduled_report where organization_id = :o", new MapSqlParameterSource("o", scope.organizationId()), Integer.class);
        if (count != null && count >= ReportSupport.MAX_SCHEDULES_PER_ORG) {
            throw new Exceptions("error.report.scheduleLimit", HttpStatus.UNPROCESSABLE_ENTITY);                                         // [V7][M20-T12]
        }
        if (req.recipientIds() == null || req.recipientIds().isEmpty()) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "destinatarios");
        }
        catalog.provider(req.code());                                                                                                    // 404 si el código no existe
        for (UUID pid : req.recipientIds()) {
            PersonLookupService.PersonMin p = persons.get(scope.organizationId(), pid);
            if (!"ACTIVE".equals(p.status())) {
                throw new Exceptions("error.report.recipientNoAccess", HttpStatus.UNPROCESSABLE_ENTITY, p.fullName());                   // [V5]
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into scheduled_report (id, organization_id, code, filters, frequency, day_of_week, day_of_month, format, recipients, status, created_at, created_by)"
                        + " values (:id, :o, :c, cast(:f as jsonb), :freq, :dow, :dom, :fmt, :rec, 'ACTIVE', :at, :by)",
                new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()).addValue("c", req.code()).addValue("f", writeJson(req.filters()))
                        .addValue("freq", req.frequency()).addValue("dow", req.dayOfWeek()).addValue("dom", req.dayOfMonth())
                        .addValue("fmt", req.format() == null ? "XLSX" : req.format()).addValue("rec", req.recipientIds().toArray(new UUID[0]))
                        .addValue("at", Timestamp.from(clock.instant())).addValue("by", actor.contextId()));
        return get(scope, id);
    }

    @Transactional
    public void delete(AccessScope scope, UUID id) {
        jdbc.update("delete from scheduled_report where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()));
    }

    @Transactional
    public ReportDtos.ScheduledReportView setStatus(AccessScope scope, UUID id, boolean active) {
        jdbc.update("update scheduled_report set status = :s, updated_at = :at, version = version + 1 where id = :id and organization_id = :o",
                new MapSqlParameterSource("s", active ? "ACTIVE" : "PAUSED").addValue("at", Timestamp.from(clock.instant())).addValue("id", id).addValue("o", scope.organizationId()));
        return get(scope, id);
    }

    private ReportDtos.ScheduledReportView get(AccessScope scope, UUID id) {
        return jdbc.query(SELECT + "where id = :id and organization_id = :o", new MapSqlParameterSource("id", id).addValue("o", scope.organizationId()), (rs, i) -> map(rs))
                .stream().findFirst().orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    /** Invocado por {@link ScheduledReportJob}: ejecuta las programaciones vigentes hoy y avisa por campana [D2]. */
    @Transactional
    public void runDue() {
        LocalDate today = LocalDate.now(clock);
        List<Map<String, Object>> due = jdbc.queryForList("select * from scheduled_report where status = 'ACTIVE' and"
                        + " ((frequency = 'WEEKLY' and day_of_week = :dow and (last_run_at is null or last_run_at < :cutoffWeek))"
                        + " or (frequency = 'MONTHLY' and day_of_month = :dom and (last_run_at is null or last_run_at < :cutoffMonth)))",
                new MapSqlParameterSource("dow", today.getDayOfWeek().getValue()).addValue("dom", Math.min(today.getDayOfMonth(), 28))
                        .addValue("cutoffWeek", Timestamp.from(clock.instant().minusSeconds(6L * 24 * 3600)))
                        .addValue("cutoffMonth", Timestamp.from(clock.instant().minusSeconds(27L * 24 * 3600))));
        for (Map<String, Object> row : due) {
            runOne(row);
        }
    }

    private void runOne(Map<String, Object> row) {
        UUID id = (UUID) row.get("id");
        UUID orgId = (UUID) row.get("organization_id");
        String code = (String) row.get("code");
        try {
            ReportProvider provider = catalog.provider(code);
            AccessScope scope = new AccessScope(orgId, true, Set.of(), null, null, RoleType.ORG_ADMIN);
            ReportProvider.ReportResult result = provider.run(scope, new ReportProvider.Filters(null, LocalDate.now(clock).minusMonths(1), LocalDate.now(clock), Map.of()));
            UUID[] recipients = (UUID[]) row.get("recipients");
            List<UUID> ok = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            for (UUID personId : recipients) {
                PersonLookupService.PersonMin person = null;
                try {
                    person = persons.get(orgId, personId);
                } catch (RuntimeException ignored) {
                    // persona ya no existe/visible: se trata como "sin acceso" más abajo
                }
                // [V5] revalidación al enviar: se exige que el destinatario siga activo; si no, se omite y se avisa al creador.
                if (person != null && "ACTIVE".equals(person.status())) {
                    ok.add(personId);
                } else {
                    skipped.add(person == null ? personId.toString() : person.fullName());
                }
            }
            if (!ok.isEmpty()) {
                UUID jobId = exportJobs.storeSystemExport(orgId, code, result, (String) row.get("format"));
                notifications.toPersons(NotificationType.REPORT_SCHEDULED_READY, orgId, ok, Map.of("code", code), "/reports/export-jobs/" + jobId, null);
            }
            if (!skipped.isEmpty()) {
                UUID createdBy = (UUID) row.get("created_by");
                if (createdBy != null) {
                    notifications.toPersons(NotificationType.REPORT_SCHEDULED_SKIPPED, orgId, List.of(createdBy), Map.of("names", String.join(", ", skipped)), null, null);
                }
            }
            jdbc.update("update scheduled_report set last_run_at = :at where id = :id", new MapSqlParameterSource("at", Timestamp.from(clock.instant())).addValue("id", id));
        } catch (RuntimeException e) {
            log.error("Falló la programación de reporte {}", id, e);
        }
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReportDtos.ScheduledReportView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            ReportDtos.RunFilters f = mapper.readValue(rs.getString("filters"), ReportDtos.RunFilters.class);
            java.sql.Array arr = rs.getArray("recipients");
            List<UUID> recipients = new ArrayList<>();
            List<String> names = new ArrayList<>();
            if (arr != null) {
                for (Object o : (Object[]) arr.getArray()) {
                    UUID pid = (UUID) o;
                    recipients.add(pid);
                    try {
                        names.add(persons.get((UUID) rs.getObject("organization_id"), pid).fullName());
                    } catch (RuntimeException ignored) {
                        names.add("—");
                    }
                }
            }
            Timestamp lastRun = rs.getTimestamp("last_run_at");
            return new ReportDtos.ScheduledReportView((UUID) rs.getObject("id"), rs.getString("code"), f, rs.getString("frequency"),
                    (Integer) rs.getObject("day_of_week"), (Integer) rs.getObject("day_of_month"), rs.getString("format"), recipients, names,
                    rs.getString("status"), lastRun == null ? null : lastRun.toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
