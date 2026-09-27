package pe.dcs.app.features.portal.dto;

import pe.dcs.app.shared.vo.DocumentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M24 · DTOs del portal del miembro. Registros simples (sin lógica): la validación vive en los servicios. */
public final class PortalDtos {

    private PortalDtos() {
    }

    // ---------------------------------------------------------------- ajustes (admin)
    public record HomeBlock(String code, int order, boolean enabled) {
    }

    public record SettingsResponse(UUID branchId, String branchName, String signupMode, boolean directoryEnabled, boolean allowChildrenView,
                                   String legalTextVersion, List<HomeBlock> homeBlocks, String welcomeTextEs, String welcomeTextEn) {
    }

    public record SettingsRequest(String signupMode, Boolean directoryEnabled, Boolean allowChildrenView, List<HomeBlock> homeBlocks,
                                  String welcomeTextEs, String welcomeTextEn) {
    }

    // ---------------------------------------------------------------- invitaciones y acceso (admin)
    public record InviteRequest(UUID personId, DocumentType docType, String docNumber, String firstName, String lastName, String email,
                                String phone, UUID branchId) {
    }

    public record AccessRow(UUID personId, String fullName, String docNumber, UUID branchId, String branchName, String accessStatus, UUID accessId) {
    }

    public record DisableRequest(String reason) {
    }

    // ---------------------------------------------------------------- autorregistro público
    public record PublicBranchOption(String code, String name) {
    }

    public record SignupPublicConfig(String orgName, boolean open, List<PublicBranchOption> branches, String welcomeText, String legalTextVersion) {
    }

    public record SignupPublicRequest(String docType, String docNumber, String firstName, String lastName, LocalDate birthDate, String email,
                                      String phone, String branchCode, Boolean consent, String website) {
    }

    // ---------------------------------------------------------------- /portal/me
    public record ProfileResponse(String fullName, String docType, String docNumber, String email, String phone, LocalDate birthDate,
                                  String branchName, String status) {
    }

    public record MyRequestRow(UUID id, String type, String status, Instant createdAt, Instant decidedAt, String reason) {
    }

    public record HomeResponse(String welcomeText, List<HomeBlock> blocks, boolean directoryEnabled) {
    }

    public record DirectoryPreferenceResponse(boolean visible, boolean phone, boolean email, boolean photo, boolean groups) {
    }

    public record DirectoryPreferenceRequest(boolean visible, boolean phone, boolean email, boolean photo, boolean groups) {
    }

    public record DirectoryEntry(String fullName, String phone, String email, String branchName) {
    }

    public record PushSubscribeRequest(String endpoint, String p256dh, String auth, String userAgent) {
    }

    public record DataRequestResponse(String type, Instant requestedAt, UUID supportCaseId) {
    }

    /** M24 N4 · bloque DOCUMENTS (seguimiento 2026-09-25): certificados propios emitidos por el motor de M18 (M08/M13). */
    public record MyDocumentRow(UUID id, String type, String documentNo, String status, Instant issuedAt) {
    }

    /** M24 N4 · bloque FAMILY (seguimiento 2026-09-25): hogar propio (M06), integrantes según {@code allowChildrenView}. */
    public record HouseholdMemberRow(UUID personId, String fullName, String role, boolean guardian, boolean isSelf, LocalDate birthDate) {
    }

    public record MyFamilyResponse(String householdName, List<HouseholdMemberRow> members) {
    }

    /** M24 N4 · bloque MEMBERSHIP (seguimiento 2026-09-26): membresías propias (M08), sin `exitNotes` (cifrado, solo con H). */
    public record MembershipRow(UUID id, String branchName, String kind, String status, boolean current, LocalDate startDate,
                                LocalDate endDate, String exitReason) {
    }

    /** M24 N4 · bloque ATTENDANCE (seguimiento 2026-09-26): historial propio de M09, vía el índice existente {@code ix_ar_person}.
     * El QR propio y el auto-registro ya son alcanzables hoy sin código nuevo (endpoints {@code MY_ACCOUNT} de {@code AdminAttendanceController}). */
    public record AttendanceHistoryRow(UUID id, String contextType, String title, LocalDate sessionDate, String status, String method, Instant at) {
    }

