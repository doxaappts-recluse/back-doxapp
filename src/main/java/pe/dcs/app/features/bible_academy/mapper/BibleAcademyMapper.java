package pe.dcs.app.features.bible_academy.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.BibleClass;
import pe.dcs.app.entity.BibleCourse;
import pe.dcs.app.entity.BibleCurriculum;
import pe.dcs.app.entity.BibleEnrollment;
import pe.dcs.app.features.bible_academy.response.BibleClassDetailResponse;
import pe.dcs.app.features.bible_academy.response.BibleClassSearchRowResponse;
import pe.dcs.app.features.bible_academy.response.BibleCourseResponse;
import pe.dcs.app.features.bible_academy.response.BibleCurriculumDetailResponse;
import pe.dcs.app.features.bible_academy.response.BibleCurriculumSearchRowResponse;
import pe.dcs.app.features.bible_academy.response.BibleEnrollmentResponse;
import pe.dcs.app.util.auditable.BaseMapper;

import java.util.List;

@Component
public class BibleAcademyMapper {

    // =====================================================
    // CURRICULUM
    // =====================================================

    public BibleCurriculumSearchRowResponse toCurriculumSearchRow(
            BibleCurriculum curriculum,
            long courseCount,
            boolean canManage,
            boolean showAudit
    ) {

        BibleCurriculumSearchRowResponse row = new BibleCurriculumSearchRowResponse();

        BaseMapper.mapAudit(curriculum, row, showAudit);

        row.setId(curriculum.getId());
        row.setName(curriculum.getName());
        row.setDescription(curriculum.getDescription());
        row.setStatus(curriculum.getStatus());
        row.setCourseCount(courseCount);
        row.setCanManage(canManage);

        return row;
    }

    public BibleCurriculumDetailResponse toCurriculumDetailResponse(
            BibleCurriculum curriculum,
            List<BibleCourseResponse> courses,
            boolean canManage
    ) {

        BibleCurriculumDetailResponse response = new BibleCurriculumDetailResponse();

        response.setId(curriculum.getId());
        response.setName(curriculum.getName());
        response.setDescription(curriculum.getDescription());
        response.setStatus(curriculum.getStatus());
        response.setCanManage(canManage);
        response.setCourses(courses);

        return response;
    }

    // =====================================================
    // COURSE
    // =====================================================

    public BibleCourseResponse toCourseResponse(
            BibleCourse course,
            long classCount,
            boolean canManage,
            boolean showAudit
    ) {

        BibleCourseResponse response = new BibleCourseResponse();

        BaseMapper.mapAudit(course, response, showAudit);

        response.setId(course.getId());
        response.setName(course.getName());
        response.setDescription(course.getDescription());
        response.setOrder(course.getOrder());
        response.setExtra(course.isExtra());
        response.setStatus(course.getStatus());
        response.setClassCount(classCount);
        response.setCanManage(canManage);

        if (course.getCurriculum() != null) {
            response.setCurriculumId(course.getCurriculum().getId());
            response.setCurriculumName(course.getCurriculum().getName());
        }

        if (course.getBranch() != null) {
            response.setBranchId(course.getBranch().getId());
            response.setBranchName(course.getBranch().getName());
        }

        return response;
    }

    // =====================================================
    // CLASS (DICTADO)
    // =====================================================

    public BibleClassSearchRowResponse toClassSearchRow(
            BibleClass bibleClass,
            long enrolledCount,
            boolean canManage,
            boolean showAudit
    ) {

        BibleClassSearchRowResponse row = new BibleClassSearchRowResponse();

        BaseMapper.mapAudit(bibleClass, row, showAudit);

        row.setId(bibleClass.getId());
        row.setTeacherName(resolveTeacherName(bibleClass));
        row.setMeetingDay(bibleClass.getMeetingDay());
        row.setMeetingTime(bibleClass.getMeetingTime());
        row.setLocation(bibleClass.getLocation());
        row.setStartDate(bibleClass.getStartDate());
        row.setEndDate(bibleClass.getEndDate());
        row.setCapacity(bibleClass.getCapacity());
        row.setEnrolledCount(enrolledCount);
        row.setStatus(bibleClass.getStatus());
        row.setCanManage(canManage);

        BibleCourse course = bibleClass.getCourse();

        if (course != null) {
            row.setCourseId(course.getId());
            row.setCourseName(course.getName());
            row.setCourseExtra(course.isExtra());

            if (course.getCurriculum() != null) {
                row.setCurriculumName(course.getCurriculum().getName());
            }
        }

        if (bibleClass.getBranch() != null) {
            row.setBranchId(bibleClass.getBranch().getId());
            row.setBranchName(bibleClass.getBranch().getName());
        }

        if (bibleClass.getTeacherPerson() != null) {
            row.setTeacherPersonId(bibleClass.getTeacherPerson().getId());
        }

        return row;
    }

