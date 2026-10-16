package pe.dcs.app.features.portal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.access.service.AccessProvisioner;
import pe.dcs.app.features.approval.service.ApprovalHandler;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.features.person.service.ConsentService;
import pe.dcs.app.util.Exceptions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M24 · lado "decisión" del autorregistro (PORTAL_SIGNUP): aprobar da de alta el acceso MEMBER sobre la persona ya
 * creada/enlazada en el alta pública (nunca duplica — {@code personId} viene fijo en {@code subjectId}). El
 * {@link Handler} solo delega aquí, igual que {@code GroupParticipants.JoinHandler} delega en {@code GroupJoinService}.
 */
@Service
@RequiredArgsConstructor
public class PortalSignupService {

    public static final String TYPE = "PORTAL_SIGNUP";
    public static final String MODULE = "PORTAL_SIGNUP";

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessProvisioner provisioner;
    private final OrganizationRepository organizations;
    private final ConsentService consents;

    Map<String, String> notifyParams(ApprovalHandler.ApprovalRow row) {
        String name = jdbc.queryForObject("select first_name || ' ' || last_name from person where id = :p",
                new MapSqlParameterSource("p", row.subjectId()), String.class);
        List<String> branch = jdbc.queryForList("select name from branch where id = :b", new MapSqlParameterSource("b", row.branchId()), String.class);
        return Map.of("name", name == null ? "" : name, "branch", branch.isEmpty() ? "" : branch.get(0));
    }

    void onApproved(ApprovalHandler.ApprovalRow row) {
        Organization org = organizations.findById(row.organizationId()).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        UUID branchId = UUID.fromString(String.valueOf(row.payload().get("branchId")));
        AccessProvisioner.PersonInput input = new AccessProvisioner.PersonInput(row.subjectId(), null, null, null, null, null, null);
        provisioner.provisionMember(org, input, branchId);
        consents.grantDefaults(row.organizationId(), row.subjectId(), "PORTAL", null);
    }

    /** Registrado automáticamente en {@code ApprovalEngine} (ObjectProvider&lt;ApprovalHandler&gt;). */
    @Component
    @RequiredArgsConstructor
    public static class Handler implements ApprovalHandler {

        private final PortalSignupService service;

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public String moduleCode() {
            return MODULE;
        }

        @Override
        public Integer expiryDays() {
            return 30;
        }

        @Override
        public Map<String, String> notifyParams(ApprovalRow request) {
            return service.notifyParams(request);
        }

        @Override
        public void onApprove(Decision d) {
            service.onApproved(d.request());
        }
    }
}
