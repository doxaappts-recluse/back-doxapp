package pe.dcs.app.features.group.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** M10 · Grupos y células: grupos, integrantes, reuniones con asistencia, salud, multiplicación y reglas. */
public final class GroupDtos {

    private GroupDtos() {
    }

    // ---------------------------------------------------------------- reglas
    public record Rules(boolean leaderRequiresMembership, boolean allowMultipleGroups, int minAdultLeadersMinors, Long version) {
    }

    public record RulesRequest(Boolean leaderRequiresMembership, Boolean allowMultipleGroups, Integer minAdultLeadersMinors, Long version) {
    }

    // ---------------------------------------------------------------- grupos
    /** leaderPersonId es opcional al crear (el grupo nace en borrador); meetingDay 1 = lunes … 7 = domingo. */
    public record GroupRequest(UUID branchId, String name, String category, String audience, String description, UUID hostPersonId, Integer meetingDay, LocalTime meetingTime,
                               String location, String zone, Integer capacity, Boolean openToJoin, LocalDate startDate, LocalDate endDate, UUID leaderPersonId) {
    }

    public record GroupUpdate(String name, String category, String audience, String description, UUID hostPersonId, Integer meetingDay, LocalTime meetingTime,
                              String location, String zone, Integer capacity, Boolean openToJoin, LocalDate startDate, LocalDate endDate, Long version) {
    }

    public record Search(Filters filters, PaginationRequest pagination) {
        public record Filters(String q, UUID branchId, String status, String category, String audience, Boolean openToJoin) {
        }
    }

    public record ReasonRequest(String reason) {
    }

    public record GroupResponse(UUID id, UUID branchId, String branchName, String name, String category, String audience, String description, UUID hostPersonId, String hostName,
                                Integer meetingDay, LocalTime meetingTime, String location, String zone, Integer capacity, boolean openToJoin, LocalDate startDate, LocalDate endDate,
                                UUID parentGroupId, String parentName, String status, String statusReason, int members, UUID leaderId, String leaderName, List<String> coLeaders,
                                LocalDate nextMeeting, int pendingRequests, boolean hasMinors, Instant createdAt, long version) {
    }

    // ---------------------------------------------------------------- integrantes
    public record MemberAdd(UUID personId, String role) {
    }

    public record MemberRole(String role) {
    }

    public record MemberResponse(UUID id, UUID personId, String personName, String role, String status, LocalDate joinedAt, LocalDate leftAt, String leftReason, boolean minor,
                                 Integer age, boolean leaderMember) {
    }

    // ---------------------------------------------------------------- reuniones
    /**
     * repeatWeeks = cuántas semanas más se repite (0–26); la fecha y hora por omisión salen del grupo.
     * [M10→M16] endTime y spaceId son opcionales: si ambos vienen (y SPACES está contratado), cada ocurrencia creada intenta
     * reservar ese espacio; si el espacio ya está ocupado en esa franja, esa ocurrencia puntual queda sin reserva (no bloquea
     * la creación de la reunión ni del resto de la serie), mismo criterio que M13→M16.
     */
    public record MeetingRequest(LocalDate date, LocalTime time, String topic, String location, Integer repeatWeeks, LocalTime endTime, UUID spaceId) {
    }

    public record MeetingUpdate(LocalDate date, LocalTime time, String topic, String location, Long version, LocalTime endTime, UUID spaceId) {
    }

    public record HoldRequest(String topic, String notes, String noAttendanceReason) {
    }

    public record MeetingResponse(UUID id, UUID groupId, String groupName, UUID branchId, LocalDate date, LocalTime time, String topic, String location, String status,
                                  String notes, String noAttendanceReason, String cancelReason, UUID attendanceSessionId, int attendees, int members, long version,
                                  LocalTime endTime, UUID spaceId, String spaceName) {
    }

    public record MeetingSearch(UUID groupId, String status, LocalDate from, LocalDate to, PaginationRequest pagination) {
    }

    public record MeetingCreated(int created, int skipped, List<MeetingResponse> meetings) {
    }

    public record AttendanceMark(UUID personId, String status) {
    }

    public record AttendanceRow(UUID personId, String personName, String role, String status, boolean guest, boolean minor) {
    }

    public record AttendanceView(MeetingResponse meeting, List<AttendanceRow> rows, int attendees, boolean canMark) {
    }

    // ---------------------------------------------------------------- multiplicación
    public record MultiplyRequest(String name, List<UUID> memberIds, UUID leaderPersonId, UUID coLeaderPersonId, Integer meetingDay, LocalTime meetingTime, String location,
                                  String zone) {
    }

    public record MultiplyResult(GroupResponse child, GroupResponse parent, int moved) {
    }

    // ---------------------------------------------------------------- salud
    public record InactiveMember(UUID personId, String personName, LocalDate lastAttended) {
    }

    /** level: OK | ATTENTION | STALLED | NEW (todavía sin reuniones planificadas en el periodo). */
    public record Health(UUID groupId, String groupName, int periodWeeks, LocalDate from, LocalDate to, int members, int joined, int left, int netGrowth, int held, int planned,
                         Integer compliancePct, Double averageAttendance, int inactiveWeeks, List<InactiveMember> inactive, String level) {
    }

    public record BranchHealth(UUID branchId, String branchName, int activeGroups, int members, int held, int planned, Double averageAttendance, int netGrowth) {
    }

    public record HealthOverview(int periodWeeks, LocalDate from, LocalDate to, List<BranchHealth> branches) {
    }

    // ---------------------------------------------------------------- solicitudes de ingreso
    public record JoinRequest(UUID personId, String message) {
    }

    public record JoinRequestRow(UUID id, UUID personId, String personName, String requestedByName, String message, Instant createdAt) {
    }

    public record PersonGroup(UUID groupId, String groupName, String branchName, String role, String status, LocalDate joinedAt, LocalDate leftAt) {
    }
}
