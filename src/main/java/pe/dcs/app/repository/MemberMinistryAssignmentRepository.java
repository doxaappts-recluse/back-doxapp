package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.MemberMinistryAssignment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MemberMinistryAssignmentRepository extends JpaRepository<MemberMinistryAssignment, UUID> {

    // =====================================================
    // FIND BY USER
    // =====================================================

    List<MemberMinistryAssignment>
    findByUserIdOrderByMinistryNameAscStartDateDesc(
            UUID userId
    );

    // =====================================================
    // OPEN ASSIGNMENT SAME MINISTRY
    // =====================================================

    boolean existsByUserIdAndMinistryIdAndEndDateIsNull(
            UUID userId,
            UUID ministryId
    );

    boolean existsByUserIdAndMinistryIdAndEndDateIsNullAndIdNot(
            UUID userId,
            UUID ministryId,
            UUID assignmentId
    );

    // =====================================================
    // DATE OVERLAP (CREATE)
    // =====================================================
    @Query("""
        SELECT COUNT(a) > 0
        FROM MemberMinistryAssignment a
        WHERE a.user.id = :userId
        AND a.ministry.id = :ministryId
        AND :startDate <= COALESCE(a.endDate, :maxDate)
        AND :endDate >= a.startDate
    """)
    boolean existsOverlapClosed(
            @Param("userId") UUID userId,
            @Param("ministryId") UUID ministryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("maxDate") LocalDate maxDate
    );

    @Query("""
        SELECT COUNT(a) > 0
        FROM MemberMinistryAssignment a
        WHERE a.user.id = :userId
        AND a.ministry.id = :ministryId
        AND :startDate <= COALESCE(a.endDate, :maxDate)
    """)
    boolean existsOverlapOpen(
            @Param("userId") UUID userId,
            @Param("ministryId") UUID ministryId,
            @Param("startDate") LocalDate startDate,
            @Param("maxDate") LocalDate maxDate
    );

    // =====================================================
    // DATE OVERLAP (UPDATE)
    // =====================================================
    @Query("""
        SELECT COUNT(a) > 0
        FROM MemberMinistryAssignment a
        WHERE a.user.id = :userId
        AND a.ministry.id = :ministryId
        AND a.id <> :assignmentId
        AND :startDate <= COALESCE(a.endDate, :maxDate)
        AND :endDate >= a.startDate
    """)
    boolean existsOverlapClosedExcludingSelf(
            @Param("userId") UUID userId,
            @Param("ministryId") UUID ministryId,
            @Param("assignmentId") UUID assignmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("maxDate") LocalDate maxDate
    );

    @Query("""
        SELECT COUNT(a) > 0
        FROM MemberMinistryAssignment a
        WHERE a.user.id = :userId
        AND a.ministry.id = :ministryId
        AND a.id <> :assignmentId
        AND :startDate <= COALESCE(a.endDate, :maxDate)
    """)
    boolean existsOverlapOpenExcludingSelf(
            @Param("userId") UUID userId,
            @Param("ministryId") UUID ministryId,
            @Param("assignmentId") UUID assignmentId,
            @Param("startDate") LocalDate startDate,
            @Param("maxDate") LocalDate maxDate
    );

    @Query("""
        SELECT a
        FROM MemberMinistryAssignment a
        JOIN FETCH a.ministry m
        LEFT JOIN FETCH a.ministryRole r
        WHERE a.user.id = :userId
        ORDER BY m.name ASC, a.startDate DESC
    """)
    List<MemberMinistryAssignment> findAllByUserId(
            @Param("userId") UUID userId
    );
}