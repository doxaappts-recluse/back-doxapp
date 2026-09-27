package pe.dcs.app.features.catalog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.catalog.domain.CatalogItem;
import pe.dcs.app.features.catalog.domain.CatalogItemRepository;
import pe.dcs.app.features.catalog.dto.*;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * M23 · Catálogos. BASE: los publica y mantiene la plataforma (es+en obligatorios, código inmutable, nunca se borran,
 * solo se desactivan). ORG: ítems propios de una organización; nunca alteran a los BASE, que puede ocultar.
 * [V1] [V2] [V5] [V6] [V12].
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final String MODULE = "CATALOGS";
    private static final String ENTITY = "CatalogItem";
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{2,60}$");

    private final CatalogItemRepository items;
    private final CatalogTypeRegistry registry;
    private final OrgGuard guard;
    private final AuditService audit;
    private final ObjectProvider<CatalogUsageChecker> usage;

    // ---------------------------------------------------------------- tipos

    @Transactional(readOnly = true)
    public List<CatalogTypeResponse> types() {
        Map<String, long[]> counts = new HashMap<>();
        for (Object[] r : items.baseCounts()) {
            counts.put((String) r[0], new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        }
        return registry.all().stream().map(t -> {
            long[] c = counts.getOrDefault(t.code(), new long[]{0, 0});
            return new CatalogTypeResponse(t.code(), t.ownerModule(), t.editableByOrg(), t.extensible(), t.parentType(),
                    t.publicRead(), t.mandatory(), c[0], c[1]);
        }).toList();
    }

    public CatalogTypeDef type(String code) {
        return registry.find(code).orElseThrow(() -> new Exceptions("error.catalog.typeNotFound", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- N1 · ítems BASE

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listBase(String typeCode, UUID parentId) {
        CatalogTypeDef def = type(typeCode);
        List<CatalogItem> list = items.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(def.code());
        if (parentId != null) {
            list = list.stream().filter(i -> parentId.equals(i.getParentId())).toList();
        }
        return toResponses(list, Set.of(), true);
    }

    @Transactional
    public CatalogItemResponse createBase(String typeCode, CatalogItemRequest req) {
        CatalogTypeDef def = type(typeCode);
        String code = normalizeCode(req.code());
        names(req);
        if (items.existsByTypeAndCode(def.code(), code)) {                                   // [V5]
            throw new Exceptions("error.catalog.codeTaken", HttpStatus.CONFLICT);
        }
        CatalogItem i = new CatalogItem();
        i.setType(def.code());
        i.setCode(code);
        i.setSource(CatalogItem.BASE);
        i.setParentId(parent(def, req.parentId(), null));
        fill(i, req);
        if (req.sortOrder() == null) {
            i.setSortOrder(nextOrder(items.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(def.code())));
        }
        items.saveAndFlush(i);
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, i.getId(), null, null, snapshot(i)));
        return toResponses(List.of(i), Set.of(), true).get(0);
    }

    @Transactional
    public CatalogItemResponse updateBase(String typeCode, UUID id, CatalogItemRequest req) {
        CatalogTypeDef def = type(typeCode);
        CatalogItem i = items.findByIdAndOrganizationIdIsNull(id)
                .filter(x -> x.getType().equals(def.code()))
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        checkVersion(i, req.version());
        if (req.code() != null && !req.code().isBlank() && !normalizeCode(req.code()).equals(i.getCode())) {
            throw new Exceptions("error.catalog.codeImmutable", HttpStatus.UNPROCESSABLE_ENTITY);   // [V1]
        }
        names(req);
        Map<String, Object> before = snapshot(i);
        i.setParentId(parent(def, req.parentId(), null));
        fill(i, req);
        items.saveAndFlush(i);
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, i.getId(), null, null, diff(before, snapshot(i))));
        return toResponses(List.of(i), Set.of(), true).get(0);
    }

    @Transactional
    public CatalogItemResponse setBaseActive(String typeCode, UUID id, Boolean active) {
        CatalogTypeDef def = type(typeCode);
        if (active == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "active");
        }
        CatalogItem i = items.findByIdAndOrganizationIdIsNull(id)
                .filter(x -> x.getType().equals(def.code()))
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (i.isActive() == active) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, i.isActive() ? "ACTIVE" : "INACTIVE");
        }
        if (!active && def.mandatory() && items.countActiveBase(def.code()) <= 1) {              // [V12]
            throw new Exceptions("error.catalog.lastActive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        i.setActive(active);
        items.saveAndFlush(i);
        audit.record(new AuditService.Command(MODULE, "STATUS_CHANGE", ENTITY, i.getId(), null, null,
                Map.of("active", Map.of("from", !active, "to", active))));
        return toResponses(List.of(i), Set.of(), true).get(0);
    }

    // ---------------------------------------------------------------- N2 · ítems de la organización

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listOrg(AuthenticatedActor actor, String typeCode, UUID parentId) {
        CatalogTypeDef def = type(typeCode);
        UUID org = actor.organizationId();
        Set<UUID> hidden = items.hiddenIds(org);
        List<CatalogItem> all = new ArrayList<>(items.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(def.code()));
        all.addAll(items.findByTypeAndOrganizationIdOrderBySortOrderAscNameEsAsc(def.code(), org));
        if (parentId != null) {
            all = all.stream().filter(i -> parentId.equals(i.getParentId())).collect(Collectors.toCollection(ArrayList::new));
        }
        return toResponses(all, hidden, false);
    }

    @Transactional
    public CatalogItemResponse createOrg(AuthenticatedActor actor, String typeCode, CatalogItemRequest req) {
        guard.requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        guard.assertOpen(org);
        CatalogTypeDef def = type(typeCode);
        if (!def.extensible()) {
            throw new Exceptions("error.catalog.notExtensible", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String code = normalizeCode(req.code());
        names(req);
        if (items.existsByTypeAndCodeAndOrganizationIdIsNull(def.code(), code)) {                // [V5] no colisiona con BASE
            throw new Exceptions("error.catalog.codeTaken", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (items.existsByTypeAndCodeAndOrganizationId(def.code(), code, org)) {
            throw new Exceptions("error.catalog.codeTaken", HttpStatus.CONFLICT);
        }
        CatalogItem i = new CatalogItem();
        i.setOrganizationId(org);
        i.setType(def.code());
        i.setCode(code);
        i.setSource(CatalogItem.ORG);
        i.setParentId(parent(def, req.parentId(), org));
        fill(i, req);
        if (req.sortOrder() == null) {
            i.setSortOrder(nextOrder(items.findByTypeAndOrganizationIdOrderBySortOrderAscNameEsAsc(def.code(), org)));
        }
        items.saveAndFlush(i);
        audit.record(new AuditService.Command(MODULE, "CREATE", ENTITY, i.getId(), org, null, snapshot(i)));
        return toResponses(List.of(i), Set.of(), false).get(0);
    }

    @Transactional
    public CatalogItemResponse updateOrg(AuthenticatedActor actor, String typeCode, UUID id, CatalogItemRequest req) {
        guard.requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        guard.assertOpen(org);
        CatalogTypeDef def = type(typeCode);
        CatalogItem i = ownItem(org, def, id);
        checkVersion(i, req.version());
        if (req.code() != null && !req.code().isBlank() && !normalizeCode(req.code()).equals(i.getCode())) {
            throw new Exceptions("error.catalog.codeImmutable", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        names(req);
        Map<String, Object> before = snapshot(i);
        i.setParentId(parent(def, req.parentId(), org));
        fill(i, req);
        items.saveAndFlush(i);
        audit.record(new AuditService.Command(MODULE, "UPDATE", ENTITY, i.getId(), org, null, diff(before, snapshot(i))));
        return toResponses(List.of(i), Set.of(), false).get(0);
    }

    @Transactional
    public CatalogItemResponse setOrgActive(AuthenticatedActor actor, String typeCode, UUID id, Boolean active) {
        guard.requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        guard.assertOpen(org);
        CatalogTypeDef def = type(typeCode);
        if (active == null) {
            throw new Exceptions("error.common.required", HttpStatus.BAD_REQUEST, "active");
        }
        CatalogItem i = ownItem(org, def, id);
        if (i.isActive() == active) {
            throw new Exceptions("error.common.invalidState", HttpStatus.CONFLICT, i.isActive() ? "ACTIVE" : "INACTIVE");
        }
        if (!active) {
            assertKeepsOne(def, org, i.getId());
        }
        i.setActive(active);
        items.saveAndFlush(i);
        audit.record(new AuditService.Command(MODULE, "STATUS_CHANGE", ENTITY, i.getId(), org, null,
                Map.of("active", Map.of("from", !active, "to", active))));
        return toResponses(List.of(i), Set.of(), false).get(0);
    }

    /** Oculta (o vuelve a mostrar) un ítem BASE para esta organización. */
    @Transactional
    public CatalogItemResponse setHidden(AuthenticatedActor actor, String typeCode, UUID id, boolean hide) {
        guard.requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        guard.assertOpen(org);
        CatalogTypeDef def = type(typeCode);
        if (!def.editableByOrg()) {
            throw new Exceptions("error.catalog.baseReadOnly", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        CatalogItem i = items.findByIdAndOrganizationIdIsNull(id)
                .filter(x -> x.getType().equals(def.code()))
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (hide) {
            assertKeepsOne(def, org, i.getId());
            items.hide(org, i.getId());
        } else {
            items.show(org, i.getId());
        }
        audit.record(new AuditService.Command(MODULE, hide ? "HIDE" : "SHOW", ENTITY, i.getId(), org, null, Map.of("code", i.getCode())));
        return toResponses(List.of(i), items.hiddenIds(org), false).get(0);
    }

    @Transactional
    public void deleteOrg(AuthenticatedActor actor, String typeCode, UUID id) {
        guard.requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        guard.assertOpen(org);
        CatalogTypeDef def = type(typeCode);
        CatalogItem i = items.findByIdAndOrganizationId(id, org)
                .filter(x -> x.getType().equals(def.code()))
                .or(() -> items.findByIdAndOrganizationIdIsNull(id).filter(x -> x.getType().equals(def.code())))
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        if (i.isBase()) {                                                                          // [V2]
            throw new Exceptions("error.catalog.baseReadOnly", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (inUse(org, i)) {                                                                       // [V6]
            throw new Exceptions("error.catalog.inUse", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (i.isActive()) {
            assertKeepsOne(def, org, i.getId());
        }
        items.delete(i);
        items.flush();
        audit.record(new AuditService.Command(MODULE, "DELETE", ENTITY, i.getId(), org, null, Map.of("code", i.getCode(), "type", i.getType())));
    }

    @Transactional
    public List<CatalogItemResponse> reorder(AuthenticatedActor actor, String typeCode, List<UUID> ids) {
        guard.requireOrgAdmin(actor);
        UUID org = actor.organizationId();
        guard.assertOpen(org);
        CatalogTypeDef def = type(typeCode);
        List<CatalogItem> own = items.findByTypeAndOrganizationIdOrderBySortOrderAscNameEsAsc(def.code(), org);
        if (ids == null || ids.size() != own.size() || !new HashSet<>(ids).equals(own.stream().map(CatalogItem::getId).collect(Collectors.toSet()))) {
            throw new Exceptions("error.catalog.reorderInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<UUID, CatalogItem> byId = own.stream().collect(Collectors.toMap(CatalogItem::getId, x -> x));
        int n = 10;
        for (UUID id : ids) {
            byId.get(id).setSortOrder(n);
            n += 10;
        }
        items.saveAllAndFlush(own);
        audit.record(new AuditService.Command(MODULE, "REORDER", ENTITY, def.code(), org, null, Map.of("type", def.code(), "count", ids.size())));
        return toResponses(ids.stream().map(byId::get).toList(), Set.of(), false);
    }

    // ---------------------------------------------------------------- consumo (selects)

    /** Ítems activos y visibles para llenar un select: BASE no ocultos + propios de la organización. */
    @Transactional(readOnly = true)
    public List<CatalogOption> options(AuthenticatedActor actor, String typeCode, UUID parentId) {
        CatalogTypeDef def = type(typeCode);
        UUID org = actor.isStaff() ? null : actor.organizationId();
        Set<UUID> hidden = org == null ? Set.of() : items.hiddenIds(org);
        List<CatalogItem> all = new ArrayList<>(items.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(def.code()));
        if (org != null) {
            all.addAll(items.findByTypeAndOrganizationIdOrderBySortOrderAscNameEsAsc(def.code(), org));
        }
        return all.stream()
                .filter(CatalogItem::isActive)
                .filter(i -> !hidden.contains(i.getId()))
                .filter(i -> parentId == null || parentId.equals(i.getParentId()))
                .map(i -> new CatalogOption(i.getId(), i.getCode(), i.getNameEs(), i.getNameEn(), i.getParentId()))
                .toList();
    }

    /** Solo tipos públicos y solo ítems BASE activos; otro tipo → 404. */
    @Transactional(readOnly = true)
    public List<CatalogOption> publicItems(String typeCode, String parentCode) {
        CatalogTypeDef def = registry.find(typeCode).filter(CatalogTypeDef::publicRead)
                .orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
        UUID parent = null;
        if (parentCode != null && !parentCode.isBlank() && def.hierarchical()) {
            parent = items.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(def.parentType()).stream()
                    .filter(p -> p.getCode().equalsIgnoreCase(parentCode.trim())).map(CatalogItem::getId).findFirst().orElse(null);
        }
        final UUID pf = parent;
        return items.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(def.code()).stream()
                .filter(CatalogItem::isActive)
                .filter(i -> pf == null || pf.equals(i.getParentId()))
                .map(i -> new CatalogOption(i.getId(), i.getCode(), i.getNameEs(), i.getNameEn(), i.getParentId()))
                .toList();
    }

    // ---------------------------------------------------------------- reglas

    private CatalogItem ownItem(UUID org, CatalogTypeDef def, UUID id) {
        Optional<CatalogItem> mine = items.findByIdAndOrganizationId(id, org).filter(x -> x.getType().equals(def.code()));
        if (mine.isPresent()) {
            return mine.get();
        }
        if (items.findByIdAndOrganizationIdIsNull(id).filter(x -> x.getType().equals(def.code())).isPresent()) {
            throw new Exceptions("error.catalog.baseReadOnly", HttpStatus.UNPROCESSABLE_ENTITY);   // [V2]
        }
        throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);                         // ajeno o inexistente
    }

    /** [V12] tras la operación debe quedar al menos un ítem activo y visible en los catálogos obligatorios. */
    private void assertKeepsOne(CatalogTypeDef def, UUID org, UUID excluding) {
        if (!def.mandatory()) {
            return;
        }
        Set<UUID> hidden = items.hiddenIds(org);
        long left = items.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(def.code()).stream()
                .filter(i -> i.isActive() && !hidden.contains(i.getId()) && !i.getId().equals(excluding)).count()
                + items.findByTypeAndOrganizationIdOrderBySortOrderAscNameEsAsc(def.code(), org).stream()
                .filter(i -> i.isActive() && !i.getId().equals(excluding)).count();
        if (left < 1) {
            throw new Exceptions("error.catalog.lastActive", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private boolean inUse(UUID org, CatalogItem i) {
        if (items.countByParentId(i.getId()) > 0) {
            return true;
        }
        return usage.orderedStream().anyMatch(u -> u.inUse(org, i.getType(), i.getCode()));
    }

    private UUID parent(CatalogTypeDef def, UUID parentId, UUID org) {
        if (!def.hierarchical()) {
            if (parentId != null) {
                throw new Exceptions("error.catalog.parentNotAllowed", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return null;
        }
        if (parentId == null) {
            throw new Exceptions("error.catalog.parentRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        CatalogItem p = items.findById(parentId).orElseThrow(() -> new Exceptions("error.catalog.parentInvalid", HttpStatus.UNPROCESSABLE_ENTITY));
        boolean visible = p.isBase() || (org != null && org.equals(p.getOrganizationId()));
        if (!p.getType().equals(def.parentType()) || !visible) {
            throw new Exceptions("error.catalog.parentInvalid", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return p.getId();
    }

    private static String normalizeCode(String raw) {
        String c = raw == null ? "" : raw.trim().toUpperCase().replace(' ', '_');
        if (!CODE.matcher(c).matches()) {
            throw new Exceptions("error.catalog.codeFormat", HttpStatus.BAD_REQUEST);
        }
        return c;
    }

    /** [V1] nombre en español e inglés obligatorio. */
    private static void names(CatalogItemRequest req) {
        String es = req.nameEs() == null ? "" : req.nameEs().trim();
        String en = req.nameEn() == null ? "" : req.nameEn().trim();
        if (es.isEmpty() || en.isEmpty()) {
            throw new Exceptions("error.catalog.translationRequired", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (es.length() > 120 || en.length() > 120) {
            throw new Exceptions("error.common.tooLong", HttpStatus.BAD_REQUEST, "nombre", 120);
        }
    }

    private static void fill(CatalogItem i, CatalogItemRequest req) {
        i.setNameEs(req.nameEs().trim());
        i.setNameEn(req.nameEn().trim());
        if (req.sortOrder() != null) {
            i.setSortOrder(req.sortOrder());
        }
        i.setMeta(req.meta() == null ? new HashMap<>() : new HashMap<>(req.meta()));
    }

    private static int nextOrder(List<CatalogItem> list) {
        return list.stream().mapToInt(CatalogItem::getSortOrder).max().orElse(0) + 10;
    }

    private static void checkVersion(CatalogItem i, Long version) {
        if (version != null && !version.equals(i.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
    }

    // ---------------------------------------------------------------- mapeo y auditoría

    private List<CatalogItemResponse> toResponses(List<CatalogItem> list, Set<UUID> hidden, boolean platform) {
        Set<UUID> parentIds = list.stream().map(CatalogItem::getParentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CatalogItem> parents = items.findAllById(parentIds).stream().collect(Collectors.toMap(CatalogItem::getId, p -> p));
        Set<UUID> withChildren = list.isEmpty() ? Set.of() : items.parentsWithChildren(list.stream().map(CatalogItem::getId).toList());
        return list.stream().map(i -> {
            CatalogItem p = i.getParentId() == null ? null : parents.get(i.getParentId());
            return new CatalogItemResponse(i.getId(), i.getType(), i.getCode(), i.getNameEs(), i.getNameEn(), i.getSortOrder(),
                    i.isActive(), i.getSource(), i.getParentId(), p == null ? null : p.getNameEs(), p == null ? null : p.getNameEn(),
                    hidden.contains(i.getId()), !platform && i.isBase(), withChildren.contains(i.getId()),
                    i.getMeta(), i.getVersion());
        }).toList();
    }

    private static Map<String, Object> snapshot(CatalogItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", i.getType());
        m.put("code", i.getCode());
        m.put("nameEs", i.getNameEs());
        m.put("nameEn", i.getNameEn());
        m.put("sortOrder", i.getSortOrder());
        m.put("active", i.isActive());
        m.put("parentId", i.getParentId() == null ? null : i.getParentId().toString());
        return m;
    }

    private static Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> d = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("from", before.get(k));
                ch.put("to", v);
                d.put(k, ch);
            }
        });
        return d;
    }
}
