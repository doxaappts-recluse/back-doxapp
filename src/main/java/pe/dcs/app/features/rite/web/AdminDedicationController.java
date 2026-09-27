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

/** M08 · Dedication (módulo contratable CHILD_DEDICATION). */
@RestController
@RequestMapping("/api/v1/admin/child-dedications")
@ModuleAccess(module = "CHILD_DEDICATION", action = Action.V)
public class AdminDedicationController extends AbstractRiteController {

    public AdminDedicationController(RiteService service, CertificateService certificates, RiteRequirementService requirements, RiteRulesService rules, AccessScopeResolver resolver) {
        super("DEDICATION", service, certificates, requirements, rules, resolver);
    }
}
