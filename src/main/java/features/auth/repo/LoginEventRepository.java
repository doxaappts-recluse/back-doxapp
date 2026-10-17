package features.auth.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import features.auth.domain.LoginEvent;

import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
}
