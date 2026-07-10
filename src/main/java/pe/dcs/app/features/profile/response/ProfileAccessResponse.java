package pe.dcs.app.features.profile.response;

public record ProfileAccessResponse(
        String organization,
        String branch,
        String role
){}