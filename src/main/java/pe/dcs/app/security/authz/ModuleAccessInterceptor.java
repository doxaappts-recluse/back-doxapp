package pe.dcs.app.security.authz;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import pe.dcs.app.util.Exceptions;

/**
 * Aplica {@link ModuleAccess} a cada endpoint de /api/v1. Deny-by-default: un handler sin {@code @ModuleAccess}
 * ni {@code @PublicEndpoint} responde 403 y se registra como error de programación.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModuleAccessInterceptor implements HandlerInterceptor {

    private final AuthorizationService authorization;
    private final AccessScopeResolver resolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        if (AnnotatedElementUtils.hasAnnotation(hm.getMethod(), PublicEndpoint.class)
                || AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), PublicEndpoint.class)) {
            return true;
        }
        ModuleAccess access = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), ModuleAccess.class);
        if (access == null) {
            access = AnnotatedElementUtils.findMergedAnnotation(hm.getBeanType(), ModuleAccess.class);
        }
        if (access == null) {
            log.error("Endpoint sin @ModuleAccess ni @PublicEndpoint: {}#{}", hm.getBeanType().getSimpleName(), hm.getMethod().getName());
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        authorization.require(resolver.actor(), access.module(), access.action());
        return true;
    }
}
