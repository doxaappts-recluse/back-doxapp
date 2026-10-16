package pe.dcs.app.security;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * [#4, auditoría 2026-10-09] Envuelve el bean {@code "dataSource"} (el pool de Hikari que autoconfigura Spring
 * Boot a partir de {@code spring.datasource.*}) en {@link RlsAwareDataSource}, sin tener que redeclarar ninguna
 * propiedad de Hikari a mano. Solo actúa sobre ESE nombre exacto de bean — nunca sobre otros DataSource que
 * pudiera haber (p. ej. de Testcontainers, aunque este proyecto no usa pruebas automatizadas) — para no envolver
 * nada por accidente.
 */
@Component
public class RlsDataSourceBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("dataSource".equals(beanName) && bean instanceof DataSource ds && !(bean instanceof RlsAwareDataSource)) {
            return new RlsAwareDataSource(ds);
        }
        return bean;
    }
}
