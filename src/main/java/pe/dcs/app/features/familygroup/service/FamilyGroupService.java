package pe.dcs.app.features.familygroup.service;

import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.familygroup.request.FamilyGroupFormRequest;
import pe.dcs.app.features.familygroup.request.FamilyGroupSearchRequest;
import pe.dcs.app.features.familygroup.request.FamilyMemberFormRequest;
import pe.dcs.app.features.familygroup.response.FamilyGroupDetailResponse;
import pe.dcs.app.features.familygroup.response.FamilyGroupPersonSearchResponse;
import pe.dcs.app.features.familygroup.response.FamilyGroupSearchRowResponse;
import pe.dcs.app.features.familygroup.response.FamilyMemberResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

public interface FamilyGroupService {

    PageResponse<FamilyGroupSearchRowResponse> search(FamilyGroupSearchRequest request);

    FamilyGroupDetailResponse getById(UUID id);

    FamilyGroupPersonSearchResponse findPersonByDni(String dni);

    UUID create(FamilyGroupFormRequest request);

    void update(UUID id, FamilyGroupFormRequest request);

    List<FamilyMemberResponse> listMembers(UUID groupId);

    void addMember(UUID groupId, FamilyMemberFormRequest request);

    void updateMemberRole(UUID groupId, UUID memberId, FamilyMemberFormRequest request);

    void removeMember(UUID groupId, UUID memberId);

    /**
     * Efecto automático disparado por MarriageServiceImpl al crear o
     * editar un Matrimonio: si al menos un cónyuge está vinculado a
     * Person, crea o actualiza el grupo familiar correspondiente
     * (cónyuge1=HEAD_OF_HOUSEHOLD, cónyuge2=SPOUSE). Diseñado para
     * NUNCA lanzar excepción — best-effort, no debe romper el flujo
     * de registrar un matrimonio.
     */
    void syncFromMarriage(Person spouse1, Person spouse2, Branch branch);
}
