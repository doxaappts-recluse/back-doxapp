package pe.dcs.app.features.contract.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.contract.domain.Contract;
import pe.dcs.app.features.contract.domain.ContractExpiryNotice;
import pe.dcs.app.features.contract.domain.ContractExpiryNoticeRepository;
import pe.dcs.app.features.contract.domain.ContractRepository;
import pe.dcs.app.features.contract.domain.ContractStatus;
import pe.dcs.app.features.contract.dto.MaintenanceResponse;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.mail.MailMessage;
import pe.dcs.app.features.notification.domain.NotificationType;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.shared.mail.MailPort;
import pe.dcs.app.shared.tx.AfterCommit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M03 · Tareas del ciclo de vida (idempotentes; se ejecutan cada hora y al arrancar):
 * <ol>
 *   <li>Inicia las renovaciones PENDING cuyo inicio llegó: el contrato anterior pasa a REPLACED.</li>
 *   <li>Vence (EXPIRED) los contratos PENDING/ACTIVE/SUSPENDED cuyo {@code endDate} ya pasó (día de la organización).</li>
 *   <li>Avisa a los ORG_ADMIN a 30/15/7 días del vencimiento, una sola vez por umbral (tabla {@code contract_expiry_notice}).</li>
 * </ol>
 * El correo sale por {@link MailPort}; hasta que exista M19 solo queda en el log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractLifecycleService {

    static final int[] THRESHOLDS = {7, 15, 30};

    private final ContractRepository contracts;
    private final ContractExpiryNoticeRepository notices;
    private final OrganizationRepository organizations;
    private final ContractGate gate;
    private final AuditService audit;
    private final MailPort mail;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional
    public MaintenanceResponse run() {
        Instant now = clock.instant();
        Map<UUID, Organization> orgs = new HashMap<>();
        Set<UUID> touched = new HashSet<>();

        int started = 0;
        for (Contract c : contracts.findByStatusIn(List.of(ContractStatus.PENDING))) {
            if (c.getPreviousContractId() == null) {
                continue;                                                                   // un alta nueva solo se activa a mano
            }
            Organization o = org(orgs, c.getOrganizationId());
            LocalDate today = today(o);
            if (o.getStatus() == OrganizationStatus.CLOSED || c.getStartDate().isAfter(today) || c.getEndDate().isBefore(today)) {
                continue;
            }
            contracts.findById(c.getPreviousContractId())
                    .filter(p -> p.getStatus() == ContractStatus.ACTIVE || p.getStatus() == ContractStatus.SUSPENDED)
                    .ifPresent(p -> {
                        p.setStatus(ContractStatus.REPLACED);
                        p.setReplacedAt(now);
                        contracts.save(p);
                        record("REPLACED", p, Map.of("replacedBy", c.getId().toString()));
                    });
            c.setStatus(ContractStatus.ACTIVE);
            c.setActivatedAt(now);
            contracts.save(c);
            record("ACTIVATE", c, Map.of("status", "ACTIVE", "by", "job"));
            touched.add(c.getOrganizationId());
            started++;
        }

        int expired = 0;
        int sent = 0;
        for (Contract c : contracts.findByStatusIn(List.of(ContractStatus.PENDING, ContractStatus.ACTIVE, ContractStatus.SUSPENDED))) {
            Organization o = org(orgs, c.getOrganizationId());
            LocalDate today = today(o);
            if (c.getEndDate().isBefore(today)) {
                c.setStatus(ContractStatus.EXPIRED);
                c.setExpiredAt(now);
                contracts.save(c);
                record("EXPIRE", c, Map.of("endDate", c.getEndDate().toString()));
                touched.add(c.getOrganizationId());
                expired++;
            } else if (c.getStatus() == ContractStatus.ACTIVE && sendNotice(c, o, today, now)) {
                sent++;
            }
        }

        touched.forEach(id -> AfterCommit.run(() -> gate.invalidate(id)));
        if (started + expired + sent > 0) {
            log.info("Contratos: {} iniciados, {} vencidos, {} avisos", started, expired, sent);
        }
        return new MaintenanceResponse(started, expired, sent);
    }

    /** Envía el aviso del umbral vigente (el menor umbral ≥ días restantes) si aún no se envió. */
    private boolean sendNotice(Contract c, Organization o, LocalDate today, Instant now) {
        long days = ChronoUnit.DAYS.between(today, c.getEndDate());
        Integer threshold = null;
        for (int t : THRESHOLDS) {
            if (days <= t) {
                threshold = t;
                break;
            }
        }
        if (threshold == null || notices.existsById(new ContractExpiryNotice.Key(c.getId(), threshold))) {
            return false;
        }
        List<String> emails = contracts.adminEmails(c.getOrganizationId());
        for (String to : emails) {
            mail.send(new MailMessage(to, "contract.expiring", o.getDefaultLanguage(), Map.of(
                    "organization", o.getName(), "days", String.valueOf(days), "endDate", c.getEndDate().toString())));
        }
        notifications.toPersons(NotificationType.CONTRACT_EXPIRING, c.getOrganizationId(), notifications.orgAdmins(c.getOrganizationId()),
                Map.of("days", String.valueOf(days), "endDate", c.getEndDate().toString()), "/app/contract", "contract:" + c.getId() + ":" + threshold);
        ContractExpiryNotice n = new ContractExpiryNotice();
        n.setContractId(c.getId());
        n.setThresholdDays(threshold);
        n.setSentAt(now);
        n.setRecipients(emails.size());
        notices.save(n);
        record("EXPIRY_NOTICE", c, Map.of("threshold", threshold, "daysLeft", days, "recipients", emails.size()));
        return true;
    }

    private Organization org(Map<UUID, Organization> cache, UUID id) {
        return cache.computeIfAbsent(id, k -> organizations.findById(k).orElseThrow());
    }

    private LocalDate today(Organization o) {
        return LocalDate.now(clock.withZone(ZoneId.of(o.getTimezone())));
    }

    private void record(String action, Contract c, Map<String, Object> diff) {
        audit.record(new AuditService.Command(ContractService.MODULE, action, ContractService.ENTITY, c.getId(),
                c.getOrganizationId(), c.getBranchId(), diff));
    }
}
