package pe.dcs.app.features.bible_academy.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.bible_academy.request.BibleClassFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleClassSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleCourseFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleCourseSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleCurriculumFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleCurriculumSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleEnrollmentFormRequest;
import pe.dcs.app.features.bible_academy.request.BibleEnrollmentSearchRequest;
import pe.dcs.app.features.bible_academy.request.BibleEnrollmentStatusUpdateRequest;
import pe.dcs.app.features.bible_academy.response.BibleClassDetailResponse;
import pe.dcs.app.features.bible_academy.response.BibleClassSearchRowResponse;
import pe.dcs.app.features.bible_academy.response.BibleCourseResponse;
import pe.dcs.app.features.bible_academy.response.BibleCurriculumDetailResponse;
import pe.dcs.app.features.bible_academy.response.BibleCurriculumSearchRowResponse;
import pe.dcs.app.features.bible_academy.response.BibleEnrollmentResponse;
import pe.dcs.app.features.bible_academy.response.BiblePersonSearchResponse;
import pe.dcs.app.features.bible_academy.service.BibleAcademyService;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * Academia Bíblica: malla curricular (exclusiva de org admin), cursos
 * (de malla o extra por sede), dictados y matrículas. SYSTEM no tiene
 * acceso — ver BibleAcademyAccessGuard.
 */
@RestController
@RequestMapping("/api/v1/bible-academy")
@RequiredArgsConstructor
public class BibleAcademyController {

    private final BibleAcademyService service;

    @GetMapping("/find-by-dni")
    public ApiResponse<BiblePersonSearchResponse> findPersonByDni(@RequestParam String dni) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Persona encontrada correctamente",
                service.findPersonByDni(dni)
        );
    }

    // =====================================================
    // MALLA CURRICULAR
    // =====================================================

    @PostMapping("/curriculums/search")
    public ApiResponse<PageResponse<BibleCurriculumSearchRowResponse>> searchCurriculums(
            @RequestBody BibleCurriculumSearchRequest request
    ) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Mallas curriculares obtenidas correctamente",
                service.searchCurriculums(request)
        );
    }

    @GetMapping("/curriculums/{id}")
    public ApiResponse<BibleCurriculumDetailResponse> getCurriculumById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Malla curricular obtenida correctamente",
                service.getCurriculumById(id)
        );
    }

    @PostMapping("/curriculums/create")
    public ApiResponse<UUID> createCurriculum(@RequestBody BibleCurriculumFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Malla curricular registrada correctamente",
                service.createCurriculum(request)
        );
    }

    @PutMapping("/curriculums/update/{id}")
    public ApiResponse<String> updateCurriculum(@PathVariable UUID id, @RequestBody BibleCurriculumFormRequest request) {
        service.updateCurriculum(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Malla curricular actualizada correctamente", "OK");
    }

    @PostMapping("/curriculums/{id}/activate")
    public ApiResponse<String> activateCurriculum(@PathVariable UUID id) {
        service.activateCurriculum(id);
        return new ApiResponse<>(HttpStatus.OK.value(), "Malla curricular activada correctamente", "OK");
    }

    @PostMapping("/curriculums/{id}/retire")
    public ApiResponse<String> retireCurriculum(@PathVariable UUID id) {
        service.retireCurriculum(id);
        return new ApiResponse<>(HttpStatus.OK.value(), "Malla curricular retirada correctamente", "OK");
    }

    // =====================================================
    // CURSOS
    // =====================================================

    @PostMapping("/courses/search")
    public ApiResponse<PageResponse<BibleCourseResponse>> searchCourses(@RequestBody BibleCourseSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Cursos obtenidos correctamente",
                service.searchCourses(request)
        );
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<BibleCourseResponse> getCourseById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Curso obtenido correctamente",
                service.getCourseById(id)
        );
    }

    @PostMapping("/courses/create")
    public ApiResponse<UUID> createCourse(@RequestBody BibleCourseFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Curso registrado correctamente",
                service.createCourse(request)
        );
    }

    @PutMapping("/courses/update/{id}")
    public ApiResponse<String> updateCourse(@PathVariable UUID id, @RequestBody BibleCourseFormRequest request) {
        service.updateCourse(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Curso actualizado correctamente", "OK");
    }

    // =====================================================
    // DICTADOS
    // =====================================================

    @PostMapping("/classes/search")
    public ApiResponse<PageResponse<BibleClassSearchRowResponse>> searchClasses(@RequestBody BibleClassSearchRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Dictados obtenidos correctamente",
                service.searchClasses(request)
        );
    }

    @GetMapping("/classes/{id}")
    public ApiResponse<BibleClassDetailResponse> getClassById(@PathVariable UUID id) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Dictado obtenido correctamente",
                service.getClassById(id)
        );
    }

    @PostMapping("/classes/create")
    public ApiResponse<UUID> createClass(@RequestBody BibleClassFormRequest request) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Dictado registrado correctamente",
                service.createClass(request)
        );
    }

    @PutMapping("/classes/update/{id}")
    public ApiResponse<String> updateClass(@PathVariable UUID id, @RequestBody BibleClassFormRequest request) {
        service.updateClass(id, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Dictado actualizado correctamente", "OK");
    }

    // =====================================================
    // MATRÍCULAS
    // =====================================================

    @PostMapping("/classes/{classId}/enrollments/search")
    public ApiResponse<PageResponse<BibleEnrollmentResponse>> searchEnrollments(
            @PathVariable UUID classId,
            @RequestBody BibleEnrollmentSearchRequest request
    ) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Matrículas obtenidas correctamente",
                service.searchEnrollments(classId, request)
        );
    }

    @PostMapping("/classes/{classId}/enrollments")
    public ApiResponse<UUID> createEnrollment(
            @PathVariable UUID classId,
            @RequestBody BibleEnrollmentFormRequest request
    ) {
        return new ApiResponse<>(
                HttpStatus.OK.value(),
                "Matrícula registrada correctamente",
                service.createEnrollment(classId, request)
        );
    }

    @PutMapping("/classes/{classId}/enrollments/{enrollmentId}/status")
    public ApiResponse<String> updateEnrollmentStatus(
            @PathVariable UUID classId,
            @PathVariable UUID enrollmentId,
            @RequestBody BibleEnrollmentStatusUpdateRequest request
    ) {
        service.updateEnrollmentStatus(classId, enrollmentId, request);
        return new ApiResponse<>(HttpStatus.OK.value(), "Matrícula actualizada correctamente", "OK");
    }
}
