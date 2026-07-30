package pe.dcs.app.util.enums.contract;

public enum ContractStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    SUSPENDED,
    PENDING,
    /**
     * Terminal, distinto de CANCELLED/EXPIRED: el contrato no se
     * canceló ni venció por fecha, sino que un cambio comercial
     * (plan/precio/módulos/licencias) generó un contrato nuevo que
     * lo reemplaza (ver Contract.previousContract y
     * ContractServiceImpl.replaceWithNewVersion()).
     */
    REPLACED
}