    /** M24 N4 · bloque GROUPS (seguimiento 2026-09-26): grupos propios (M10, vía {@code group_member}). */
    public record MyGroupRow(UUID id, String name, String category, String audience, Integer meetingDay, LocalTime meetingTime, String location,
                             String role, LocalDate joinedAt) {
    }

    /** Directorio de grupos abiertos de la organización ({@code open_to_join = true}) — para pedir ingreso. */
    public record OpenGroupRow(UUID id, String name, String category, String audience, String branchName, Integer meetingDay, LocalTime meetingTime,
                               String location, Integer capacity, int activeCount, boolean alreadyMember, boolean alreadyRequested) {
    }

    public record GroupJoinRequest(String message) {
    }

    /** M24 N4 · bloque SERVICE (seguimiento 2026-09-26): turnos propios de M11b ({@code shift_assignment}). El comentario
     * de {@code ShiftAssignmentService.confirm/decline} ("a cargo de quien coordina; el autoservicio llega en M24") ya
     * anticipaba esta pantalla — mismas reglas de decisión (ventana de bloqueo para rechazar), repetidas aquí sin
     * {@code AccessScope} porque el candado real es "es mi propio turno", no el alcance de sede de un coordinador. */
    public record MyShiftRow(UUID id, UUID planId, UUID slotId, String ministryName, String positionName, LocalDate planDate, LocalTime startTime,
                             LocalTime endTime, String status, String notes) {
    }

    public record DeclineShiftRequest(String reason) {
    }

    /** M24 N4 · bloque PASTORAL_CARE (seguimiento 2026-09-26): casos propios de M12 ({@code pastoral_case}), SIN el
     * contenido de {@code case_note} en ningún sentido (mismo criterio que {@code exit_notes} de MEMBERSHIP) — solo
     * los datos estructurados del caso y el nombre de quien lo atiende. La creación desde el portal usa
     * {@code source = 'PORTAL'}, valor ya previsto en {@code ck_pc_source} desde la migración original de M12. */
    public record MyPastoralCaseRow(UUID id, String type, String priority, String status, String assignedName, Instant createdAt,
                                    Instant resolvedAt, Instant closedAt) {
    }

    /** {@code note} es opcional: si se envía, se guarda como {@code case_note} normal (igual que {@code initialNote} en
     * el alta admin) — el miembro es su propio autor, así que no hay problema de confidencialidad al escribirla; solo
     * al leerla de vuelta (por eso {@link MyPastoralCaseRow} nunca la incluye). */
    public record RequestPastoralCareRequest(String type, String note) {
    }

    /** Peticiones de oración propias de M12 ({@code prayer_request}). {@code testimony}/{@code answeredAt} solo se
     * completan cuando el staff marca la petición como respondida desde el panel admin. */
    public record MyPrayerRequestRow(UUID id, String text, String category, String visibility, boolean anonymous, String status, String moderation,
                                     Instant answeredAt, String testimony, Instant createdAt) {
    }

    public record SubmitPrayerRequest(String text, String category, String visibility, Boolean anonymous) {
    }

    /** M24 N4 · bloque TRAINING (seguimiento 2026-09-26): matrículas propias de M13 ({@code enrollment}). La nota final
     * es propia (no confidencial) así que sí se expone, a diferencia de {@code case_note}/{@code exit_notes}. */
    public record MyEnrollmentRow(UUID id, String courseName, int hours, LocalDate startDate, LocalDate endDate, Integer dayOfWeek, LocalTime startTime,
                                  LocalTime endTime, String location, String teacherName, String status, BigDecimal finalGrade, Instant enrolledAt) {
    }

    /** Dictados abiertos ({@code course_class} en PLANNED/IN_PROGRESS) para inscribirse. Sin ruta de excepción de
     * prerrequisito (esa requiere acción {@code O} de staff, no disponible desde el portal — {@code EnrollmentService.enroll}). */
    public record OpenClassRow(UUID id, String courseName, int hours, String branchName, LocalDate startDate, LocalDate endDate, Integer dayOfWeek,
                               LocalTime startTime, LocalTime endTime, String location, String teacherName, int capacity, int activeCount,
                               boolean alreadyEnrolled) {
    }

    public record WithdrawEnrollmentRequest(String reason) {
    }

