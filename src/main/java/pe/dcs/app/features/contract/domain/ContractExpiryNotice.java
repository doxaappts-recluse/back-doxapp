package pe.dcs.app.features.contract.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** M03 · Aviso de vencimiento ya enviado: una fila por (contrato, umbral) evita repetirlo. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contract_expiry_notice")
@IdClass(ContractExpiryNotice.Key.class)
public class ContractExpiryNotice {

    @Id
    @Column(name = "contract_id")
    private UUID contractId;

    @Id
    @Column(name = "threshold_days")
    private int thresholdDays;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "recipients", nullable = false)
    private int recipients;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private UUID contractId;
        private int thresholdDays;

        public Key(UUID contractId, int thresholdDays) {
            this.contractId = contractId;
            this.thresholdDays = thresholdDays;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && thresholdDays == k.thresholdDays && java.util.Objects.equals(contractId, k.contractId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(contractId, thresholdDays);
        }
    }
}
