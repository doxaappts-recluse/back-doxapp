package pe.dcs.app.features.space_reservation.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.ReservableSpace;
import pe.dcs.app.entity.SpaceReservation;
import pe.dcs.app.features.space_reservation.response.ReservableSpaceResponse;
import pe.dcs.app.features.space_reservation.response.SpaceReservationResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class SpaceReservationMapper {

    public ReservableSpaceResponse toSpaceResponse(
            ReservableSpace space,
            long reservationCount,
            boolean canManage,
            boolean showAudit
    ) {

        ReservableSpaceResponse response = new ReservableSpaceResponse();

        BaseMapper.mapAudit(space, response, showAudit);

        response.setId(space.getId());
        response.setName(space.getName());
        response.setDescription(space.getDescription());
        response.setCapacity(space.getCapacity());
        response.setStatus(space.getStatus());
        response.setReservationCount(reservationCount);
        response.setCanManage(canManage);

        if (space.getBranch() != null) {
            response.setBranchId(space.getBranch().getId());
            response.setBranchName(space.getBranch().getName());
        }

        return response;
    }

    public SpaceReservationResponse toReservationResponse(
            SpaceReservation reservation,
            boolean canManage,
            boolean showAudit
    ) {

        SpaceReservationResponse response = new SpaceReservationResponse();

        BaseMapper.mapAudit(reservation, response, showAudit);

        response.setId(reservation.getId());
        response.setSourceType(reservation.getSourceType());
        response.setSourceId(reservation.getSourceId());
        response.setPurpose(reservation.getPurpose());
        response.setRequesterName(resolveRequesterName(reservation));
        response.setStartDateTime(reservation.getStartDateTime());
        response.setEndDateTime(reservation.getEndDateTime());
        response.setStatus(reservation.getStatus());
        response.setNotes(reservation.getNotes());
        response.setCanManage(canManage);

        if (reservation.getRequesterPerson() != null) {
            response.setRequesterPersonId(reservation.getRequesterPerson().getId());
        }

        ReservableSpace space = reservation.getSpace();

        if (space != null) {
            response.setSpaceId(space.getId());
            response.setSpaceName(space.getName());

            if (space.getBranch() != null) {
                response.setBranchId(space.getBranch().getId());
                response.setBranchName(space.getBranch().getName());
            }
        }

        return response;
    }

    private String resolveRequesterName(SpaceReservation reservation) {

        if (reservation.getRequesterPerson() != null) {
            return reservation.getRequesterPerson().getName()
                    + " "
                    + reservation.getRequesterPerson().getLastname();
        }

        return reservation.getRequesterName();
    }
}
