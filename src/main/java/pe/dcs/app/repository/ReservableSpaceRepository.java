package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.ReservableSpace;

import java.util.UUID;

@Repository
public interface ReservableSpaceRepository extends JpaRepository<ReservableSpace, UUID>, JpaSpecificationExecutor<ReservableSpace> {
}