    public BibleClassDetailResponse toClassDetailResponse(
            BibleClass bibleClass,
            long enrolledCount,
            boolean canManage
    ) {

        BibleClassDetailResponse response = new BibleClassDetailResponse();

        response.setId(bibleClass.getId());
        response.setTeacherName(resolveTeacherName(bibleClass));
        response.setMeetingDay(bibleClass.getMeetingDay());
        response.setMeetingTime(bibleClass.getMeetingTime());
        response.setLocation(bibleClass.getLocation());
        response.setStartDate(bibleClass.getStartDate());
        response.setEndDate(bibleClass.getEndDate());
        response.setCapacity(bibleClass.getCapacity());
        response.setEnrolledCount(enrolledCount);
        response.setStatus(bibleClass.getStatus());
        response.setCanManage(canManage);

        BibleCourse course = bibleClass.getCourse();

        if (course != null) {
            response.setCourseId(course.getId());
            response.setCourseName(course.getName());
            response.setCourseExtra(course.isExtra());

            if (course.getCurriculum() != null) {
                response.setCurriculumId(course.getCurriculum().getId());
                response.setCurriculumName(course.getCurriculum().getName());
                response.setCurriculumOrder(course.getOrder());
            }
        }

        if (bibleClass.getBranch() != null) {
            response.setBranchId(bibleClass.getBranch().getId());
            response.setBranchName(bibleClass.getBranch().getName());
        }

        if (bibleClass.getTeacherPerson() != null) {
            response.setTeacherPersonId(bibleClass.getTeacherPerson().getId());
            response.setTeacherDni(bibleClass.getTeacherPerson().getDni());
        }

        if (bibleClass.getMinistryAssignment() != null) {
            response.setMinistryAssignmentId(bibleClass.getMinistryAssignment().getId());
        }

        return response;
    }

    // =====================================================
    // ENROLLMENT
    // =====================================================

    public BibleEnrollmentResponse toEnrollmentResponse(
            BibleEnrollment enrollment,
            boolean canManage,
            boolean showAudit
    ) {

        BibleEnrollmentResponse response = new BibleEnrollmentResponse();

        BaseMapper.mapAudit(enrollment, response, showAudit);

        response.setId(enrollment.getId());
        response.setBibleClassId(enrollment.getBibleClass().getId());
        response.setEnrollDate(enrollment.getEnrollDate());
        response.setStatus(enrollment.getStatus());
        response.setFinalGrade(enrollment.getFinalGrade());
        response.setStatusReason(enrollment.getStatusReason());
        response.setPrerequisiteOverridden(enrollment.isPrerequisiteOverridden());
        response.setOverrideReason(enrollment.getOverrideReason());
        response.setCanManage(canManage);

        if (enrollment.getPerson() != null) {
            response.setPersonId(enrollment.getPerson().getId());
            response.setPersonName(enrollment.getPerson().getName());
            response.setPersonLastname(enrollment.getPerson().getLastname());
            response.setPersonDni(enrollment.getPerson().getDni());
        }

        return response;
    }

    private String resolveTeacherName(BibleClass bibleClass) {

        if (bibleClass.getTeacherPerson() != null) {
            return bibleClass.getTeacherPerson().getName()
                    + " "
                    + bibleClass.getTeacherPerson().getLastname();
        }

        return bibleClass.getTeacherName();
    }
}
