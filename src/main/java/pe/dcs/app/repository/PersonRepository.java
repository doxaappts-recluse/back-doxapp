package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.dcs.app.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonRepository extends JpaRepository<Person, UUID>, JpaSpecificationExecutor<Person> {

    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(
            String dni,
            UUID id
    );

    @Query("""
        SELECT DISTINCT p
        FROM Person p
        LEFT JOIN FETCH p.credential c
        LEFT JOIN FETCH p.accesses ua
        LEFT JOIN FETCH ua.organization
        LEFT JOIN FETCH ua.branch
        LEFT JOIN FETCH ua.role
        WHERE c.username = :username
    """)
    Optional<Person> findProfileByUsername(
            @Param("username") String username
    );

}