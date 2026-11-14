package lt.reviv.supplements.service;

import lt.reviv.supplements.domain.Distributor;
import lt.reviv.supplements.domain.Supplement;
import lt.reviv.supplements.domain.SupplementInteraction;
import lt.reviv.supplements.dto.DistributorRequest;
import lt.reviv.supplements.dto.DistributorResponse;
import lt.reviv.supplements.dto.InteractionRequest;
import lt.reviv.supplements.dto.InteractionResponse;
import lt.reviv.supplements.dto.SupplementRequest;
import lt.reviv.supplements.dto.SupplementResponse;
import lt.reviv.supplements.exception.SupplementConflictException;
import lt.reviv.supplements.exception.SupplementNotFoundException;
import lt.reviv.supplements.exception.SupplementValidationException;
import lt.reviv.supplements.repository.SupplementInteractionRepository;
import lt.reviv.supplements.repository.SupplementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** DP7 – Papildų valdymo komponentas (US-10: papildų katalogas). */
@Service
public class SupplementService {

    private final SupplementRepository supplementRepository;
    private final SupplementInteractionRepository interactionRepository;
    private final SupplementUsage supplementUsage;

    public SupplementService(SupplementRepository supplementRepository,
                             SupplementInteractionRepository interactionRepository,
                             SupplementUsage supplementUsage) {
        this.supplementRepository = supplementRepository;
        this.interactionRepository = interactionRepository;
        this.supplementUsage = supplementUsage;
    }

