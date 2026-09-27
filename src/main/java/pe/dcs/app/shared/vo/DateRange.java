package pe.dcs.app.shared.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Núcleo 01 §1 · DateRange sobre LocalDate (D9): fin ≥ inicio. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class DateRange {

    @Column(name = "start_date")
    private LocalDate start;

    @Column(name = "end_date")
    private LocalDate end;

    public boolean isValid() {
        return start != null && end != null && !end.isBefore(start);
    }

    public boolean contains(LocalDate day) {
        return day != null && start != null && !day.isBefore(start) && (end == null || !day.isAfter(end));
    }
}
