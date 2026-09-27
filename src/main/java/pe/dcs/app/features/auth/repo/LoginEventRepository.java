package pe.dcs.app.features.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.dcs.app.features.auth.domain.LoginEvent;

import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
}
