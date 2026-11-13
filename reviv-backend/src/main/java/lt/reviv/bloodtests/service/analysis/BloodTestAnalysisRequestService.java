package lt.reviv.bloodtests.service.analysis;

import lt.reviv.bloodtests.ai.analysis.AnalysisProperties;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BloodTestAnalysisRequestService {

    private final BloodTestRepository bloodTestRepository;
    private final BloodTestAnalysisRepository analysisRepository;
    private final AppUserRepository appUserRepository;
    private final AnalysisProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public BloodTestAnalysisRequestService(
            BloodTestRepository bloodTestRepository,
            BloodTestAnalysisRepository analysisRepository,
            AppUserRepository appUserRepository,
            AnalysisProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bloodTestRepository = bloodTestRepository;
        this.analysisRepository = analysisRepository;
        this.appUserRepository = appUserRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    public record RequestResult(
            boolean newlyRequested,
            String status
    ) {}

    @Transactional
    public RequestResult requestAnalysis(
            Long bloodTestId,
            Jwt jwt
    ) {
        AppUser user = appUserRepository
                .findByGoogleId(jwt.getSubject())
                .orElseThrow(UserNotFoundException::new);

        BloodTest bloodTest = bloodTestRepository
                .findById(bloodTestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tyrimas nerastas."
                ));

        if (!bloodTest.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Tyrimas nerastas."
            );
        }

        if (!"CONFIRMED".equals(bloodTest.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pirmiausia reikia patvirtinti tyrima."
            );
        }

        var existing = analysisRepository
                .findByBloodTestId(bloodTestId);

        if (existing.isPresent()) {
            return new RequestResult(
                    false,
                    existing.get().getStatus().name()
            );
        }

        if (!properties.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DI analize siuo metu isjungta."
            );
        }


        int inserted = analysisRepository.enqueueIfAbsent(
                bloodTestId
        );

        if (inserted == 0) {
            var current = analysisRepository
                    .findByBloodTestId(bloodTestId)
                    .orElseThrow();

            return new RequestResult(
                    false,
                    current.getStatus().name()
            );
        }

        eventPublisher.publishEvent(
                new BloodTestAnalysisRequestedEvent(bloodTestId)
        );

        return new RequestResult(true, "QUEUED");

    }
}
