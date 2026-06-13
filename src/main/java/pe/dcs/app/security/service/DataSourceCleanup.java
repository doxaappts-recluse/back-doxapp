package pe.dcs.app.security.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DataSourceCleanup {

    private final DataSource dataSource;

    public DataSourceCleanup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PreDestroy
    public void cleanup() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
            hikari.close();
        }
    }
}