    /** M24 N4 · bloque EVENTS (seguimiento 2026-09-26): eventos publicados + inscripciones propias de M14
     * ({@code event_registration}). El portal siempre inscribe con {@code category = 'MEMBER'} y sin acompañante
     * a cargo (sin selector de tutor: es autoservicio sobre uno mismo, igual criterio que el resto de N4);
     * {@code source = 'PUBLIC'} porque {@code ck_reg_source} solo admite STAFF/PUBLIC — no hay valor PORTAL como en
     * {@code pastoral_case} — y PUBLIC es semánticamente correcto (la persona se inscribió a sí misma, no el staff). */
    public record EventQuestionRow(UUID id, String label, String type, String options, boolean required) {
    }

    public record OpenEventRow(UUID id, String name, String typeCode, String branchName, Instant startAt, Instant endAt, String location,
                               String onlineUrl, Integer capacity, int registeredCount, boolean waitlistEnabled, Integer minAge, int guestsMax,
                               BigDecimal memberAmount, String currency, boolean alreadyRegistered, List<EventQuestionRow> questions) {
    }

    public record RegisterEventRequest(Integer guests, Map<String, String> answers) {
    }

    public record MyRegistrationRow(UUID id, UUID eventId, String eventName, Instant startAt, Instant endAt, String location, String status,
                                    String paymentStatus, BigDecimal amount, String currency, int guests, String ticketCode, Instant createdAt) {
    }

    /** Solo cubre el retiro "seguro" desde el portal: dentro del plazo de cancelación y sin pago ya confirmado (PAID
     * exige reembolso, una acción financiera de staff — {@code error.event.refundRequired} dirige a contactar la
     * iglesia). Pasado el plazo, igual: eso exige la acción {@code O} de staff ({@code error.event.cancelDeadline}). */
    public record CancelRegistrationRequest(String reason) {
    }

    /** M24 N4 · bloque GIVING (seguimiento 2026-09-26): historial propio de diezmos/ofrendas/donaciones de M15
     * ({@code fin_movement}), solo lectura — sin pasarela de pago ni conexión bancaria, decisión fija del proyecto,
     * así que aquí no hay ningún endpoint de escritura. Se ubica por {@code fin_donor.person_id = actor.ownerId()}
     * (donante ligado a la persona; si nunca donó, no tiene fila en {@code fin_donor} y la lista sale vacía, nunca
     * un error). Solo movimientos de tipo {@code INCOME} — los de egreso (planilla, mantenimiento, etc.) nunca llevan
     * un donante real y quedan fuera por construcción del filtro, no por una lista explícita de categorías.
     * Se incluyen los cuatro estados (PENDING/APPROVED/REJECTED/VOIDED) para transparencia total sobre lo propio —
     * mismo criterio que {@code MyRequestRow}/{@code MyShiftRow} — pero {@code voidReason}/{@code decidedBy} quedan
     * fuera: son la anotación interna de Finanzas, no un dato del donante (mismo criterio que {@code case_note} de
     * PASTORAL_CARE). La "constancia anual de donación" que el encabezado de {@code V26__finance_m15.sql} dejó
     * pendiente para este portal (D2) se resuelve aquí como un total por año calculado en el front sobre esta misma
     * lista (suma de {@code APPROVED}) — no como un PDF nuevo de M18: ese motor solo conoce los cuatro tipos de
     * certificado ya migrados (bautizo/matrimonio/presentación/academia bíblica) y sumarle un quinto tipo genérico
     * de donación exigiría su propia migración y plantilla, fuera del alcance ya acotado de esta entrega. */
    public record MyGivingRow(UUID id, LocalDate movementDate, String category, String fundName, BigDecimal amount, String currency, String method,
                              String receiptNo, String status, String description) {
    }

