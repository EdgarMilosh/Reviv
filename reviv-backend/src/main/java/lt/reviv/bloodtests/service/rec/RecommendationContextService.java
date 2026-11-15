package lt.reviv.bloodtests.service.rec;

import lt.reviv.bloodtests.ai.rec.OllamaRecommendationClient;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.service.analysis.CriticalIndicatorService;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;
import lt.reviv.bloodtests.service.IndicatorStatusCalculator;
import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.repository.ProfileRepository;
import lt.reviv.supplements.domain.SupplementStatus;
import lt.reviv.supplements.repository.SupplementRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import lt.reviv.supplements.repository.SupplementInteractionRepository;


import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class RecommendationContextService {

    private final BloodTestReviewService reviewService;
    private final BloodTestRepository bloodTestRepository;
    private final SupplementRepository supplementRepository;
    private final ProfileRepository profileRepository;
    private final CriticalIndicatorService criticalService;
    private final SupplementInteractionRepository interactionRepository;


    public RecommendationContextService(
            BloodTestReviewService reviewService,
            BloodTestRepository bloodTestRepository,
            SupplementRepository supplementRepository,
            ProfileRepository profileRepository,
            CriticalIndicatorService criticalService,
            SupplementInteractionRepository interactionRepository

    ) {
        this.reviewService = reviewService;
        this.bloodTestRepository = bloodTestRepository;
        this.supplementRepository = supplementRepository;
        this.profileRepository = profileRepository;
        this.criticalService = criticalService;
        this.interactionRepository = interactionRepository;
    }

    public record RecommendationContext(
            List<OllamaRecommendationClient.IndicatorInput> indicators,
            List<OllamaRecommendationClient.SupplementInput> catalog,
            List<OllamaRecommendationClient.InteractionInput> interactions,
            OllamaRecommendationClient.ProfileInput profile,
            int normalCount,
            int undeterminedCount,
            boolean criticalDetected,
            boolean criticalityNotAssessed
    ) {}

    @Transactional(readOnly = true)
    public RecommendationContext prepare(
            Long bloodTestId,
            Jwt jwt
    ) {

        var details = reviewService.getDetails(bloodTestId, jwt);

        if (!"CONFIRMED".equals(details.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pirmiausia reikia patvirtinti tyrimą."
            );
        }

        var indicators = new ArrayList<
                OllamaRecommendationClient.IndicatorInput>();

        int normalCount = 0;
        int undeterminedCount = 0;
        boolean criticalDetected = false;
        boolean criticalityNotAssessed = false;

        for (var indicator : details.indicators()) {

            var status = indicator.indicatorStatus();

            if (status == IndicatorStatusCalculator.Status.WITHIN_RANGE) {
                normalCount++;
                continue;
            }

            if (status == IndicatorStatusCalculator.Status.UNDETERMINED) {
                undeterminedCount++;
                continue;
            }

            var critical = criticalService.assess(
                    indicator.name(),
                    indicator.rawValue(),
                    indicator.unit()
            );

            if (critical == CriticalIndicatorService
                    .CriticalStatus.CRITICAL) {
                criticalDetected = true;
                continue;
            }

            if (critical == CriticalIndicatorService
                    .CriticalStatus.NOT_ASSESSED) {
                criticalityNotAssessed = true;
            }

            if (status != IndicatorStatusCalculator.Status.BELOW_RANGE) {
                continue;
            }

            indicators.add(
                    new OllamaRecommendationClient.IndicatorInput(
                            indicator.id(),
                            indicator.name(),
                            indicator.rawValue(),
                            indicator.unit(),
                            indicator.referenceInterval(),
                            status.name()
                    )
            );
        }

        if (criticalDetected) {
            indicators.clear();
        }

        var catalog = supplementRepository.findAllByOrderByNameAsc()
                .stream()
                .filter(s -> s.getStatus() == SupplementStatus.ACTIVE)
                .map(s -> new OllamaRecommendationClient.SupplementInput(
                        s.getId(),
                        s.getName(),
                        s.getActiveSubstance(),
                        s.getForm().name(),
                        s.getDoseAmount(),
                        s.getDoseUnit(),
                        s.getMaxDailyDose()
                ))
                .toList();

        var bloodTest = bloodTestRepository.findById(bloodTestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tyrimas nerastas."
                ));

        var profile = profileRepository
                .findByUserId(bloodTest.getUserId())
                .map(this::mapProfile)
                .orElseGet(() ->
                        new OllamaRecommendationClient.ProfileInput(
                                null,
                                List.of(),
                                List.of()
                        )
                );






        // Gauname aktyvių katalogo papildų ID
        var activeIds = catalog.stream()
                .map(OllamaRecommendationClient.SupplementInput::supplementId)
                .collect(java.util.stream.Collectors.toSet());

        // Gauname tik aktyvių papildų tarpusavio sąveikas
        var interactions = interactionRepository.findAll()
                .stream()
                .filter(i ->
                        activeIds.contains(i.getSupplementAId())
                                && activeIds.contains(i.getSupplementBId())
                )
                .map(i -> new OllamaRecommendationClient.InteractionInput(
                        i.getSupplementAId(),
                        i.getSupplementBId(),
                        i.getType(),
                        i.getRecommendation()
                ))
                .toList();


        return new RecommendationContext(
                List.copyOf(indicators),
                List.copyOf(catalog),
                List.copyOf(interactions),
                profile,
                normalCount,
                undeterminedCount,
                criticalDetected,
                criticalityNotAssessed
        );
    }

    private OllamaRecommendationClient.ProfileInput mapProfile(
            Profile profile
    ) {
        Integer age = null;

        if (profile.getBirthDate() != null
                && !profile.getBirthDate().isAfter(LocalDate.now())) {

            age = Period.between(
                    profile.getBirthDate(),
                    LocalDate.now()
            ).getYears();
        }

        return new OllamaRecommendationClient.ProfileInput(
                age,
                Arrays.asList(profile.getAllergies()),
                Arrays.asList(profile.getHarmfulHabits())
        );
    }
}

