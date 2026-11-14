
package lt.reviv.bloodtests.service.review;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.dto.review.BloodTestDetailResponse;

import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.ExtractionIssueRepository;

import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import lt.reviv.common.error.UserNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lt.reviv.bloodtests.domain.ExtractionIssue;
import lt.reviv.bloodtests.dto.review.MarkExtractionIssueRequest;

import lt.reviv.bloodtests.ai.OpenAiProperties;
import org.springframework.context.ApplicationEventPublisher;


@Service
public class BloodTestReviewService {

    private final BloodTestRepository bloodTestRepository;
    private final BloodTestIndicatorRepository indicatorRepository;
    private final ExtractionIssueRepository issueRepository;
    private final AppUserRepository appUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OpenAiProperties aiProperties;

    public BloodTestReviewService(
            BloodTestRepository bloodTestRepository,
            BloodTestIndicatorRepository indicatorRepository,
            ExtractionIssueRepository issueRepository,
            AppUserRepository appUserRepository,
            ApplicationEventPublisher eventPublisher,
            OpenAiProperties aiProperties
    ) {
        this.bloodTestRepository = bloodTestRepository;
        this.indicatorRepository = indicatorRepository;
        this.issueRepository = issueRepository;
        this.appUserRepository = appUserRepository;
        this.eventPublisher = eventPublisher;
        this.aiProperties = aiProperties;
    }


    @Transactional(readOnly = true)
    public BloodTestDetailResponse getDetails(
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

        var indicators = indicatorRepository
                .findByBloodTestIdOrderByIdAsc(bloodTestId);

        var issues = issueRepository
                .findByBloodTestIdOrderByCreatedAtAsc(bloodTestId);

        return BloodTestDetailResponse.from(
                bloodTest,
                indicators,
                issues
        );
    }


    @Transactional
    public BloodTestDetailResponse markIssue(
            Long bloodTestId,
            MarkExtractionIssueRequest request,
            Jwt jwt
    ) {
        BloodTestDetailResponse details =
                getDetails(bloodTestId, jwt);

        if (!"NEEDS_REVIEW".equals(details.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tyrimas dar neparuostas perziurai."
            );
        }

        indicatorRepository.findByIdAndBloodTestId(
                request.indicatorId(),
                bloodTestId
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Rodiklis nerastas."
        ));

        boolean alreadyMarked = issueRepository
                .findByBloodTestIdAndResolvedFalse(bloodTestId)
                .stream()
                .anyMatch(issue ->
                        issue.getIndicatorId().equals(
                                request.indicatorId()
                        )
                                && issue.getFieldName().equals(
                                request.fieldName()
                        )
                );

        if (alreadyMarked) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sis rodiklio laukas jau pazymetas kaip klaidingas."
            );
        }

        ExtractionIssue issue = new ExtractionIssue(
                bloodTestId,
                request.indicatorId(),
                request.fieldName(),
                request.note()
        );

        issueRepository.save(issue);

        return getDetails(bloodTestId, jwt);
    }


    @Transactional
    public BloodTestDetailResponse unmarkIssue(
            Long bloodTestId,
            Long issueId,
            Jwt jwt
    ) {

        BloodTestDetailResponse details =
                getDetails(bloodTestId, jwt);

        if (!"NEEDS_REVIEW".equals(details.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tyrimo klaidu keisti nebegalima."
            );
        }

        ExtractionIssue issue = issueRepository
                .findByIdAndBloodTestId(issueId, bloodTestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Klaida nerasta."
                ));

        if (issue.isResolved()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Jau istaisyto klaidos iraso salinti negalima."
            );
        }

        issueRepository.delete(issue);
        issueRepository.flush();

        return getDetails(bloodTestId, jwt);
    }


    @Transactional
    public BloodTestDetailResponse requestCorrection(
            Long bloodTestId,
            Jwt jwt
    ) {
        BloodTestDetailResponse details =
                getDetails(bloodTestId, jwt);

        if (!"NEEDS_REVIEW".equals(details.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tyrimas nera paruostas pakartotiniam istraukimui."
            );
        }

        if (!aiProperties.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DI duomenu istraukimas siuo metu isjungtas."
            );
        }

        if (!issueRepository.existsByBloodTestIdAndResolvedFalse(
                bloodTestId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nera pazymetu klaidu, kurias reiketu taisyti."
            );
        }

        BloodTest test = bloodTestRepository
                .findById(bloodTestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tyrimas nerastas."
                ));

        if (test.getExtractionAttempts()
                >= aiProperties.maxAttempts()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pasiektas maksimalus DI apdorojimo bandymu skaicius."
            );
        }

        test.queueCorrection();

        eventPublisher.publishEvent(
                new BloodTestCorrectionRequestedEvent(bloodTestId)
        );

        return getDetails(bloodTestId, jwt);
    }


    @Transactional
    public BloodTestDetailResponse confirm(
            Long bloodTestId,
            Jwt jwt
    ) {

        BloodTestDetailResponse details =
                getDetails(bloodTestId, jwt);


        if (!"NEEDS_REVIEW".equals(details.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tyrimas dar neparuostas patvirtinimui."
            );
        }


        if (indicatorRepository
                .findByBloodTestIdOrderByIdAsc(bloodTestId)
                .isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tyrime nera istrauktu rodikliu."
            );
        }


        if (issueRepository.existsByBloodTestIdAndResolvedFalse(
                bloodTestId
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pirmiausia istaisyti pazymetas klaidas."
            );
        }


        BloodTest test = bloodTestRepository
                .findById(bloodTestId)
                .orElseThrow();

        test.confirm();

        return getDetails(bloodTestId, jwt);
    }

    @Transactional(readOnly = true)
    public byte[] getOriginalPdf(
            Long bloodTestId,
            Jwt jwt
    ) {

        getDetails(bloodTestId, jwt);

        BloodTest test = bloodTestRepository
                .findById(bloodTestId)
                .orElseThrow();

        return test.getPdfData();
    }




}
