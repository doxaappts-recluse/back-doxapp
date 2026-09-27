package pe.dcs.app.features.support.dto;

/**
 * M22 · datos de la sede que el ORG_ADMIN pide al abrir un caso NEW_BRANCH. Solo {@code name}/{@code code} son obligatorios
 * (mismas reglas que {@code BranchService.create}); el resto queda vacío y se completa después en M04 si no se envía.
 * Se guarda tal cual como payload de la {@code ApprovalRequest NEW_BRANCH_REQUEST}; {@code NewBranchRequestHandler} lo
 * reconstruye a un {@code BranchCreateRequest} real al resolver el caso.
 */
public record NewBranchDraft(
        String name, String code, String displayName, String phone, String email,
        String openingDate, String timezone, String addressLine, String addressCity, String addressCountry
) {
}
