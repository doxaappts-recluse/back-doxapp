package pe.dcs.app.features.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.response.attendance.TicketValidationResponse;
import pe.dcs.app.features.event.response.event.TicketTemplateResponse;
import pe.dcs.app.features.event.service.ticket.TicketService;
import pe.dcs.app.features.event.service.ticket.TicketValidationService;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketValidationService validationService;

    @PostMapping("/{registrationId}/generate")
    public ResponseEntity<byte[]> generate(@PathVariable UUID registrationId) {

        byte[] pdf = ticketService.generateTicket(registrationId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=ticket.pdf")
                .body(pdf);
    }

    @PostMapping("/validate/{qrToken}")
    public ApiResponse<TicketValidationResponse> validate(@PathVariable String qrToken) {

        return new ApiResponse<>(
                200,
                "OK",
                validationService.validate(qrToken)
        );
    }

    @GetMapping("/ticket-template/{id}")
    public ApiResponse<TicketTemplateResponse> getTicketTemplate(
            @PathVariable UUID id
    ) {
        return new ApiResponse<>(
                200,
                "OK",
                ticketService.getTicketTemplate(id)
        );

    }

}