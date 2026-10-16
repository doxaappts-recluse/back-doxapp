package pe.dcs.app.security;

import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DataSourceCloser {

    private final DataSource dataSource;

    public DataSourceCloser(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PreDestroy
    public void close() throws Exception {
        // [#4, auditoría 2026-10-09] Desde RlsAwareDataSource (RLS acotado), el bean "dataSource" ya no es
        // directamente el HikariDataSource sino un DelegatingDataSource que lo envuelve — hay que desenvolverlo
        // antes de revisar el tipo, o este cierre dejaría de pasar nunca y el pool quedaría abierto al apagar.
        DataSource target = dataSource;
        while (target instanceof DelegatingDataSource delegating) {
            target = delegating.getTargetDataSource();
        }
        if (target instanceof com.zaxxer.hikari.HikariDataSource hikari) {
            hikari.close();
        }
    }
}
