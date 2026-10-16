package pe.dcs.app.features.rite.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.rite.service.CertificateService;
import pe.dcs.app.features.rite.service.RiteRequirementService;
import pe.dcs.app.features.rite.service.RiteRulesService;
import pe.dcs.app.features.rite.service.RiteService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;

/** M08 · Baptism (módulo contratable BAPTISM). */
@RestController
@RequestMapping("/api/v1/admin/baptisms")
@ModuleAccess(module = "BAPTISM", action = Action.V)
public class AdminBaptismController extends AbstractRiteController {

    public AdminBaptismController(RiteService service, CertificateService certificates, RiteRequirementService requirements, RiteRulesService rules, AccessScopeResolver resolver) {
        super("BAPTISM", service, certificates, requirements, rules, resolver);
    }
}
