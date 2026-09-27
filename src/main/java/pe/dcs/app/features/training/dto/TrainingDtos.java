package pe.dcs.app.features.training.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M13 · Formación: malla curricular (/admin/curricula), dictados (/admin/classes) y matrículas (/admin/enrollments). */
public final class TrainingDtos {

    private TrainingDtos() {
    }

    // ---------------------------------------------------------------- malla y cursos

    public record CurriculumRequest(String name, String description, Long version) {
    }

    public record CurriculumSummary(UUID id, String name, String description, String status, int courseCount, int classCount, Instant createdAt, Long version) {
    }

    public record CourseView(UUID id, Integer order, String name, String description, int hours, Integer minAttendancePct, String passGrade, String status,
                             UUID branchId, String branchName, int classCount, Long version) {
    }

    public record CurriculumResponse(CurriculumSummary summary, List<CourseView> courses) {
    }

    public record CourseRequest(String name, String description, Integer hours, Integer minAttendancePct, String passGrade, UUID branchId, Long version) {
    }

    public record ReorderRequest(List<UUID> courseIds) {
    }

    // ---------------------------------------------------------------- dictados (course_class)

    public record ClassSearch(ClassFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record ClassFilters(UUID branchId, UUID courseId, UUID teacherId, String status, LocalDate from, LocalDate to, Boolean mine) {
        }
    }

    public record ClassRequest(UUID courseId, UUID branchId, UUID teacherId, Integer dayOfWeek, LocalTime startTime, LocalTime endTime, String location,
                               LocalDate startDate, LocalDate endDate, Integer capacity, UUID spaceId, Long version) {
    }

    public record ClassSummary(UUID id, UUID courseId, String courseName, Integer courseOrder, String curriculumName, UUID branchId, String branchName,
                               UUID teacherId, String teacherName, Integer dayOfWeek, LocalTime startTime, LocalTime endTime, String location,
                               LocalDate startDate, LocalDate endDate, int capacity, String status, int enrolledCount, Instant createdAt, Long version,
                               UUID spaceId, String spaceName) {
    }

    public record EnrollmentBrief(UUID id, UUID personId, String personName, String status, String finalGrade) {
    }

    public record ClassResponse(ClassSummary summary, String cancelReason, List<EnrollmentBrief> enrollments) {
    }

    public record SessionView(LocalDate date, boolean held, int present, int total) {
    }

    public record MarkAttendanceRequest(UUID personId, String status) {
    }

    // ---------------------------------------------------------------- matrículas

    public record EnrollmentSearch(EnrollmentFilters filters, PaginationRequest pagination, List<SortRequest> sorts) {
        public record EnrollmentFilters(UUID personId, UUID classId, UUID courseId, String status, Boolean mine) {
        }
    }

    public record EnrollRequest(UUID personId, UUID classId, String overrideReason) {
    }

    public record GradeRequest(String grade, String lateReason) {
    }

    public record StatusRequest(String status, String reason) {
    }

    public record EnrollmentSummary(UUID id, UUID personId, String personName, UUID classId, String courseName, Integer courseOrder, UUID branchId,
                                    String branchName, String status, String finalGrade, Instant enrolledAt, Instant resolvedAt, Long version) {
    }

    public record EnrollmentResponse(EnrollmentSummary summary, String statusReason, String overrideReason, String overriddenByName,
                                     CertificateResponse certificate) {
    }

    // ---------------------------------------------------------------- escala de notas

    public record GradeScaleRequest(String gradeMin, String gradeMax, String gradePass, Long version) {
    }

    public record GradeScaleResponse(String gradeMin, String gradeMax, String gradePass, Long version) {
    }

    // ---------------------------------------------------------------- certificado

    public record CertificateResponse(UUID id, UUID enrollmentId, String certificateNo, String code, String status, Instant issuedAt, String issuedByName,
                                      String voidReason, Instant voidedAt, Map<String, Object> snapshot, UUID issuedDocumentId) {
    }

    public record CertificateVerification(boolean found, String status, String certificateNo, Instant issuedAt, String holder, String course,
                                          String organization, String branch) {
    }
}
