package pe.dcs.app.features.report.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.report.spi.PlatformReportProvider;
import pe.dcs.app.features.report.spi.ReportProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * M20 · Adopción de módulos entre organizaciones con contrato ACTIVE: cuántas lo tienen contratado. [V1] solo
 * agrega metadatos de contratos — nunca toca filas de personas/finanzas de ninguna organización.
 */
@Component
@RequiredArgsConstructor
public class PlatformAdoptionReportProvider implements PlatformReportProvider {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public String code() {
        return "PLATFORM_ADOPTION";
    }

    @Override
    public String nameKey() {
        return "report.platformAdoption";
    }

    @Override
    public String chartType() {
        return "BAR";
    }

    @Override
    public List<String> columns() {
        return List.of("moduleCode", "moduleName", "orgsContracted");
    }

    @Override
    public ReportProvider.ReportResult run(ReportProvider.Filters filters) {
        List<Object[]> rows = jdbc.query("select m.code, m.name_es, count(distinct c.organization_id) from module m"
                        + " join contract_module cm on cm.module_code = m.code join contract c on c.id = cm.contract_id and c.status = 'ACTIVE'"
                        + " where m.kind = 'CONTRACTABLE' group by m.code, m.name_es order by 3 desc, 1", new MapSqlParameterSource(),
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getLong(3)});
        List<List<Object>> data = new ArrayList<>();
        for (Object[] r : rows) {
            data.add(List.of(r[0], r[1], r[2]));
        }
        Long totalOrgs = jdbc.queryForObject("select count(distinct id) from contract where status = 'ACTIVE'", new MapSqlParameterSource(), Long.class);
        return new ReportProvider.ReportResult(columns(), data, Map.of("orgsWithActiveContract", totalOrgs == null ? 0 : totalOrgs));
    }
}
