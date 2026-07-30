package pe.dcs.app.features.event.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.EventFinance;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.event.response.finance.EventFinanceResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class EventFinanceMapper {

    public EventFinanceResponse simple(
            EventFinance finance,
            boolean showAudit
    ) {

        EventFinanceResponse response =
                new EventFinanceResponse();

        BaseMapper.mapAudit(finance, response, showAudit);

        response.setId(
                finance.getId()
        );

        response.setEventId(
                finance.getEvent().getId()
        );

        response.setEventName(
                finance.getEvent().getName()
        );

        response.setType(
                finance.getType()
        );

        response.setStatus(
                finance.getStatus()
        );

        response.setDescription(
                finance.getDescription()
        );

        response.setAmount(
                finance.getAmount()
        );

        response.setTransactionDate(
                finance.getTransactionDate()
        );

        response.setObservations(
                finance.getObservations()
        );

        response.setRejectionReason(
                finance.getRejectionReason()
        );

        response.setApprovedAt(
                finance.getApprovedAt()
        );

        if (finance.getCreatedByUser() != null) {

            response.setCreatedByUserId(
                    finance.getCreatedByUser().getId()
            );

            response.setCreatedByUserName(
                    buildFullName(
                            finance.getCreatedByUser()
                    )
            );
        }

        if (finance.getApprovedByUser() != null) {

            response.setApprovedByUserId(
                    finance.getApprovedByUser().getId()
            );

            response.setApprovedByUserName(
                    buildFullName(
                            finance.getApprovedByUser()
                    )
            );
        }

        return response;
    }

    private String buildFullName(
            Person user
    ) {

        return String.format(
                "%s %s",
                user.getName(),
                user.getLastname()
        ).trim();
    }
}