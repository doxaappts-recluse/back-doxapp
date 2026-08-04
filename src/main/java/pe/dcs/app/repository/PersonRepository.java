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

    /**
     * DNI único por organización (no global): busca si algún
     * OTRO Person con ese DNI tiene al menos un PersonBranch
     * cuya sede pertenezca a esta organización, sin importar
     * la sede puntual ni si ese PersonBranch está activo.
     */
    @Query("""
        SELECT COUNT(p) > 0
        FROM Person p
        JOIN p.branchHistory pb
        JOIN pb.branch b
        JOIN b.organization o
        WHERE p.dni = :dni
        AND o.id = :organizationId
    """)
    boolean existsByDniInOrganization(
            @Param("dni") String dni,
            @Param("organizationId") UUID organizationId
    );

    @Query("""
        SELECT COUNT(p) > 0
        FROM Person p
        JOIN p.branchHistory pb
        JOIN pb.branch b
        JOIN b.organization o
        WHERE p.dni = :dni
        AND o.id = :organizationId
        AND p.id <> :excludeId
    """)
    boolean existsByDniInOrganizationAndIdNot(
            @Param("dni") String dni,
            @Param("organizationId") UUID organizationId,
            @Param("excludeId") UUID excludeId
    );

    @Query("""
        SELECT DISTINCT p
        FROM Person p
        JOIN p.branchHistory pb
        JOIN pb.branch b
        JOIN b.organization o
        WHERE p.dni = :dni
        AND o.id = :organizationId
    """)
    Optional<Person> findByDniInOrganization(
            @Param("dni") String dni,
            @Param("organizationId") UUID organizationId
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