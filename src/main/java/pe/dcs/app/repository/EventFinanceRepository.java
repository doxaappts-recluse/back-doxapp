package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.EventFinance;
import pe.dcs.app.util.enums.events.EventFinanceStatus;
import pe.dcs.app.util.enums.events.EventFinanceType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventFinanceRepository extends JpaRepository<EventFinance, UUID>, JpaSpecificationExecutor<EventFinance> {
    @Query("""
        SELECT COALESCE(SUM(f.amount),0)
        FROM EventFinance f
        WHERE f.event.id = :eventId
        AND f.type = 'INCOME'
        AND f.status = 'APPROVED'
    """)
    BigDecimal sumIncomeApproved(UUID eventId);

    @Query("""
        SELECT COALESCE(SUM(f.amount),0)
        FROM EventFinance f
        WHERE f.event.id = :eventId
        AND f.type = 'EXPENSE'
        AND f.status = 'APPROVED'
    """)
    BigDecimal sumExpenseApproved(UUID eventId);

    @Query("""
        SELECT COALESCE(SUM(f.amount),0)
        FROM EventFinance f
        WHERE f.event.id = :eventId
        AND f.status = 'PENDING'
    """)
    BigDecimal sumPending(UUID eventId);

    long countByEventId(UUID eventId);

    @Query("""
        SELECT COALESCE(SUM(f.amount), 0)
        FROM EventFinance f
        WHERE f.event.id = :eventId
          AND f.status = 'PENDING'
          AND f.type = :type
    """)
    BigDecimal sumPendingByType(
            @Param("eventId") UUID eventId,
            @Param("type") EventFinanceType type
    );

    @Query("""
        SELECT COALESCE(SUM(f.amount), 0)
        FROM EventFinance f
        WHERE f.event.id = :eventId
          AND f.status = :status
          AND f.type = :type
    """)
    BigDecimal sumByStatusAndType(
            UUID eventId,
            EventFinanceStatus status,
            EventFinanceType type
    );

    @Query("""
        SELECT
            DATE(f.createdAt),
            SUM(CASE WHEN f.type = 'INCOME' AND f.status = 'APPROVED' THEN f.amount ELSE 0 END),
            SUM(CASE WHEN f.type = 'EXPENSE' AND f.status = 'APPROVED' THEN f.amount ELSE 0 END)
        FROM EventFinance f
        WHERE f.event.id = :eventId
        GROUP BY DATE(f.createdAt)
        ORDER BY DATE(f.createdAt)
    """)
    List<Object[]> financeReport(@Param("eventId") UUID eventId);
}