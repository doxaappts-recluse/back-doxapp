package pe.dcs.app.features.contract.dto;

import java.util.List;

/**
 * Vista de la organización (N2). {@code current}: ACTIVE, o si no hay, SUSPENDED, PENDING o el último cerrado; null si nunca
 * tuvo contrato. {@code limited}: la organización no tiene un contrato ACTIVE (modo limitado).
 */
public record AdminContractResponse(ContractResponse current, boolean limited, boolean expiringSoon, Long daysToExpire,
                                    List<ContractResponse.HistoryItem> history) {
}
