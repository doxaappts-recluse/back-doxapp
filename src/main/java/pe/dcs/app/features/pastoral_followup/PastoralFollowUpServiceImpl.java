package pe.dcs.app.features.pastoral_followup;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.FollowUpContact;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.PrayerRequest;
import pe.dcs.app.features.pastoral_followup.mapper.FollowUpContactMapper;
import pe.dcs.app.features.pastoral_followup.mapper.PrayerRequestMapper;
import pe.dcs.app.features.pastoral_followup.request.AssignLeaderRequest;
import pe.dcs.app.features.pastoral_followup.request.FollowUpContactFormRequest;
import pe.dcs.app.features.pastoral_followup.request.InactiveMemberFilterRequest;
import pe.dcs.app.features.pastoral_followup.request.InactiveMemberSearchRequest;
import pe.dcs.app.features.pastoral_followup.request.PastoralFollowUpHistoryRequest;
import pe.dcs.app.features.pastoral_followup.request.PrayerRequestFormRequest;
import pe.dcs.app.features.pastoral_followup.response.FollowUpContactResponse;
import pe.dcs.app.features.pastoral_followup.response.InactiveMemberResponse;
import pe.dcs.app.features.pastoral_followup.response.PastoralFollowUpSummaryResponse;
import pe.dcs.app.features.pastoral_followup.response.PrayerRequestResponse;
import pe.dcs.app.features.pastoral_followup.service.PastoralFollowUpService;
import pe.dcs.app.repository.BranchRepository;
import pe.dcs.app.repository.FollowUpContactRepository;
import pe.dcs.app.repository.MembershipRepository;
import pe.dcs.app.repository.PersonRepository;
import pe.dcs.app.repository.PrayerRequestRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

/**
 * Seguimiento pastoral genérico: aplica a CUALQUIER Person (miembro,
 * visitante o cualquier otra) — no es exclusivo del módulo
 * Visitantes, que lo usa como base (ver features.visitor). Cubre dos
 * cosas independientes sobre una Person:
 * - Person.assignedLeader: quién es el líder responsable.
 * - Historial de FollowUpContact (contactos) y PrayerRequest
 *   (peticiones de oración).
 */
@Service
@RequiredArgsConstructor
public class PastoralFollowUpServiceImpl implements PastoralFollowUpService {

    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final MembershipRepository membershipRepository;
    private final FollowUpContactRepository followUpContactRepository;
    private final PrayerRequestRepository prayerRequestRepository;
    private final FollowUpContactMapper contactMapper;
    private final PrayerRequestMapper prayerRequestMapper;
    private final AuthContext authContext;
    private final PastoralFollowUpAccessGuard accessGuard;

    // =====================================================
    // SUMMARY / LEADER
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PastoralFollowUpSummaryResponse getSummary(UUID personId) {

        accessGuard.assertCanUse();

        Person person = findPersonOrThrow(personId);

        PastoralFollowUpSummaryResponse response = new PastoralFollowUpSummaryResponse();

        response.setPersonId(person.getId());
        response.setCanManage(accessGuard.canManage(currentBranchOf(person)));

        Person leader = person.getAssignedLeader();

        if (leader != null) {
            response.setAssignedLeaderId(leader.getId());
            response.setAssignedLeaderName(leader.getName() + " " + leader.getLastname());
        }

        return response;
    }

    @Override
    @Transactional
    public void assignLeader(UUID personId, AssignLeaderRequest request) {

        accessGuard.assertCanAssignLeader();

        Person person = findPersonOrThrow(personId);

        if (request.getLeaderId() == null) {
            person.setAssignedLeader(null);
            personRepository.save(person);
            return;
        }

        if (request.getLeaderId().equals(personId)) {
            throw new Exceptions(
                    "error.personaNoPuedeSerPropioLider",
                    HttpStatus.BAD_REQUEST
            );
        }

        Person leader = findPersonOrThrow(request.getLeaderId());

        person.setAssignedLeader(leader);
        personRepository.save(person);
    }

