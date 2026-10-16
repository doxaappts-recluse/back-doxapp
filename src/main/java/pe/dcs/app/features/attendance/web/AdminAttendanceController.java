package pe.dcs.app.features.attendance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.attendance.service.AttendanceQrService;
import pe.dcs.app.features.attendance.service.AttendanceService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * M09 · Asistencia. V consultar · C crear la sesión · E abrir y cerrar · A reabrir · T tomar asistencia (registrar, lista, QR, visitantes) ·
 * D borrar una sesión vacía · X exportar. El ujier (ORG_USER) solo necesita T (y V de la sesión abierta).
 */
@RestController
@RequestMapping("/api/v1/admin/attendance")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private static final String MODULE = "ATTENDANCE";

    private final AttendanceService service;
    private final AttendanceQrService qr;
    private final AccessScopeResolver resolver;
    private final Clock clock;

    @PostMapping("/sessions/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<AttendanceDtos.SessionSummary>> search(@RequestBody(required = false) AttendanceDtos.SessionSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<AttendanceDtos.SessionResponse> create(@RequestBody AttendanceDtos.SessionCreate req) {
        return new ApiResponse<>(201, "ok.attendance.sessionCreated", service.create(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/sessions/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AttendanceDtos.SessionResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping("/sessions/{id}/open")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<AttendanceDtos.SessionResponse> open(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.attendance.opened", service.open(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/sessions/{id}/close")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<AttendanceDtos.SessionResponse> close(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.attendance.closed", service.close(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/sessions/{id}/reopen")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<AttendanceDtos.SessionResponse> reopen(@PathVariable UUID id, @RequestBody AttendanceDtos.ReopenRequest req) {
        return new ApiResponse<>(200, "ok.attendance.reopened", service.reopen(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/sessions/{id}/anonymous")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<AttendanceDtos.SessionResponse> anonymous(@PathVariable UUID id, @RequestBody AttendanceDtos.AnonymousRequest req) {
        return new ApiResponse<>(200, "ok.attendance.recorded", service.setAnonymous(resolver.current(), id, req));
    }

    @DeleteMapping("/sessions/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.current(), id);
        return new ApiResponse<>(200, "ok.attendance.sessionDeleted", null);
    }

    @GetMapping("/sessions/{id}/records")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<AttendanceDtos.RecordItem>> records(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.records(resolver.current(), id));
    }

    @PostMapping("/sessions/{id}/roster")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<PageResponse<AttendanceDtos.RosterItem>> roster(@PathVariable UUID id, @RequestBody(required = false) AttendanceDtos.RosterSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.roster(resolver.current(), id, req));
    }

    /** Idempotente [V5]: registrar de nuevo a la misma persona no duplica. */
    @PostMapping("/sessions/{id}/records")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<AttendanceDtos.RecordResult> record(@PathVariable UUID id, @RequestBody AttendanceDtos.RecordRequest req) {
        return new ApiResponse<>(200, "ok.attendance.recorded", service.record(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/sessions/{id}/records/batch")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<AttendanceDtos.BatchResult> batch(@PathVariable UUID id, @RequestBody AttendanceDtos.BatchRequest req) {
        return new ApiResponse<>(200, "ok.attendance.recorded", service.batch(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/sessions/{id}/records/{personId}")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<Void> removeRecord(@PathVariable UUID id, @PathVariable UUID personId) {
        service.removeRecord(resolver.current(), id, personId);
        return new ApiResponse<>(200, "ok.attendance.recorded", null);
    }

    /** El ujier escanea el QR personal de alguien. */
    @PostMapping("/checkin/qr")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<AttendanceDtos.RecordResult> scan(@RequestBody AttendanceDtos.QrScanRequest req) {
        return new ApiResponse<>(200, "ok.attendance.recorded", qr.scan(resolver.actor(), resolver.current(), req));
    }

    /** QR del culto (solo si el culto activa el autoservicio). */
    @GetMapping("/sessions/{id}/qr")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AttendanceDtos.QrToken> sessionQr(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", qr.sessionQr(resolver.current(), id));
    }

    /** Mi QR personal (para quien tiene acceso al sistema; el portal del miembro lo ofrecerá en M24). */
    @GetMapping("/me/qr")
    @ModuleAccess(module = "MY_ACCOUNT", action = Action.V)
    public ApiResponse<AttendanceDtos.QrToken> myQr(@RequestParam(required = false) UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", qr.issueMine(resolver.actor(), branchId));
    }

    /** Autoservicio: quien tiene acceso escanea el QR del culto. */
    @PostMapping("/checkin/self")
    @ModuleAccess(module = "MY_ACCOUNT", action = Action.V)
    public ApiResponse<AttendanceDtos.RecordResult> self(@RequestBody AttendanceDtos.SelfRequest req) {
        return new ApiResponse<>(200, "ok.attendance.recorded", qr.self(resolver.actor(), req));
    }

    @PostMapping("/trends")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AttendanceDtos.Trends> trends(@RequestBody(required = false) AttendanceDtos.TrendsRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.trends(resolver.current(), req));
    }

    @GetMapping("/sessions/{id}/export")
    @ModuleAccess(module = MODULE, action = Action.X)
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        AttendanceService.ExportResult ex = service.exportSession(resolver.actor(), resolver.current(), id);
        String name = "asistencia-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(clock.instant().atZone(ZoneOffset.UTC)) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Export-Rows", String.valueOf(ex.rows()))
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(ex.content());
    }
}