    /** M24 N4 · bloque SPACES (seguimiento 2026-09-26): espacios disponibles + reservas propias de M16 ({@code space}/
     * {@code reservation}). {@code space_rules.reservation_requesters} (STAFF/LEADERS/ANY_MEMBER) nunca se leía en
     * ningún lugar del código hasta esta entrega — quedó documentado en su migración (V27) pero sin ningún llamador
     * real: el panel admin siempre habla por sí mismo (ya es staff) y por eso nunca lo consultaba. Este bloque es su
     * primer y único punto de lectura: {@code canRequest} en {@link OpenSpacesResponse} ya viene resuelto según esa
     * regla (STAFF → false; ANY_MEMBER → true; LEADERS → true solo si el actor lidera/colidera un grupo activo
     * ({@code group_member.role}) o es líder de un ministerio de sede ({@code branch_ministry.leader_person_id})),
     * para que el front oculte el botón de solicitar sin que el miembro tenga que intentarlo primero — pero el back
     * repite la misma verificación en {@code requestSpaceReservation} (nunca confía solo en que el front lo ocultó).
     * Reservas siempre {@code source_type = 'OTHER'} (autoservicio sobre uno mismo, igual criterio que
     * {@code category = 'MEMBER'} de EVENTS) y SIN recurrencia (esa es la única pieza no replicada de
     * {@code ReservationService.submit()}: la vista previa de choques por ocurrencia es una interacción de dos pasos
     * que no aporta lo suficiente para el autoservicio de un miembro — pedir una reserva repetida sigue disponible
     * pidiendo cada fecha por separado). El resto de las reglas SÍ se replica entera: espacio ACTIVE, horario válido
     * y no pasado, duración máxima de {@code space_rules.max_duration_hours}, y el mismo choque anti-solape contra
     * CONFIRMED con el buffer del espacio, bajo el mismo bloqueo de fila que {@code ReservationService} usa para
     * serializar dos solicitudes simultáneas al mismo hueco. */
    public record OpenSpaceRow(UUID id, String name, String typeCode, String branchName, Integer capacity, List<String> equipment,
                               boolean requiresApproval, LocalTime openFrom, LocalTime openTo) {
    }

    public record OpenSpacesResponse(boolean canRequest, List<OpenSpaceRow> spaces) {
    }

    public record RequestSpaceReservationRequest(UUID spaceId, String title, Instant startAt, Instant endAt, Integer attendeesEst) {
    }

    public record MySpaceReservationRow(UUID id, String spaceName, String branchName, String title, Instant startAt, Instant endAt, String status,
                                        String decisionReason, Instant createdAt) {
    }

    /** M24 N4 · bloque MY_WORK (seguimiento 2026-09-26): ficha laboral propia + boletas de M17 ({@code staff_member}/
     * {@code payroll_record}), solo lectura — datos de planilla, la categoría más sensible de todo el portal. Se
     * ubica por {@code staff_member.person_id = actor.ownerId()}; sin fila propia (la mayoría de MEMBER no son
     * personal contratado) sale una respuesta vacía, nunca un error, igual criterio que {@code fin_donor} en GIVING.
     * Se incluyen los tres estados de {@code staff_member} (ACTIVE/SUSPENDED/TERMINATED) para que un ex-colaborador
     * conserve acceso a su propio historial — pero NUNCA {@code bank_encrypted}: aunque es la propia cuenta del
     * colaborador (no es una anotación interna como {@code case_note}), esta pantalla es una ficha de consulta, no
     * un lugar para reexponer un número de cuenta ya descifrado; el mismo criterio de "no todo lo que existe en la
     * fila hace falta en la pantalla" ya aplicado a {@code void_reason} en GIVING. Las boletas solo incluyen periodos
     * con {@code payroll_run.status in (APPROVED, PAID, CLOSED)} — un DRAFT/CALCULATED puede corregirse todavía, y
     * mostrarlo antes de tiempo confundiría al colaborador con una cifra que puede cambiar. Las líneas de detalle
     * excluyen {@code kind = 'EMPLOYER_CONTRIBUTION'} (el aporte del empleador, no del trabajador) — mismo criterio
     * que excluir {@code employerCost} del total: es el costo de la organización, no algo que el colaborador reciba
     * o del que se le descuente. */
    public record MyEmploymentRow(UUID id, String position, String contractType, LocalDate hireDate, LocalDate contractEnd, LocalDate terminationDate,
                                  BigDecimal baseSalary, String currency, String payFrequency, String branchName, String ministryName, String status) {
    }

    public record MyPayslipLineRow(String conceptName, String kind, BigDecimal amount) {
    }

    public record MyPayslipRow(UUID id, String period, BigDecimal gross, BigDecimal deductions, BigDecimal net, BigDecimal workedDays,
                               BigDecimal unpaidDays, String payslipNo, String runStatus, List<MyPayslipLineRow> lines) {
    }

    public record MyWorkResponse(List<MyEmploymentRow> employments, List<MyPayslipRow> payslips) {
    }
}
