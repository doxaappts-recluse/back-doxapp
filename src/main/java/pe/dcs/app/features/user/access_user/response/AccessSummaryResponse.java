package pe.dcs.app.features.user.access_user.response;

public record AccessSummaryResponse(
        String organization,
        String branch,
        String role
) {}