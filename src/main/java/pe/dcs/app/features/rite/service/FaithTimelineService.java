package pe.dcs.app.features.rite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.rite.dto.RiteDtos;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.security.authz.AuthorizationService;

import java.util.List;
import java.util.UUID;

/** M08 · Línea de vida eclesial de una persona: membresías, bautizo, matrimonios y presentaciones (propia y como tutora). Cada bloque respeta el permiso V de su módulo y la visibilidad entre sedes. */
@Service
@RequiredArgsConstructor
public class FaithTimelineService {

    private final MembershipService memberships;
    private final RiteService rites;
    private final RiteSupport support;
    private final AuthorizationService authz;

    @Transactional(readOnly = true)
    public RiteDtos.Timeline forPerson(AuthenticatedActor actor, AccessScope scope, UUID personId) {
        RiteSupport.PersonInfo p = support.person(scope.organizationId(), personId);
        boolean m = can(actor, "MEMBERSHIP");
        boolean b = can(actor, "BAPTISM");
        boolean w = can(actor, "MARRIAGE");
        boolean d = can(actor, "CHILD_DEDICATION");
        return new RiteDtos.Timeline(p.id(), p.name(), m ? memberships.ofPerson(actor, scope, personId) : List.of(), b ? rites.ofPerson(scope, "BAPTISM", personId) : List.of(),
                w ? rites.ofPerson(scope, "MARRIAGE", personId) : List.of(), d ? rites.ofPerson(scope, "DEDICATION", personId) : List.of(),
                d ? rites.guardianOf(scope, personId) : List.of());
    }

    private boolean can(AuthenticatedActor actor, String module) {
        return authz.effectiveActions(actor, module).contains("V");
    }
}
