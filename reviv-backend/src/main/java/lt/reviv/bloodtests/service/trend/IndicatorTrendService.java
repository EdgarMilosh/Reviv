package lt.reviv.bloodtests.service.trend;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.dto.trend.IndicatorHistoryPoint;
import lt.reviv.bloodtests.dto.trend.IndicatorHistoryResponse;
import lt.reviv.bloodtests.dto.trend.IndicatorOptionResponse;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndicatorTrendService {

    private final AppUserRepository userRepository;
    private final BloodTestRepository bloodTestRepository;
    private final BloodTestIndicatorRepository indicatorRepository;

    public IndicatorTrendService(
            AppUserRepository userRepository,
            BloodTestRepository bloodTestRepository,
            BloodTestIndicatorRepository indicatorRepository
    ) {
        this.userRepository = userRepository;
        this.bloodTestRepository = bloodTestRepository;
        this.indicatorRepository = indicatorRepository;
    }

    @Transactional(readOnly = true)
    public List<IndicatorOptionResponse> listIndicators(Jwt jwt) {
        AppUser user = userRepository.findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new);

        Map<String, IndicatorOptionResponse> options = new LinkedHashMap<>();
        for (BloodTest test : bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(user.getId())) {
            for (BloodTestIndicator indicator : indicatorRepository.findByBloodTestIdOrderByIdAsc(test.getId())) {
                if (indicator.getName() == null || indicator.getName().isBlank()) {
                    continue;
                }
                String key = indicator.getName().trim();
                options.putIfAbsent(key, toOption(indicator));
            }
        }

        return options.values().stream()
                .sorted(Comparator.comparing(IndicatorOptionResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public IndicatorHistoryResponse getHistory(
            Jwt jwt,
            Long indicatorId,
            String period,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        AppUser user = userRepository.findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new);

        BloodTestIndicator sourceIndicator = indicatorRepository.findById(indicatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rodiklis nerastas."));

        BloodTest sourceTest = bloodTestRepository.findById(sourceIndicator.getBloodTestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tyrimas nerastas."));

        if (!sourceTest.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rodiklis nerastas.");
        }

        LocalDate effectiveFrom = resolveFromDate(period, fromDate);
        LocalDate effectiveTo = toDate != null ? toDate : LocalDate.now();

        List<IndicatorHistoryPoint> points = bloodTestRepository.findAllByUserIdOrderByUploadedAtDesc(user.getId()).stream()
                .filter(test -> includesTest(test, effectiveFrom, effectiveTo))
                .flatMap(test -> indicatorRepository.findByBloodTestIdOrderByIdAsc(test.getId()).stream())
                .filter(candidate -> candidate.getName() != null
                        && candidate.getName().equalsIgnoreCase(sourceIndicator.getName()))
                .filter(candidate -> candidate.getRawValue() != null && !candidate.getRawValue().isBlank())
                .map(candidate -> toPoint(candidate, candidate.getBloodTestId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(IndicatorHistoryPoint::date).thenComparing(IndicatorHistoryPoint::testId))
                .toList();

        return new IndicatorHistoryResponse(
                String.valueOf(indicatorId),
                sourceIndicator.getName(),
                sourceIndicator.getUnit(),
                sourceIndicator.getReferenceInterval(),
                points,
                points.size() >= 2
        );
    }

    private LocalDate resolveFromDate(String period, LocalDate fromDate) {
        if (fromDate != null) {
            return fromDate;
        }
        if (period == null || period.isBlank()) {
            return null;
        }

        LocalDate end = LocalDate.now();
        return switch (period.toLowerCase()) {
            case "1m", "1mėn", "1-menuo", "1-month", "1month" -> end.minusMonths(1);
            case "3m", "3mėn", "3-menuo", "3-month", "3month" -> end.minusMonths(3);
            case "6m", "6mėn", "6-menuo", "6-month", "6month" -> end.minusMonths(6);
            case "1y", "1metai", "1-year", "1year" -> end.minusYears(1);
            default -> null;
        };
    }

    private IndicatorOptionResponse toOption(BloodTestIndicator indicator) {
        return new IndicatorOptionResponse(
                String.valueOf(indicator.getId()),
                indicator.getName(),
                indicator.getUnit(),
                indicator.getReferenceInterval()
        );
    }

    private boolean includesTest(BloodTest test, LocalDate fromDate, LocalDate toDate) {
        if (test.getTestDate() == null) {
            return false;
        }
        if (fromDate != null && test.getTestDate().isBefore(fromDate)) {
            return false;
        }
        if (toDate != null && test.getTestDate().isAfter(toDate)) {
            return false;
        }
        return true;
    }

    private IndicatorHistoryPoint toPoint(BloodTestIndicator indicator, Long bloodTestId) {
        BloodTest bloodTest = bloodTestRepository.findById(bloodTestId).orElse(null);
        if (bloodTest == null || bloodTest.getTestDate() == null) {
            return null;
        }
        return new IndicatorHistoryPoint(
                bloodTest.getId(),
                bloodTest.getTestDate(),
                indicator.getRawValue(),
                indicator.getUnit(),
                indicator.getReferenceInterval()
        );
    }
}
