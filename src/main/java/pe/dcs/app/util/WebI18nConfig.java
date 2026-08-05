package pe.dcs.app.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Resuelve el idioma de cada request a partir del header HTTP
 * "Accept-Language" que manda el frontend (ver LanguageInterceptor en
 * front-doxapp), sin depender de sesión (la API es stateless/JWT).
 *
 * Spring MVC setea LocaleContextHolder automáticamente en cada
 * request según este bean, así que Exceptions.resolve() (vía
 * LocaleContextHolder.getLocale()) ya recibe el idioma correcto sin
 * pasos adicionales.
 */
@Configuration
public class WebI18nConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(new Locale("es"));
        resolver.setSupportedLocales(List.of(new Locale("es"), new Locale("en")));
        return resolver;
    }
}
