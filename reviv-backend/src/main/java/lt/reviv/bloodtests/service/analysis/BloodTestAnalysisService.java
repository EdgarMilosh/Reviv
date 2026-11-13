package lt.reviv.bloodtests.service.analysis;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class BloodTestAnalysisService {

    private final BloodTestRepository bloodTestRepository;
    private final BloodTestIndicatorRepository indicatorRepository;
    private final IndicatorDeviationCalculator deviationCalculator;
    private final CriticalIndicatorService criticalIndicatorService;

    public BloodTestAnalysisService(
            BloodTestRepository bloodTestRepository,
            BloodTestIndicatorRepository indicatorRepository,
            IndicatorDeviationCalculator deviationCalculator,
            CriticalIndicatorService criticalIndicatorService
    ) {
        this.bloodTestRepository = bloodTestRepository;
        this.indicatorRepository = indicatorRepository;
        this.deviationCalculator = deviationCalculator;
        this.criticalIndicatorService = criticalIndicatorService;
    }

    public record AnalyzedIndicator(
            Long indicatorId,
            String name,
            String rawValue,
            String unit,
            String referenceInterval,
            IndicatorDeviationCalculator.Status status,
            BigDecimal deviationPercent,
            CriticalIndicatorService.CriticalStatus criticalStatus
    ) {}

    public record PreparedAnalysis(
            Long bloodTestId,
            List<AnalyzedIndicator> forAi,
            List<AnalyzedIndicator> critical,
            List<AnalyzedIndicator> unknown,
            int normalCount
    ) {}

    @Transactional(readOnly = true)
    public PreparedAnalysis prepare(Long bloodTestId) {

        BloodTest bloodTest = bloodTestRepository
                .findById(bloodTestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tyrimas nerastas."
                ));

        if (!"CONFIRMED".equals(bloodTest.getStatus())) {
            throw new IllegalStateException(
                    "Analizuoti galima tik patvirtintą tyrimą."
            );
        }

        List<BloodTestIndicator> indicators =
                indicatorRepository.findByBloodTestIdOrderByIdAsc(
                        bloodTestId
                );

        List<AnalyzedIndicator> forAi = new ArrayList<>();
        List<AnalyzedIndicator> critical = new ArrayList<>();
        List<AnalyzedIndicator> unknown = new ArrayList<>();

        int normalCount = 0;

        for (BloodTestIndicator indicator : indicators) {

            var deviation = deviationCalculator.calculate(
                    indicator.getRawValue(),
                    indicator.getReferenceInterval()
            );

            if (deviation.status()
                    == IndicatorDeviationCalculator.Status.NORMAL) {
                normalCount++;
                continue;
            }

            var criticalStatus = criticalIndicatorService.assess(
                    indicator.getName(),
                    indicator.getRawValue(),
                    indicator.getUnit()
            );

            AnalyzedIndicator analyzed = new AnalyzedIndicator(
                    indicator.getId(),
                    indicator.getName(),
                    indicator.getRawValue(),
                    indicator.getUnit(),
                    indicator.getReferenceInterval(),
                    deviation.status(),
                    deviation.percentage(),
                    criticalStatus
            );

            if (criticalStatus
                    == CriticalIndicatorService.CriticalStatus.CRITICAL) {

                critical.add(analyzed);

            } else if (deviation.status()
                    == IndicatorDeviationCalculator.Status.UNKNOWN) {

                unknown.add(analyzed);

            } else {

                forAi.add(analyzed);
            }
        }

        return new PreparedAnalysis(
                bloodTestId,
                List.copyOf(forAi),
                List.copyOf(critical),
                List.copyOf(unknown),
                normalCount
        );
    }
}
