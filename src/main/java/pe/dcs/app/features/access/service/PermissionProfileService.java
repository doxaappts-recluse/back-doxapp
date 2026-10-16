package pe.dcs.app.features.access.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.access.domain.PermissionProfile;
import pe.dcs.app.features.access.dto.PermissionItemDto;
import pe.dcs.app.features.access.dto.ProfileRequest;
import pe.dcs.app.features.access.dto.ProfileResponse;
import pe.dcs.app.features.access.dto.ProfileSearchRequest;
import pe.dcs.app.features.access.repo.PermissionProfileRepository;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScope;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.shared.domain.StatusRules;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M05 · Perfiles de permisos de la organización (N2). Reglas: nombre único por organización, al menos un módulo,
 * [V12]/[V13] los mismos que la delegación. Un perfil inactivo no se ofrece al delegar; los accesos que lo usaron
 * conservan lo otorgado (se copia).
 */
@Service
@RequiredArgsConstructor
public class PermissionProfileService {

    static final String MODULE = "PERMISSION_PROFILES";
    static final String ENTITY = "PermissionProfile";

    private final PermissionProfileRepository profiles;
    private final DelegationRules rules;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> search(ProfileSearchRequest req, AccessScope scope) {
        ProfileSearchRequest.Filters f = req == null || req.filters() == null ? new ProfileSearchRequest.Filters(null, null) : req.filters();
        Specification<PermissionProfile> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("organizationId"), scope.organizationId()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)));
            }
            if (f.status() != null && !f.status().isBlank()) {
                ps.add(cb.equal(root.get("status"), parseStatus(f.status())));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<SortRequest> sorts = req == null ? null : req.sorts();
        if (sorts == null || sorts.isEmpty()) {
            SortRequest byName = new SortRequest();
            byName.setKey("name");
            byName.setDirection("ASC");
            sorts = List.of(byName);
        }
        Pageable pageable = PageableUtil.buildPageable(req == null ? null : req.pagination(), sorts,
                field -> switch (field) { case "name", "status", "createdAt" -> field; default -> "name"; });
        Page<PermissionProfile> page = profiles.findAll(spec, pageable);
        return new PageResponse<>(page.getContent().stream().map(this::toResponse).toList(), new PaginationResponse(
                (int) page.getTotalElements(), page.getTotalPages(), page.getSize(), page.getNumber()));
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID id, AccessScope scope) {
        return toResponse(find(id, scope));
    }

    @Transactional
    public ProfileResponse create(ProfileRequest req, AuthenticatedActor actor, AccessScope scope) {
        String name = req.name().trim();
        if (profiles.nameTaken(scope.organizationId(), name, null)) {
            throw new Exceptions("error.profile.nameTaken", HttpStatus.CONFLICT);
        }
        Map<String, List<String>> items = normalized(actor, req.items());
        PermissionProfile p = new PermissionProfile();
        p.setOrganizationId(scope.organizationId());
        p.setName(name);
        p.setDescription(blankToNull(req.description()));
        for (Map.Entry<String, List<String>> e : items.entrySet()) {
            p.getItems().add(new PermissionProfile.Item(e.getKey(), e.getValue()));
        }
        p = profiles.saveAndFlush(p);
        audit.record(AuditService.Command.of(MODULE, "CREATE", ENTITY, p.getId(), Map.of("name", name, "modules", String.join(",", items.keySet()))));
        return toResponse(p);
    }

    @Transactional
    public ProfileResponse update(UUID id, ProfileRequest req, AuthenticatedActor actor, AccessScope scope) {
        PermissionProfile p = find(id, scope);
        if (req.version() != null && !req.version().equals(p.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        String name = req.name().trim();
        if (profiles.nameTaken(scope.organizationId(), name, p.getId())) {
            throw new Exceptions("error.profile.nameTaken", HttpStatus.CONFLICT);
        }
        Map<String, List<String>> items = normalized(actor, req.items());
        Map<String, Object> diff = new LinkedHashMap<>();
        if (!p.getName().equals(name)) {
            diff.put("name", Map.of("from", p.getName(), "to", name));
        }
        String before = summary(p.getItems());
        p.setName(name);
        p.setDescription(blankToNull(req.description()));
        p.getItems().clear();
        for (Map.Entry<String, List<String>> e : items.entrySet()) {
            p.getItems().add(new PermissionProfile.Item(e.getKey(), e.getValue()));
        }
        p = profiles.saveAndFlush(p);
        String after = summary(p.getItems());
        if (!before.equals(after)) {
            diff.put("items", Map.of("from", before, "to", after));
        }
        audit.record(AuditService.Command.of(MODULE, "UPDATE", ENTITY, p.getId(), diff));
        return toResponse(p);
    }

    @Transactional
    public ProfileResponse changeStatus(UUID id, StatusType to, AccessScope scope) {
        PermissionProfile p = find(id, scope);
        StatusType from = p.getStatus();
        StatusRules.assertTransition(from, to, "n/a");
        p.setStatus(to);
        p = profiles.saveAndFlush(p);
        audit.record(AuditService.Command.of(MODULE, "STATUS_CHANGE", ENTITY, p.getId(), Map.of("from", from.name(), "to", to.name())));
        return toResponse(p);
    }

    /** Perfil ACTIVO de la organización para copiarlo a un acceso. */
    PermissionProfile activeProfile(UUID id, UUID orgId) {
        PermissionProfile p = profiles.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (p.getStatus() != StatusType.ACTIVE) {
            throw new Exceptions("error.profile.inactive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return p;
    }

    private Map<String, List<String>> normalized(AuthenticatedActor actor, List<PermissionItemDto> items) {
        Map<String, List<String>> n = rules.normalize(actor, items);
        if (n.isEmpty()) {
            throw new Exceptions("error.profile.itemsRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return n;
    }

    private PermissionProfile find(UUID id, AccessScope scope) {
        return profiles.findByIdAndOrganizationId(id, scope.organizationId())
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private ProfileResponse toResponse(PermissionProfile p) {
        List<PermissionItemDto> items = p.getItems().stream().map(i -> new PermissionItemDto(i.getModuleCode(), i.actionList())).toList();
        return new ProfileResponse(p.getId(), p.getName(), p.getDescription(), p.getStatus(), items, p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }

    private static String summary(List<PermissionProfile.Item> items) {
        return items.stream().sorted(java.util.Comparator.comparing(PermissionProfile.Item::getModuleCode))
                .map(i -> i.getModuleCode() + ":" + String.join("", i.actionList())).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static StatusType parseStatus(String s) {
        try {
            return StatusType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, s);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
