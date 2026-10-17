package pe.dcs.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;

/*
 * El refactor de 2026-10 movio "features.*" fuera de "pe.dcs.app" (ver
 * doxapp-spec/00_PROMPT_MAESTRO.md §10). @SpringBootApplication por
 * defecto solo escanea el paquete de esta clase ("pe.dcs.app") y sus
 * hijos, asi que sin esto Spring ya no encuentra ningun @Component,
 * @Service, @Entity ni @Repository bajo "features" (p.ej.
 * "features.module.domain.AppModuleRepository").
 */
@SpringBootApplication(scanBasePackages = {"pe.dcs.app", "features"})
@EntityScan(basePackages = {"pe.dcs.app", "features"})
@EnableJpaRepositories(basePackages = {"pe.dcs.app", "features"})
@EnableScheduling
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
        System.out.println("Zona JVM: " + ZoneId.systemDefault());

    }
}