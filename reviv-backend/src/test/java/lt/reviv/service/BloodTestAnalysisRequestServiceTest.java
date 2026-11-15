package lt.reviv.service;

import lt.reviv.bloodtests.ai.analysis.AnalysisProperties;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestAnalysis;
import lt.reviv.bloodtests.repository.BloodTestAnalysisRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.service.analysis.BloodTestAnalysisRequestService;
import lt.reviv.bloodtests.service.analysis.BloodTestAnalysisRequestedEvent;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodTestAnalysisRequestServiceTest {
    private static final Long TEST_ID = 27L;
    private static final Long USER_ID = 7L;

    @Mock BloodTestRepository bloodTestRepository;
    @Mock BloodTestAnalysisRepository analysisRepository;
    @Mock AppUserRepository appUserRepository;
    @Mock ApplicationEventPublisher publisher;

    private Jwt jwt;

    @BeforeEach
    void setup() {
        jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("google-user")
                .build();
    }

    private BloodTestAnalysisRequestService service(boolean enabled) {
        return new BloodTestAnalysisRequestService(
                bloodTestRepository, analysisRepository, appUserRepository,
                new AnalysisProperties(enabled, "gpt-test", 500, 2), publisher);
    }

    private void authenticatedOwner() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        when(appUserRepository.findByGoogleId("google-user"))
                .thenReturn(Optional.of(user));
    }

    private void ownedTest(String status) {
        BloodTest test = mock(BloodTest.class);
        when(test.getUserId()).thenReturn(USER_ID);
        when(test.getStatus()).thenReturn(status);
        when(bloodTestRepository.findById(TEST_ID))
                .thenReturn(Optional.of(test));
    }

    @Test
    void rejectsMissingUser() {
        when(appUserRepository.findByGoogleId("google-user"))
                .thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class,
                () -> service(true).requestAnalysis(TEST_ID, jwt));
        verifyNoInteractions(bloodTestRepository, analysisRepository, publisher);
    }


    @Test
    void missingTestReturns404() {
        AppUser user = mock(AppUser.class);

        when(appUserRepository.findByGoogleId("google-user"))
                .thenReturn(Optional.of(user));

        when(bloodTestRepository.findById(TEST_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service(true).requestAnalysis(TEST_ID, jwt)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(analysisRepository, publisher);
    }


    @Test
    void foreignUsersTestReturns404() {
        authenticatedOwner();
        BloodTest test = mock(BloodTest.class);
        when(test.getUserId()).thenReturn(999L);
        when(bloodTestRepository.findById(TEST_ID))
                .thenReturn(Optional.of(test));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service(true).requestAnalysis(TEST_ID, jwt));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(analysisRepository, publisher);
    }

    @Test
    void unconfirmedTestReturns409() {
        authenticatedOwner();
        ownedTest("NEEDS_REVIEW");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service(true).requestAnalysis(TEST_ID, jwt));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(analysisRepository, publisher);
    }

    @Test
    void existingAnalysisIsReturnedWithoutEnqueue() {
        authenticatedOwner();
        ownedTest("CONFIRMED");
        BloodTestAnalysis existing = new BloodTestAnalysis(TEST_ID);
        existing.markProcessing("gpt-test", "{}");
        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.of(existing));

        var result = service(false).requestAnalysis(TEST_ID, jwt);
        assertFalse(result.newlyRequested());
        assertEquals("PROCESSING", result.status());
        verify(analysisRepository, never()).enqueueIfAbsent(anyLong());
        verifyNoInteractions(publisher);
    }

    @Test
    void disabledAnalysisReturns503WhenNotAlreadyQueued() {
        authenticatedOwner();
        ownedTest("CONFIRMED");
        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service(false).requestAnalysis(TEST_ID, jwt));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(analysisRepository, never()).enqueueIfAbsent(anyLong());
        verifyNoInteractions(publisher);
    }

    @Test
    void successfulEnqueuePublishesEvent() {
        authenticatedOwner();
        ownedTest("CONFIRMED");
        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.empty());
        when(analysisRepository.enqueueIfAbsent(TEST_ID)).thenReturn(1);

        var result = service(true).requestAnalysis(TEST_ID, jwt);
        assertTrue(result.newlyRequested());
        assertEquals("QUEUED", result.status());
        verify(publisher).publishEvent(any(BloodTestAnalysisRequestedEvent.class));
    }

    @Test
    void concurrentEnqueueReturnsExistingStatusWithoutEvent() {
        authenticatedOwner();
        ownedTest("CONFIRMED");
        BloodTestAnalysis raced = new BloodTestAnalysis(TEST_ID);
        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.empty(), Optional.of(raced));
        // enqueueIfAbsent defaults to 0: another request won the race.

        var result = service(true).requestAnalysis(TEST_ID, jwt);
        assertFalse(result.newlyRequested());
        assertEquals("QUEUED", result.status());
        verify(analysisRepository).enqueueIfAbsent(TEST_ID);
        verifyNoInteractions(publisher);
    }

    @Test
    void missingConcurrentEnqueueRecordThrowsInsteadOfPublishingEvent() {
        authenticatedOwner();
        ownedTest("CONFIRMED");
        when(analysisRepository.findByBloodTestId(TEST_ID))
                .thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class,
                () -> service(true).requestAnalysis(TEST_ID, jwt));
        verifyNoInteractions(publisher);
    }
}
