package lt.reviv.service;

import lt.reviv.bloodtests.ai.OpenAiProperties;
import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.ExtractionIssue;
import lt.reviv.bloodtests.dto.review.MarkExtractionIssueRequest;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.ExtractionIssueRepository;
import lt.reviv.bloodtests.service.review.BloodTestCorrectionRequestedEvent;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;
import lt.reviv.common.error.UserNotFoundException;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloodTestReviewServiceTest {
    @Mock BloodTestRepository tests;
    @Mock BloodTestIndicatorRepository indicators;
    @Mock ExtractionIssueRepository issues;
    @Mock AppUserRepository users;
    @Mock ApplicationEventPublisher events;

    BloodTestReviewService service;
    BloodTest test;
    BloodTestIndicator indicator;
    AppUser user;
    Jwt jwt;

    @BeforeEach
    void init() {
        service = new BloodTestReviewService(tests, indicators, issues, users, events,
                new OpenAiProperties(true, "dummy", "gpt-5-nano", 5, 3000000, 2, 4000));
        test = new BloodTest(7L, "test.pdf", 8L, "sha", "%PDF-123".getBytes());
        ReflectionTestUtils.setField(test, "id", 6L);
        test.finishExtraction("TEST LAB", null);
        indicator = new BloodTestIndicator(6L, "HGB", "104", "g/L", "117-145");
        ReflectionTestUtils.setField(indicator, "id", 16L);
        user = new AppUser("g-1", "u@example.com", "User", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 7L);
        jwt = Jwt.withTokenValue("dummy").header("alg", "none").subject("g-1").build();
        when(users.findByGoogleId("g-1")).thenReturn(Optional.of(user));
        when(tests.findById(6L)).thenReturn(Optional.of(test));
        when(indicators.findByBloodTestIdOrderByIdAsc(6L)).thenReturn(List.of(indicator));
        when(issues.findByBloodTestIdOrderByCreatedAtAsc(6L)).thenReturn(List.of());
    }

    private ExtractionIssue issue(String field) {
        ExtractionIssue result = new ExtractionIssue(6L, 16L, field, "Incorrect extraction");
        ReflectionTestUtils.setField(result, "id", 22L);
        return result;
    }

    private void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, exception.getStatusCode());
    }

    @Test void getDetailsReturnsOwnedReportAndIndicators() {
        var detail = service.getDetails(6L, jwt);
        assertEquals(6L, detail.id());
        assertEquals("NEEDS_REVIEW", detail.status());
        assertEquals(1, detail.indicators().size());
        assertEquals("HGB", detail.indicators().getFirst().name());
    }
    @Test void getDetailsRejectsMissingUser() {
        when(users.findByGoogleId("g-1")).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.getDetails(6L, jwt));
    }
    @Test void getDetailsRejectsMissingTest() {
        when(tests.findById(6L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getDetails(6L, jwt));
    }
    @Test void getDetailsHidesAnotherUsersReport() {
        ReflectionTestUtils.setField(user, "id", 999L);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getDetails(6L, jwt));
        verifyNoInteractions(events);
    }
    @Test void markIssuePersistsValidField() {
        when(indicators.findByIdAndBloodTestId(16L, 6L)).thenReturn(Optional.of(indicator));
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of());
        var result = service.markIssue(6L, new MarkExtractionIssueRequest(16L, "VALUE", "wrong"), jwt);
        assertEquals("NEEDS_REVIEW", result.status());
        verify(issues).save(argThat(i -> i.getIndicatorId().equals(16L) &&
                "VALUE".equals(i.getFieldName()) && "wrong".equals(i.getNote())));
    }
    @Test void markIssueRejectsIncorrectTestState() {
        test.confirm();
        assertStatus(HttpStatus.CONFLICT,
                () -> service.markIssue(6L, new MarkExtractionIssueRequest(16L, "VALUE", null), jwt));
    }
    @Test void markIssueRejectsIndicatorNotBelongingToReport() {
        when(indicators.findByIdAndBloodTestId(16L, 6L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.markIssue(6L, new MarkExtractionIssueRequest(16L, "VALUE", null), jwt));
    }
    @Test void markIssueRejectsDuplicateField() {
        when(indicators.findByIdAndBloodTestId(16L, 6L)).thenReturn(Optional.of(indicator));
        when(issues.findByBloodTestIdAndResolvedFalse(6L)).thenReturn(List.of(issue("VALUE")));
        assertStatus(HttpStatus.CONFLICT,
                () -> service.markIssue(6L, new MarkExtractionIssueRequest(16L, "VALUE", null), jwt));
        verify(issues, never()).save(any());
    }
    @Test void markIssueRejectsUnknownFieldWithoutSaving() {
        when(indicators.findByIdAndBloodTestId(16L, 6L)).thenReturn(Optional.of(indicator));
        assertThrows(IllegalArgumentException.class,
                () -> service.markIssue(6L, new MarkExtractionIssueRequest(16L, "UNKNOWN", null), jwt));
        verify(issues, never()).save(any());
    }
    @Test void unmarkIssueDeletesOnlyUnresolvedIssueAndFlushes() {
        ExtractionIssue issue = issue("UNIT");
        when(issues.findByIdAndBloodTestId(22L, 6L)).thenReturn(Optional.of(issue));
        service.unmarkIssue(6L, 22L, jwt);
        verify(issues).delete(issue);
        verify(issues).flush();
    }
    @Test void unmarkIssueRejectsConfirmedTest() {
        test.confirm();
        assertStatus(HttpStatus.CONFLICT, () -> service.unmarkIssue(6L, 22L, jwt));
    }
    @Test void unmarkIssueRejectsMissingIssue() {
        when(issues.findByIdAndBloodTestId(22L, 6L)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.unmarkIssue(6L, 22L, jwt));
    }
    @Test void unmarkIssueRejectsResolvedIssue() {
        ExtractionIssue issue = issue("VALUE");
        issue.resolve();
        when(issues.findByIdAndBloodTestId(22L, 6L)).thenReturn(Optional.of(issue));
        assertStatus(HttpStatus.CONFLICT, () -> service.unmarkIssue(6L, 22L, jwt));
    }
    @Test void reextractQueuesAndPublishesEvent() {
        when(issues.existsByBloodTestIdAndResolvedFalse(6L)).thenReturn(true);
        service.requestCorrection(6L, jwt);
        assertEquals("RETRY_QUEUED", test.getStatus());
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        BloodTestCorrectionRequestedEvent event =
                assertInstanceOf(BloodTestCorrectionRequestedEvent.class, published.getValue());
        assertEquals(6L, event.bloodTestId());
    }
    @Test void reextractRejectsWhenNoIssues() {
        assertStatus(HttpStatus.CONFLICT, () -> service.requestCorrection(6L, jwt));
        verifyNoInteractions(events);
    }
    @Test void reextractRejectsWhenDisabled() {
        var disabled = new BloodTestReviewService(tests, indicators, issues, users, events,
                new OpenAiProperties(false, "", "gpt-5-nano", 5, 3000000, 2, 4000));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> disabled.requestCorrection(6L, jwt));
    }
    @Test void reextractRejectsWhenNotReady() {
        test.confirm();
        assertStatus(HttpStatus.CONFLICT, () -> service.requestCorrection(6L, jwt));
    }
    @Test void reextractRejectsWhenAttemptLimitReached() {
        when(issues.existsByBloodTestIdAndResolvedFalse(6L)).thenReturn(true);
        test.startProcessing(); test.startProcessing(); test.finishExtraction("TEST LAB", null);
        assertStatus(HttpStatus.CONFLICT, () -> service.requestCorrection(6L, jwt));
        verifyNoInteractions(events);
    }
    @Test void confirmChangesStatusWithoutErrors() {
        service.confirm(6L, jwt);
        assertEquals("CONFIRMED", test.getStatus());
    }
    @Test void confirmRejectsUnprocessed() {
        test.queueCorrection();
        assertStatus(HttpStatus.CONFLICT, () -> service.confirm(6L, jwt));
    }
    @Test void confirmRejectsEmptyIndicators() {
        when(indicators.findByBloodTestIdOrderByIdAsc(6L)).thenReturn(List.of());
        assertStatus(HttpStatus.CONFLICT, () -> service.confirm(6L, jwt));
    }
    @Test void confirmRejectsUnresolvedErrors() {
        when(issues.existsByBloodTestIdAndResolvedFalse(6L)).thenReturn(true);
        assertStatus(HttpStatus.CONFLICT, () -> service.confirm(6L, jwt));
        assertEquals("NEEDS_REVIEW", test.getStatus());
    }
    @Test void originalPdfReturnsDefensiveCopyForOwner() {
        byte[] data = service.getOriginalPdf(6L, jwt);
        assertArrayEquals("%PDF-123".getBytes(), data);
        data[0] = 0;
        assertEquals((byte)'%', test.getPdfData()[0]);
    }
    @Test void originalPdfIsNotAccessibleToOtherUser() {
        ReflectionTestUtils.setField(user, "id", 999L);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getOriginalPdf(6L, jwt));
    }
    @Test
    void listTestsReturnsEmptyListWhenUserHasNoTests() {
        when(tests.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of());

        var result = service.listTests(jwt, null);

        assertTrue(result.isEmpty());
        verify(tests).findAllByUserIdOrderByUploadedAtDesc(7L);
    }

    @Test
    void listTestsRejectsMissingUser() {
        when(users.findByGoogleId("g-1")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
            () -> service.listTests(jwt, null));

        verifyNoInteractions(tests);
    }

    @Test
    void listTestsPassesFilterWithoutErrors() {
        when(tests.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of());

        var result = service.listTests(jwt, "TEST LAB");

        assertTrue(result.isEmpty());
        verify(tests).findAllByUserIdOrderByUploadedAtDesc(7L);
    }

    @Test
    void deleteTestRejectsMissingUser() {
        when(users.findByGoogleId("g-1")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
            () -> service.deleteTest(6L, jwt));

        verify(tests, never()).findById(anyLong());
    }

    @Test
    void deleteTestRejectsMissingTest() {
        when(tests.findById(999L)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND,
            () -> service.deleteTest(999L, jwt));
    }

    @Test
    void deleteTestRejectsAnotherUsersTest() {
        ReflectionTestUtils.setField(test, "userId", 999L);

        assertStatus(HttpStatus.NOT_FOUND,
            () -> service.deleteTest(6L, jwt));

        verify(tests, never()).delete(any(BloodTest.class));
    }
}
