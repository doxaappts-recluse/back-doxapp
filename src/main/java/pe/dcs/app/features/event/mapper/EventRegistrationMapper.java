package pe.dcs.app.features.event.mapper;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.features.event.response.registration.EventRegistrationDetailResponse;
import pe.dcs.app.features.event.response.registration.EventRegistrationResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class EventRegistrationMapper extends BaseMapper {

    public EventRegistrationResponse simple(EventRegistration entity) {

        EventRegistrationResponse response =
                new EventRegistrationResponse();

        mapAudit(entity, response);

        response.setId(entity.getId());

        response.setEventId(entity.getEvent().getId());

        response.setEventName(
                entity.getEvent().getName()
        );

        response.setCategory(
                entity.getCategory()
        );

        response.setBirthDate(
                entity.getBirthDate()
        );

        response.setUserId(
                entity.getUser() != null
                        ? entity.getUser().getId()
                        : null
        );

        response.setName(entity.getName());
        response.setLastname(entity.getLastname());

        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());

        response.setQrToken(entity.getQrToken());

        response.setStatus(entity.getStatus());

        response.setRegularPrice(entity.getRegularPrice());
        response.setDiscount(entity.getDiscount());
        response.setFinalPrice(entity.getFinalPrice());

        return response;
    }

    public EventRegistrationDetailResponse detail(EventRegistration entity) {

        EventRegistrationDetailResponse response =
                new EventRegistrationDetailResponse();

        BeanUtils.copyProperties(
                entity,
                response
        );

        response.setEventId(
                entity.getEvent().getId()
        );

        response.setEventName(
                entity.getEvent().getName()
        );

        response.setUserId(
                entity.getUser() != null
                        ? entity.getUser().getId()
                        : null
        );

        return response;
    }
}