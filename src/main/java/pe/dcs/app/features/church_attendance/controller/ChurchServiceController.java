package pe.dcs.app.features.church_attendance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.church_attendance.request.ChurchServiceAttendanceFormRequest;
import pe.dcs.app.features.church_attendance.request.ChurchServiceFormRequest;
import pe.dcs.app.features.church_attendance.request.ChurchServiceSearchRequest;
import pe.dcs.app.features.church_attendance.response.ChurchPersonSearchResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceAttendanceResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceDetailResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceSearchRowResponse;
import pe.dcs.app.features.church_attendance.service.ChurchServiceService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Asistencia a Cultos: catálogo de cultos (búsqueda, detalle,
 * crear/editar) y registro de asistencia por fecha. Reutiliza el
 * módulo/permisos de Seguimiento Pastoral (PASTORAL_FOLLOWUP) — forma
 * parte del paquete comercial "CRM Pastoral", sin ruta ni módulo
 * propios en el contrato.
 */
@RestController
@RequestMapping("/api/v1/church-services")
@RequiredArgsConstructor
public class ChurchServiceController {

    private final ChurchServiceService service;

    @PostMapping("/search")
    public ApiResponse<PageResponse<ChurchServiceSearchRowResponse>> search(
            @RequestBody ChurchServiceSearchRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Cultos obtenidos correctamente",
                service.search(request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ChurchServiceDetailResponse> getById(
            @PathVariable UUID id
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Culto obtenido correctamente",
                service.getById(id)
        );
    }

    @GetMapping("/find-by-dni")
    public ApiResponse<ChurchPersonSearchResponse> findPersonByDni(
            @RequestParam String dni
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Persona encontrada correctamente",
                service.findPersonByDni(dni)
        );
    }

    @PostMapping("/create")
    public ApiResponse<UUID> create(
            @RequestBody ChurchServiceFormRequest request
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Culto registrado correctamente",
                service.create(request)
        );
    }

    @PutMapping("/update/{id}")
    public ApiResponse<String> update(
            @PathVariable UUID id,
            @RequestBody ChurchServiceFormRequest request
    ) {

        service.update(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Culto actualizado correctamente",
                "OK"
        );
    }

    @GetMapping("/{id}/attendance")
    public ApiResponse<List<ChurchServiceAttendanceResponse>> listAttendance(
            @PathVariable UUID id,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Asistencia obtenida correctamente",
                service.listAttendance(id, date)
        );
    }

    @PostMapping("/{id}/attendance")
    public ApiResponse<String> markAttendance(
            @PathVariable UUID id,
            @RequestBody ChurchServiceAttendanceFormRequest request
    ) {

        service.markAttendance(id, request);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Asistencia registrada correctamente",
                "OK"
        );
    }

    @DeleteMapping("/{id}/attendance/{attendanceId}")
    public ApiResponse<String> removeAttendance(
            @PathVariable UUID id,
            @PathVariable UUID attendanceId
    ) {

        service.removeAttendance(id, attendanceId);

        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Asistencia removida correctamente",
                "OK"
        );
    }
}
