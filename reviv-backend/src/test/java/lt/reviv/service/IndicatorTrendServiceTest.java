package lt.reviv.service;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.dto.trend.IndicatorHistoryResponse;
import lt.reviv.bloodtests.dto.trend.IndicatorOptionResponse;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.service.trend.IndicatorTrendService;
import lt.reviv.users.domain.AppUser;
import lt.reviv.users.domain.UserRole;
import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class IndicatorTrendServiceTest {
    @Mock BloodTestRepository tests;
    @Mock BloodTestIndicatorRepository indicators;
    @Mock AppUserRepository users;

    IndicatorTrendService service;
    AppUser user;
    Jwt jwt;

    @BeforeEach
    void setUp() {
        service = new IndicatorTrendService(users, tests, indicators);
        user = new AppUser("g-1", "u@example.com", "User", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 7L);
        jwt = Jwt.withTokenValue("dummy").header("alg", "none").subject("g-1").build();
        when(users.findByGoogleId("g-1")).thenReturn(Optional.of(user));
    }

    @Test
    void listIndicatorsReturnsUniqueNamesForUser() {
        BloodTest first = new BloodTest(7L, "a.pdf", 1L, "sha1", "pdf".getBytes());
        BloodTest second = new BloodTest(7L, "b.pdf", 2L, "sha2", "pdf".getBytes());
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(second, "id", 11L);
        first.finishExtraction("Lab", LocalDate.of(2024, 1, 10));
        second.finishExtraction("Lab", LocalDate.of(2024, 2, 10));

        when(tests.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(first, second));
        when(indicators.findByBloodTestIdOrderByIdAsc(10L)).thenReturn(List.of(
                new BloodTestIndicator(10L, "Vitamin D", "35", "ng/mL", "30-100"),
                new BloodTestIndicator(10L, "Iron", "70", "ug/dL", "60-150")
        ));
        when(indicators.findByBloodTestIdOrderByIdAsc(11L)).thenReturn(List.of(
                new BloodTestIndicator(11L, "Vitamin D", "42", "ng/mL", "30-100"),
                new BloodTestIndicator(11L, "B12", "480", "pg/mL", "200-900")
        ));

        List<IndicatorOptionResponse> result = service.listIndicators(jwt);

        assertEquals(3, result.size());
        assertEquals(List.of("B12", "Iron", "Vitamin D"), result.stream().map(IndicatorOptionResponse::name).sorted().toList());
    }

    @Test
    void getHistoryReturnsChronologicalPointsAndEnoughDataFlag() {
        BloodTest first = new BloodTest(7L, "a.pdf", 1L, "sha1", "pdf".getBytes());
        BloodTest second = new BloodTest(7L, "b.pdf", 2L, "sha2", "pdf".getBytes());
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(second, "id", 11L);
        first.finishExtraction("Lab", LocalDate.of(2024, 1, 10));
        second.finishExtraction("Lab", LocalDate.of(2024, 2, 10));

        BloodTestIndicator firstIndicator = new BloodTestIndicator(10L, "Vitamin D", "35", "ng/mL", "30-100");
        BloodTestIndicator secondIndicator = new BloodTestIndicator(11L, "Vitamin D", "42", "ng/mL", "30-100");
        ReflectionTestUtils.setField(firstIndicator, "id", 100L);
        ReflectionTestUtils.setField(secondIndicator, "id", 101L);

        when(tests.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(first, second));
        when(tests.findById(10L)).thenReturn(Optional.of(first));
        when(tests.findById(11L)).thenReturn(Optional.of(second));
        when(indicators.findById(100L)).thenReturn(Optional.of(firstIndicator));
        when(indicators.findByBloodTestIdOrderByIdAsc(10L)).thenReturn(List.of(firstIndicator));
        when(indicators.findByBloodTestIdOrderByIdAsc(11L)).thenReturn(List.of(secondIndicator));

        IndicatorHistoryResponse response = service.getHistory(jwt, 100L, null, null, null);

        assertEquals("Vitamin D", response.name());
        assertTrue(response.hasEnoughData());
        assertEquals(2, response.points().size());
        assertEquals(LocalDate.of(2024, 1, 10), response.points().getFirst().date());
        assertEquals("42", response.points().get(1).value());
    }

    @Test
    void getHistoryRejectsForeignIndicator() {
        BloodTest test = new BloodTest(99L, "other.pdf", 1L, "sha", "pdf".getBytes());
        ReflectionTestUtils.setField(test, "id", 20L);
        test.finishExtraction("Lab", LocalDate.of(2024, 3, 1));

        BloodTestIndicator indicator = new BloodTestIndicator(20L, "Vitamin D", "50", "ng/mL", "30-100");
        ReflectionTestUtils.setField(indicator, "id", 200L);

        when(indicators.findById(200L)).thenReturn(Optional.of(indicator));
        when(tests.findById(20L)).thenReturn(Optional.of(test));

        assertThrows(ResponseStatusException.class, () -> service.getHistory(jwt, 200L, null, null, null));
    }

    @Test
    void getHistoryReturnsSinglePointWhenOnlyOneMeasurementExists() {
        BloodTest test = new BloodTest(7L, "only.pdf", 1L, "sha", "pdf".getBytes());
        ReflectionTestUtils.setField(test, "id", 30L);
        test.finishExtraction("Lab", LocalDate.of(2024, 4, 1));

        BloodTestIndicator indicator = new BloodTestIndicator(30L, "B12", "520", "pg/mL", "200-900");
        ReflectionTestUtils.setField(indicator, "id", 300L);

        when(tests.findAllByUserIdOrderByUploadedAtDesc(7L)).thenReturn(List.of(test));
        when(indicators.findById(300L)).thenReturn(Optional.of(indicator));
        when(tests.findById(30L)).thenReturn(Optional.of(test));
        when(indicators.findByBloodTestIdOrderByIdAsc(30L)).thenReturn(List.of(indicator));

        IndicatorHistoryResponse response = service.getHistory(jwt, 300L, null, null, null);

        assertFalse(response.hasEnoughData());
        assertEquals(1, response.points().size());
        assertEquals("B12", response.name());
    }
}
