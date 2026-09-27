package pe.dcs.app.features.rite.service;

import org.springframework.stereotype.Component;
import pe.dcs.app.features.approval.service.ApprovalHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * M08 · Handlers de aprobación (M21): membresía y los tres ritos. Cada tipo decide con la acción A de su propio módulo. Al aprobar se comprueban
 * los requisitos [V10]; al rechazar o cancelar la solicitud, el registro queda CANCELLED con el motivo.
 */
public final class RiteApprovalHandlers {

    private RiteApprovalHandlers() {
    }

    abstract static class Base implements ApprovalHandler {

        @Override
        public Map<String, String> notifyParams(ApprovalRow r) {
            Map<String, String> m = new HashMap<>();
            String p2 = r.payload().get("person2Name") == null ? null : String.valueOf(r.payload().get("person2Name"));
            String p1 = String.valueOf(r.payload().get("personName"));
            m.put("person", p2 == null ? p1 : p1 + " y " + p2);
            m.put("branch", String.valueOf(r.payload().get("branchName")));
            return m;
        }
    }

    @Component
    public static class Membership extends Base {
        private final MembershipService service;

        public Membership(MembershipService service) {
            this.service = service;
        }

        @Override
        public String type() {
            return "RITE_MEMBERSHIP";
        }

        @Override
        public String moduleCode() {
            return MembershipService.MODULE;
        }

        @Override
        public void onApprove(Decision d) {
            service.onApproved(d.request(), d.actorPersonId());
        }

        @Override
        public void onReject(Decision d) {
            service.onClosed(d.request(), d.note());
        }

        @Override
        public void onCancel(Decision d) {
            service.onClosed(d.request(), d.note());
        }
    }

    abstract static class RiteBase extends Base {
        private final RiteService service;

        RiteBase(RiteService service) {
            this.service = service;
        }

        @Override
        public void onApprove(Decision d) {
            service.onApproved(d.request(), d.actorPersonId());
        }

        @Override
        public void onReject(Decision d) {
            service.onClosed(d.request(), d.note());
        }

        @Override
        public void onCancel(Decision d) {
            service.onClosed(d.request(), d.note());
        }
    }

    @Component
    public static class Baptism extends RiteBase {
        public Baptism(RiteService service) {
            super(service);
        }

        @Override
        public String type() {
            return "RITE_BAPTISM";
        }

        @Override
        public String moduleCode() {
            return "BAPTISM";
        }
    }

    @Component
    public static class Marriage extends RiteBase {
        public Marriage(RiteService service) {
            super(service);
        }

        @Override
        public String type() {
            return "RITE_MARRIAGE";
        }

        @Override
        public String moduleCode() {
            return "MARRIAGE";
        }
    }

    @Component
    public static class Dedication extends RiteBase {
        public Dedication(RiteService service) {
            super(service);
        }

        @Override
        public String type() {
            return "RITE_DEDICATION";
        }

        @Override
        public String moduleCode() {
            return "CHILD_DEDICATION";
        }
    }
}
