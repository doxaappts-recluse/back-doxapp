package pe.dcs.app.security;

import jakarta.annotation.PreDestroy;
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
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
            ((com.zaxxer.hikari.HikariDataSource) dataSource).close();
        }
    }
}