    // =====================================================
    // CONTACTOS
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowUpContactResponse> listContacts(
            UUID personId,
            PastoralFollowUpHistoryRequest request
    ) {

        accessGuard.assertCanUse();

        findPersonOrThrow(personId);

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<FollowUpContact> page =
                followUpContactRepository.findByPersonIdOrderByContactDateDesc(personId, pageable);

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(c -> contactMapper.toResponse(c, showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional
    public void createContact(UUID personId, FollowUpContactFormRequest request) {

        accessGuard.assertCanCreate();

        Person person = findPersonOrThrow(personId);

        validateContactForm(request);

        Branch branch = resolveBranch(request.getBranchId());

        FollowUpContact contact = new FollowUpContact();

        contact.setPerson(person);
        contact.setContactDate(request.getContactDate());
        contact.setContactMethod(request.getContactMethod());
        contact.setResult(request.getResult());
        contact.setNotes(request.getNotes());
        contact.setBranch(branch);

        followUpContactRepository.save(contact);
    }

    @Override
    @Transactional
    public void updateContact(UUID personId, UUID contactId, FollowUpContactFormRequest request) {

        validateContactForm(request);

        FollowUpContact contact =
                followUpContactRepository.findById(contactId)
                        .orElseThrow(() ->
                                new Exceptions("error.contactoNoEncontrado", HttpStatus.NOT_FOUND)
                        );

        if (!contact.getPerson().getId().equals(personId)) {
            throw new Exceptions(
                    "error.contactoNoPertenecePersona",
                    HttpStatus.BAD_REQUEST
            );
        }

        accessGuard.assertCanManage(contact.getBranch());

        Branch branch =
                request.getBranchId() != null
                        ? resolveBranch(request.getBranchId())
                        : contact.getBranch();

        contact.setContactDate(request.getContactDate());
        contact.setContactMethod(request.getContactMethod());
        contact.setResult(request.getResult());
        contact.setNotes(request.getNotes());
        contact.setBranch(branch);

        followUpContactRepository.save(contact);
    }

    // =====================================================
    // PETICIONES DE ORACION
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PrayerRequestResponse> listPrayerRequests(
            UUID personId,
            PastoralFollowUpHistoryRequest request
    ) {

        accessGuard.assertCanUse();

        findPersonOrThrow(personId);

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<PrayerRequest> page =
                prayerRequestRepository.findByPersonIdOrderByRequestDateDesc(personId, pageable);

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(p -> prayerRequestMapper.toResponse(p, showAudit))
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    @Override
    @Transactional
    public void createPrayerRequest(UUID personId, PrayerRequestFormRequest request) {

        accessGuard.assertCanCreate();

        Person person = findPersonOrThrow(personId);

        validatePrayerRequestForm(request);

        Branch branch = resolveBranch(request.getBranchId());

        PrayerRequest prayerRequest = new PrayerRequest();

        prayerRequest.setPerson(person);
        prayerRequest.setRequestDate(request.getRequestDate());
        prayerRequest.setDescription(request.getDescription());
        prayerRequest.setStatus(request.getStatus());
        prayerRequest.setConfidential(request.isConfidential());
        prayerRequest.setAnsweredNotes(request.getAnsweredNotes());
        prayerRequest.setBranch(branch);

        prayerRequestRepository.save(prayerRequest);
    }

    @Override
    @Transactional
    public void updatePrayerRequest(UUID personId, UUID prayerRequestId, PrayerRequestFormRequest request) {

        validatePrayerRequestForm(request);

        PrayerRequest prayerRequest =
                prayerRequestRepository.findById(prayerRequestId)
                        .orElseThrow(() ->
                                new Exceptions("error.peticionOracionNoEncontrada", HttpStatus.NOT_FOUND)
                        );

        if (!prayerRequest.getPerson().getId().equals(personId)) {
            throw new Exceptions(
                    "error.peticionOracionNoPertenecePersona",
                    HttpStatus.BAD_REQUEST
            );
        }

        accessGuard.assertCanManage(prayerRequest.getBranch());

        Branch branch =
                request.getBranchId() != null
                        ? resolveBranch(request.getBranchId())
                        : prayerRequest.getBranch();

        prayerRequest.setRequestDate(request.getRequestDate());
        prayerRequest.setDescription(request.getDescription());
        prayerRequest.setStatus(request.getStatus());
        prayerRequest.setConfidential(request.isConfidential());
        prayerRequest.setAnsweredNotes(request.getAnsweredNotes());
        prayerRequest.setBranch(branch);

        prayerRequestRepository.save(prayerRequest);
    }

    // =====================================================
    // MIEMBROS INACTIVOS (CRM Pastoral)
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InactiveMemberResponse> searchInactiveMembers(InactiveMemberSearchRequest request) {

        accessGuard.assertCanUse();

        InactiveMemberFilterRequest filters =
                request.getFilters() != null
                        ? request.getFilters()
                        : new InactiveMemberFilterRequest();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts()
                );

        Page<Person> page =
                personRepository.findAll(
                        InactiveMemberSpecification.filter(filters, authContext),
                        pageable
                );

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(this::toInactiveMemberResponse)
                        .toList(),
                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    private InactiveMemberResponse toInactiveMemberResponse(Person person) {

        InactiveMemberResponse response = new InactiveMemberResponse();

        response.setPersonId(person.getId());
        response.setPersonName(person.getName());
        response.setPersonLastname(person.getLastname());
        response.setPersonDni(person.getDni());
        response.setPersonPhone(person.getPhone());

        Membership membership =
                membershipRepository.findByPersonIdAndCurrentTrue(person.getId())
                        .orElse(null);

        if (membership != null) {

            response.setMembershipStartDate(membership.getStartDate());

            if (membership.getBranch() != null) {
                response.setBranchId(membership.getBranch().getId());
                response.setBranchName(membership.getBranch().getName());
            }
        }

        Person leader = person.getAssignedLeader();

        if (leader != null) {
            response.setAssignedLeaderId(leader.getId());
            response.setAssignedLeaderName(leader.getName() + " " + leader.getLastname());
        }

        List<FollowUpContact> lastContact =
                followUpContactRepository.findByPersonIdOrderByContactDateDesc(
                        person.getId(),
                        PageRequest.of(0, 1)
                ).getContent();

        if (!lastContact.isEmpty()) {
            response.setLastContactDate(lastContact.get(0).getContactDate());
            response.setLastContactResult(lastContact.get(0).getResult().name());
        }

        return response;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private Branch resolveBranch(UUID requestedBranchId) {

        UUID branchId = accessGuard.resolveBranchId(requestedBranchId);

        if (branchId == null) {
            throw new Exceptions(
                    "error.debeSeleccionarSedeRegistro",
                    HttpStatus.BAD_REQUEST
            );
        }

        return branchRepository.findById(branchId)
                .orElseThrow(() ->
                        new Exceptions("error.sedeNoEncontrada", HttpStatus.NOT_FOUND)
                );
    }

    /**
     * Sede "actual" de la persona (branch activo de branchHistory),
     * usada solo para decidir canManage en el summary — no es la
     * sede de ningún registro puntual.
     */
    private Branch currentBranchOf(Person person) {

        return person.getBranchHistory()
                .stream()
                .filter(pb -> pb.getStatus() == pe.dcs.app.util.enums.StatusType.ACTIVE)
                .findFirst()
                .map(pb -> pb.getBranch())
                .orElse(null);
    }

    private void validateContactForm(FollowUpContactFormRequest request) {

        if (request.getContactDate() == null) {
            throw new Exceptions("error.fechaContactoObligatoria", HttpStatus.BAD_REQUEST);
        }

        if (request.getContactMethod() == null) {
            throw new Exceptions("error.medioContactoObligatorio", HttpStatus.BAD_REQUEST);
        }

        if (request.getResult() == null) {
            throw new Exceptions("error.resultadoContactoObligatorio", HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePrayerRequestForm(PrayerRequestFormRequest request) {

        if (request.getRequestDate() == null) {
            throw new Exceptions("error.fechaPeticionObligatoria", HttpStatus.BAD_REQUEST);
        }

        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new Exceptions("error.descripcionPeticionObligatoria", HttpStatus.BAD_REQUEST);
        }

        if (request.getStatus() == null) {
            throw new Exceptions("error.estadoPeticionObligatorio", HttpStatus.BAD_REQUEST);
        }
    }

    private Person findPersonOrThrow(UUID id) {

        return personRepository.findById(id)
                .orElseThrow(() ->
                        new Exceptions("error.personaNoEncontrada", HttpStatus.NOT_FOUND)
                );
    }
}
