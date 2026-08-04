package pe.dcs.app.features.bible_academy.service;

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
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface BibleAcademyService {

    /** Buscar persona por DNI para asignarla como maestro o matricularla — mismo patrón que SmallGroup. */
    BiblePersonSearchResponse findPersonByDni(String dni);

    // Malla curricular
    PageResponse<BibleCurriculumSearchRowResponse> searchCurriculums(BibleCurriculumSearchRequest request);

    BibleCurriculumDetailResponse getCurriculumById(UUID id);

    UUID createCurriculum(BibleCurriculumFormRequest request);

    void updateCurriculum(UUID id, BibleCurriculumFormRequest request);

    void activateCurriculum(UUID id);

    void retireCurriculum(UUID id);

    // Cursos (malla y extra)
    PageResponse<BibleCourseResponse> searchCourses(BibleCourseSearchRequest request);

    BibleCourseResponse getCourseById(UUID id);

    UUID createCourse(BibleCourseFormRequest request);

    void updateCourse(UUID id, BibleCourseFormRequest request);

    // Dictados
    PageResponse<BibleClassSearchRowResponse> searchClasses(BibleClassSearchRequest request);

    BibleClassDetailResponse getClassById(UUID id);

    UUID createClass(BibleClassFormRequest request);

    void updateClass(UUID id, BibleClassFormRequest request);

    // Matrículas
    PageResponse<BibleEnrollmentResponse> searchEnrollments(UUID classId, BibleEnrollmentSearchRequest request);

    UUID createEnrollment(UUID classId, BibleEnrollmentFormRequest request);

    void updateEnrollmentStatus(UUID classId, UUID enrollmentId, BibleEnrollmentStatusUpdateRequest request);
}
