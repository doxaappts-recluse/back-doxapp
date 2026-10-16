package pe.dcs.app.features.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

    List<CatalogItem> findByTypeAndOrganizationIdIsNullOrderBySortOrderAscNameEsAsc(String type);

    List<CatalogItem> findByTypeAndOrganizationIdOrderBySortOrderAscNameEsAsc(String type, UUID organizationId);

    Optional<CatalogItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CatalogItem> findByIdAndOrganizationIdIsNull(UUID id);

    boolean existsByTypeAndCodeAndOrganizationIdIsNull(String type, String code);

    boolean existsByTypeAndCodeAndOrganizationId(String type, String code, UUID organizationId);

    boolean existsByTypeAndCode(String type, String code);

    long countByParentId(UUID parentId);

    @Query("select distinct c.parentId from CatalogItem c where c.parentId in :ids")
    Set<UUID> parentsWithChildren(@Param("ids") java.util.Collection<UUID> ids);

    @Query("select count(c) from CatalogItem c where c.type = :type and c.organizationId is null and c.active = true")
    long countActiveBase(@Param("type") String type);

    @Query("select count(c) from CatalogItem c where c.type = :type and c.organizationId = :org and c.active = true")
    long countActiveOrg(@Param("type") String type, @Param("org") UUID org);

    @Query("select c.type, count(c), sum(case when c.active = true then 1 else 0 end) from CatalogItem c where c.organizationId is null group by c.type")
    List<Object[]> baseCounts();

    @Query(value = "select item_id from catalog_item_hidden where organization_id = :org", nativeQuery = true)
    Set<UUID> hiddenIds(@Param("org") UUID org);

    @Modifying
    @Query(value = "insert into catalog_item_hidden (organization_id, item_id, hidden_at) values (:org, :item, now()) on conflict do nothing", nativeQuery = true)
    int hide(@Param("org") UUID org, @Param("item") UUID item);

    @Modifying
    @Query(value = "delete from catalog_item_hidden where organization_id = :org and item_id = :item", nativeQuery = true)
    int show(@Param("org") UUID org, @Param("item") UUID item);

    @Modifying
    @Query(value = "delete from catalog_item_hidden where item_id = :item", nativeQuery = true)
    int showAll(@Param("item") UUID item);
}
