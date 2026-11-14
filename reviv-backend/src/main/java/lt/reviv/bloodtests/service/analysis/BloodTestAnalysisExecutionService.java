package lt.reviv.bloodtests.service.analysis;

import lt.reviv.bloodtests.ai.analysis.AnalysisProperties;
import lt.reviv.bloodtests.ai.analysis.OpenAiAnalysisClient;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.profile.domain.Profile;
import lt.reviv.profile.repository.ProfileRepository;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class BloodTestAnalysisExecutionService {

    private static final int BATCH_SIZE = 8;
    private static final int MAX_INDICATORS = 40;

    private final BloodTestAnalysisService analysisService;
    private final AnalysisPersistenceService persistenceService;
    private final OpenAiAnalysisClient aiClient;
    private final BloodTestRepository bloodTestRepository;
    private final ProfileRepository profileRepository;
    private final AnalysisProperties properties;
    private final JsonMapper mapper;

    public BloodTestAnalysisExecutionService(
            BloodTestAnalysisService analysisService,
            AnalysisPersistenceService persistenceService,
            OpenAiAnalysisClient aiClient,
            BloodTestRepository bloodTestRepository,
            ProfileRepository profileRepository,
            AnalysisProperties properties,
            JsonMapper mapper
    ) {
        this.analysisService = analysisService;
        this.persistenceService = persistenceService;
        this.aiClient = aiClient;
        this.bloodTestRepository = bloodTestRepository;
        this.profileRepository = profileRepository;
        this.properties = properties;
        this.mapper = mapper;
    }

    public boolean execute(Long bloodTestId) {


        var prepared = analysisService.prepare(bloodTestId);

        if (prepared.forAi().size() > MAX_INDICATORS) {
            throw new IllegalStateException(
                    "Virsytas vieno tyrimo analizes limitas."
            );
        }


        BloodTest bloodTest = bloodTestRepository
                .findById(bloodTestId)
                .orElseThrow();

        var profile = profileRepository
                .findByUserId(bloodTest.getUserId())
                .map(this::toProfileContext)
                .orElseGet(this::emptyProfile);

        String profileSnapshot;

        try {
            profileSnapshot = mapper.writeValueAsString(profile);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Nepavyko paruosti profilio duomenu.", e
            );
        }

        boolean started = persistenceService.start(
                bloodTestId,
                properties.model(),
                profileSnapshot
        );

        if (!started) {
            return false;
        }

        try {
            var explanations =
                    new ArrayList<OpenAiAnalysisClient.Explanation>();

            var indicators = prepared.forAi();

            if (!indicators.isEmpty()) {

                if (!properties.enabled()) {
                    throw new IllegalStateException(
                            "US-7 AI analize isjungta."
                    );
                }

                for (int i = 0; i < indicators.size(); i += BATCH_SIZE) {

                    var batch = indicators.subList(
                            i,
                            Math.min(i + BATCH_SIZE, indicators.size())
                    );

                    var inputs = batch.stream()
                            .map(indicator ->
                                    new OpenAiAnalysisClient.IndicatorInput(
                                            indicator.indicatorId(),
                                            indicator.name(),
                                            indicator.rawValue(),
                                            indicator.unit(),
                                            indicator.referenceInterval(),
                                            indicator.status().name(),
                                            indicator.deviationPercent()
                                    )
                            )
                            .toList();

                    var result = aiClient.explain(inputs, profile);

                    explanations.addAll(result);
                }
            }

            persistenceService.complete(
                    bloodTestId,
                    explanations
            );

            return true;

        } catch (RuntimeException e) {

            persistenceService.fail(bloodTestId);

            throw e;
        }
    }

    private OpenAiAnalysisClient.ProfileContext toProfileContext(
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

        return new OpenAiAnalysisClient.ProfileContext(
                age,
                profile.getHeightCm(),
                profile.getWeightKg(),
                Arrays.asList(profile.getHarmfulHabits()),
                Arrays.asList(profile.getAllergies())
        );
    }

    private OpenAiAnalysisClient.ProfileContext emptyProfile() {
        return new OpenAiAnalysisClient.ProfileContext(
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