    @Transactional(readOnly = true)
    public List<SupplementResponse> list() {
        List<Supplement> all = supplementRepository.findAllByOrderByNameAsc();
        Map<Long, String> names = all.stream()
                .collect(Collectors.toMap(Supplement::getId, Supplement::getName));
        Map<Long, List<SupplementInteraction>> interactionsBySupplement = new HashMap<>();
        for (SupplementInteraction interaction : interactionRepository.findAll()) {
            interactionsBySupplement.computeIfAbsent(interaction.getSupplementAId(), k -> new ArrayList<>()).add(interaction);
            interactionsBySupplement.computeIfAbsent(interaction.getSupplementBId(), k -> new ArrayList<>()).add(interaction);
        }
        return all.stream()
                .map(s -> toResponse(s, interactionsBySupplement.getOrDefault(s.getId(), List.of()), names))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplementResponse get(Long id) {
        return toResponse(find(id));
    }

    /** DP7-2: tikrina pavadinimo unikalumą ir dozės reikšmes, tada išsaugo papildą su platintojais. */
    @Transactional
    public SupplementResponse create(SupplementRequest request) {
        validate(request, null);
        Supplement supplement = new Supplement(
                request.name().trim(),
                request.activeSubstance().trim(),
                request.form(),
                request.doseAmount(),
                request.doseUnit().trim(),
                request.maxDailyDose(),
                blankToNull(request.missedDoseRule()));
        supplement.replaceDistributors(distributorsOf(supplement, request));
        return toResponse(supplementRepository.save(supplement));
    }

    @Transactional
    public SupplementResponse update(Long id, SupplementRequest request) {
        Supplement supplement = find(id);
        validate(request, id);
        supplement.update(
                request.name().trim(),
                request.activeSubstance().trim(),
                request.form(),
                request.doseAmount(),
                request.doseUnit().trim(),
                request.maxDailyDose(),
                blankToNull(request.missedDoseRule()));
        supplement.replaceDistributors(distributorsOf(supplement, request));
        return toResponse(supplementRepository.save(supplement));
    }

    /**
     * DP7-3: jei papildas naudojamas vartojimo planuose, jis neištrinamas, o pažymimas nebenaudojamu
     * (grąžinamas atnaujintas įrašas). Kitu atveju papildas ištrinamas (grąžinama tuščia reikšmė).
     */
    @Transactional
    public Optional<SupplementResponse> delete(Long id) {
        Supplement supplement = find(id);
        if (supplementUsage.isUsedInPlans(id)) {
            supplement.markInactive();
            return Optional.of(toResponse(supplementRepository.save(supplement)));
        }
        supplementRepository.delete(supplement);
        return Optional.empty();
    }

    /** Sąveika saugoma vienu įrašu, todėl automatiškai taikoma abiem papildams. */
    @Transactional
    public SupplementResponse addInteraction(Long id, InteractionRequest request) {
        Supplement supplement = find(id);
        Long otherId = request.otherSupplementId();
        if (supplement.getId().equals(otherId)) {
            throw new SupplementValidationException("otherSupplementId", "Papildas negali sąveikauti pats su savimi");
        }
        if (!supplementRepository.existsById(otherId)) {
            throw new SupplementValidationException("otherSupplementId", "Pasirinktas papildas nerastas");
        }
        long first = Math.min(id, otherId);
        long second = Math.max(id, otherId);
        if (interactionRepository.findBySupplementAIdAndSupplementBId(first, second).isPresent()) {
            throw new SupplementConflictException("otherSupplementId", "Tokia sąveika jau yra");
        }
        interactionRepository.save(new SupplementInteraction(
                id, otherId, request.type().trim(), request.recommendation().trim()));
        return toResponse(supplement);
    }

    @Transactional
    public SupplementResponse removeInteraction(Long id, Long interactionId) {
        Supplement supplement = find(id);
        SupplementInteraction interaction = interactionRepository.findById(interactionId)
                .filter(i -> i.getSupplementAId().equals(id) || i.getSupplementBId().equals(id))
                .orElseThrow(() -> new SupplementNotFoundException("Sąveika nerasta (ID " + interactionId + ")"));
        interactionRepository.delete(interaction);
        return toResponse(supplement);
    }

    private Supplement find(Long id) {
        return supplementRepository.findWithDistributorsById(id)
                .orElseThrow(() -> new SupplementNotFoundException(id));
    }

    private void validate(SupplementRequest request, Long excludeId) {
        String name = request.name().trim();
        boolean exists = excludeId == null
                ? supplementRepository.existsByNameIgnoreCase(name)
                : supplementRepository.existsByNameIgnoreCaseAndIdNot(name, excludeId);
        if (exists) {
            throw new SupplementConflictException("name", "Papildas tokiu pavadinimu jau yra");
        }
        if (request.maxDailyDose().compareTo(request.doseAmount()) < 0) {
            throw new SupplementValidationException("maxDailyDose",
                    "Maksimali paros dozė negali būti mažesnė už vienos dozės kiekį");
        }
    }

    private static List<Distributor> distributorsOf(Supplement supplement, SupplementRequest request) {
        List<DistributorRequest> requested = request.distributors() == null ? List.of() : request.distributors();
        return requested.stream()
                .map(d -> new Distributor(supplement, d.name().trim(), d.url().trim()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SupplementResponse toResponse(Supplement supplement) {
        List<SupplementInteraction> interactions =
                interactionRepository.findBySupplementAIdOrSupplementBId(supplement.getId(), supplement.getId());
        Set<Long> otherIds = interactions.stream()
                .map(i -> i.otherSupplementId(supplement.getId()))
                .collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        supplementRepository.findAllById(otherIds).forEach(o -> names.put(o.getId(), o.getName()));
        return toResponse(supplement, interactions, names);
    }

    private SupplementResponse toResponse(Supplement supplement, List<SupplementInteraction> interactions,
                                          Map<Long, String> names) {
        List<InteractionResponse> interactionResponses = interactions.stream()
                .map(i -> {
                    Long otherId = i.otherSupplementId(supplement.getId());
                    return new InteractionResponse(i.getId(), otherId, names.get(otherId),
                            i.getType(), i.getRecommendation());
                })
                .toList();
        List<DistributorResponse> distributorResponses = supplement.getDistributors().stream()
                .map(d -> new DistributorResponse(d.getId(), d.getName(), d.getUrl()))
                .toList();
        return new SupplementResponse(
                supplement.getId(),
                supplement.getName(),
                supplement.getActiveSubstance(),
                supplement.getForm(),
                supplement.getDoseAmount(),
                supplement.getDoseUnit(),
                supplement.getMaxDailyDose(),
                supplement.getMissedDoseRule(),
                supplement.getStatus(),
                distributorResponses,
                interactionResponses);
    }
}