package pe.dcs.app.features.space_reservation.service;

import pe.dcs.app.features.space_reservation.request.ReservableSpaceFormRequest;
import pe.dcs.app.features.space_reservation.request.ReservableSpaceSearchRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationFormRequest;
import pe.dcs.app.features.space_reservation.request.SpaceReservationSearchRequest;
import pe.dcs.app.features.space_reservation.response.ReservableSpaceResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationPersonSearchResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

public interface SpaceReservationService {

    /** Buscar persona por DNI para asignarla como responsable de una reserva — mismo patrón que SmallGroup/Academia Bíblica. */
    SpaceReservationPersonSearchResponse findPersonByDni(String dni);

    // Espacios
    PageResponse<ReservableSpaceResponse> searchSpaces(ReservableSpaceSearchRequest request);

    ReservableSpaceResponse getSpaceById(UUID id);

    UUID createSpace(ReservableSpaceFormRequest request);

    void updateSpace(UUID id, ReservableSpaceFormRequest request);

    // Reservas
    PageResponse<SpaceReservationResponse> searchReservations(SpaceReservationSearchRequest request);

    SpaceReservationResponse getReservationById(UUID id);

    UUID createReservation(SpaceReservationFormRequest request);

    void updateReservation(UUID id, SpaceReservationFormRequest request);

    void cancelReservation(UUID id);
}
