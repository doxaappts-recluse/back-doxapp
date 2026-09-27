package pe.dcs.app.features.config.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.catalog.domain.CatalogItemRepository;
import pe.dcs.app.features.config.domain.OrgHoliday;
import pe.dcs.app.features.config.domain.OrgHolidayRepository;
import pe.dcs.app.features.config.dto.OrgProfileDtos.*;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.ZoneId;
import java.util.*;
import java.util.UUID;

/**
 * M23 · Perfil de la organización: país, zona horaria, moneda, idioma, formato de fecha, año fiscal, días laborables y
 * feriados. Único lugar de estos datos (M15, M17 y M20 los leen de aquí). [V10]
 */
@Service
@RequiredArgsConstructor
public class OrgProfileService {

    private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final Set<String> FORMATS = Set.of("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd");
    private static final int MAX_HOLIDAYS = 100;

    private final OrganizationRepository organizations;
    private final OrgHolidayRepository holidays;
    private final CatalogItemRepository catalog;
    private final MovementsPort movements;
    private final OrgGuard guard;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Response get(AuthenticatedActor actor) {
        Organization o = load(actor.organizationId());
        return toResponse(o, actor.actsAsOrgAdmin(), false);
    }

    @Transactional
    public Response update(AuthenticatedActor actor, Request req) {
        guard.requireOrgAdmin(actor);
        Organization o = guard.assertOpen(actor.organizationId());
        if (req.version() != null && !req.version().equals(o.getVersion())) {
            throw new Exceptions("error.common.concurrentUpdate", HttpStatus.CONFLICT);
        }
        Map<String, Object> before = snapshot(o, holidays.findByOrganizationIdOrderByDateAsc(o.getId()));

        String tz = req.timezone() == null ? "" : req.timezone().trim();
        if (!ZoneId.getAvailableZoneIds().contains(tz)) {                                            // [V10]
            throw new Exceptions("error.orgProfile.timezoneInvalid", HttpStatus.BAD_REQUEST);
        }
        String cur = req.currency() == null ? "" : req.currency().trim().toUpperCase();
        try {
            if (!cur.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException();
            }
            Currency.getInstance(cur);
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.orgProfile.currencyInvalid", HttpStatus.BAD_REQUEST);
        }
        String country = req.country() == null ? "" : req.country().trim().toUpperCase();
        boolean countryOk = catalog.findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc("COUNTRY").stream()
                .anyMatch(c -> c.isActive() && c.getCode().equals(country));
        if (!countryOk) {
            throw new Exceptions("error.org.countryInvalid", HttpStatus.BAD_REQUEST);
        }
        String lang = req.defaultLanguage() == null ? "" : req.defaultLanguage().trim().toLowerCase();
        if (!lang.equals("es") && !lang.equals("en")) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "idioma");
        }
        String fmt = req.dateFormat() == null ? "" : req.dateFormat().trim();
        if (!FORMATS.contains(fmt)) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "formato de fecha");
        }
        Integer fm = req.fiscalYearStartMonth();
        if (fm == null || fm < 1 || fm > 12) {
            throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "mes de inicio del año fiscal");
        }
        List<String> days = workingDays(req.workingDays());
        List<Holiday> hol = holidayList(req.holidays());

        boolean tzChanged = !tz.equals(o.getTimezone());
        boolean curChanged = !cur.equals(o.getCurrency());
        if ((tzChanged || curChanged) && movements.hasMovements(o.getId()) && !Boolean.TRUE.equals(req.confirmChange())) {
            throw new Exceptions("error.orgProfile.confirmRequired", HttpStatus.CONFLICT);            // solo afecta lo futuro
        }

        o.setCountry(country);
        o.setTimezone(tz);
        o.setCurrency(cur);
        o.setDefaultLanguage(lang);
        o.setDateFormat(fmt);
        o.setFiscalYearStartMonth(fm.shortValue());
        o.setWorkingDays(String.join(",", days));
        organizations.saveAndFlush(o);

        holidays.deleteAllOf(o.getId());
        holidays.flush();
        for (Holiday h : hol) {
            OrgHoliday e = new OrgHoliday();
            e.setOrganizationId(o.getId());
            e.setDate(h.date());
            e.setName(h.name().trim());
            holidays.save(e);
        }
        holidays.flush();

        Map<String, Object> after = snapshot(o, holidays.findByOrganizationIdOrderByDateAsc(o.getId()));
        Map<String, Object> diff = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("from", before.get(k));
                ch.put("to", v);
                diff.put(k, ch);
            }
        });
        if (!diff.isEmpty()) {
            audit.record(new AuditService.Command("ORG_SETTINGS", "UPDATE", "OrgProfile", o.getId(), o.getId(), null, diff));
        }
        return toResponse(load(o.getId()), true, curChanged);
    }

    // ---------------------------------------------------------------- validaciones

    private static List<String> workingDays(List<String> in) {
        Set<String> set = new HashSet<>();
        if (in != null) {
            for (String d : in) {
                String u = d == null ? "" : d.trim().toUpperCase();
                if (!DAYS.contains(u)) {
                    throw new Exceptions("error.common.invalid", HttpStatus.BAD_REQUEST, "días laborables");
                }
                set.add(u);
            }
        }
        if (set.isEmpty()) {
            throw new Exceptions("error.orgProfile.workingDaysRequired", HttpStatus.BAD_REQUEST);
        }
        return DAYS.stream().filter(set::contains).toList();
    }

    private static List<Holiday> holidayList(List<Holiday> in) {
        if (in == null) {
            return List.of();
        }
        if (in.size() > MAX_HOLIDAYS) {
            throw new Exceptions("error.orgProfile.holidayTooMany", HttpStatus.BAD_REQUEST, MAX_HOLIDAYS);
        }
        Set<java.time.LocalDate> seen = new HashSet<>();
        List<Holiday> out = new ArrayList<>();
        int row = 0;
        for (Holiday h : in) {
            row++;
            if (h == null || h.date() == null || h.name() == null || h.name().isBlank() || h.name().trim().length() > 100) {
                throw new Exceptions("error.orgProfile.holidayInvalid", HttpStatus.BAD_REQUEST, row);
            }
            if (!seen.add(h.date())) {
                throw new Exceptions("error.orgProfile.holidayDuplicate", HttpStatus.BAD_REQUEST, h.date().toString());
            }
            out.add(h);
        }
        out.sort(Comparator.comparing(Holiday::date));
        return out;
    }

    // ---------------------------------------------------------------- mapeo

    private Organization load(UUID id) {
        return organizations.findById(id).orElseThrow(() -> new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND));
    }

    private Response toResponse(Organization o, boolean canEdit, boolean currencyChanged) {
        List<Holiday> hs = holidays.findByOrganizationIdOrderByDateAsc(o.getId()).stream().map(h -> new Holiday(h.getDate(), h.getName())).toList();
        return new Response(o.getCountry(), o.getTimezone(), o.getCurrency(), o.getDefaultLanguage(), o.getDateFormat(),
                (int) o.getFiscalYearStartMonth(), Arrays.asList(o.getWorkingDays().split(",")), hs, canEdit, currencyChanged, o.getVersion());
    }

    private static Map<String, Object> snapshot(Organization o, List<OrgHoliday> hs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("country", o.getCountry());
        m.put("timezone", o.getTimezone());
        m.put("currency", o.getCurrency());
        m.put("defaultLanguage", o.getDefaultLanguage());
        m.put("dateFormat", o.getDateFormat());
        m.put("fiscalYearStartMonth", (int) o.getFiscalYearStartMonth());
        m.put("workingDays", o.getWorkingDays());
        m.put("holidays", hs.stream().map(h -> h.getDate() + " " + h.getName()).toList());
        return m;
    }
}
