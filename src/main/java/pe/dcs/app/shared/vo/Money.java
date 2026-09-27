package pe.dcs.app.shared.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Núcleo 01 §1 · Money: NUMERIC(14,2) ≥ 0 + moneda ISO-4217 (por defecto la de la organización). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Money {

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    public static Money of(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        return new Money(amount.setScale(2, RoundingMode.HALF_UP), currency);
    }
}
