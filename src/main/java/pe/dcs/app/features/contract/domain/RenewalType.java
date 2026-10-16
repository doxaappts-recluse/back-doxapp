package pe.dcs.app.features.contract.domain;

/** Cómo nació el contrato: NEW (alta), RENEWAL, UPGRADE (más plan) o DOWNGRADE (menos plan). */
public enum RenewalType {
    NEW, RENEWAL, UPGRADE, DOWNGRADE
}
