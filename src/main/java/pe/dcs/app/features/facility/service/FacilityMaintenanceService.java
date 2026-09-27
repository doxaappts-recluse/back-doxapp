package pe.dcs.app.features.facility.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** M16 · Tarea periódica: [V16] asignaciones vencidas → OVERDUE (avisa una vez). */
@Service
@RequiredArgsConstructor
public class FacilityMaintenanceService {

    private final InventoryAssignmentService assignments;

    public Map<String, Integer> run() {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("overdue", assignments.markOverdue());
        return out;
    }
}
