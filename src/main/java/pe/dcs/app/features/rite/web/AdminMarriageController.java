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

/** M08 · Marriage (módulo contratable MARRIAGE). */
@RestController
@RequestMapping("/api/v1/admin/marriages")
@ModuleAccess(module = "MARRIAGE", action = Action.V)
public class AdminMarriageController extends AbstractRiteController {

    public AdminMarriageController(RiteService service, CertificateService certificates, RiteRequirementService requirements, RiteRulesService rules, AccessScopeResolver resolver) {
        super("MARRIAGE", service, certificates, requirements, rules, resolver);
    }
}
