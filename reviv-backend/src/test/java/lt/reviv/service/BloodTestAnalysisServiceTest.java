
package lt.reviv.service;

import lt.reviv.bloodtests.domain.BloodTest;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.repository.BloodTestRepository;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.service.analysis.BloodTestAnalysisService;
import lt.reviv.bloodtests.service.analysis.IndicatorDeviationCalculator;
import lt.reviv.bloodtests.service.analysis.CriticalIndicatorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodTestAnalysisServiceTest {

    private static final Long TEST_ID = 5L;

    @Mock
    private BloodTestRepository bloodTestRepository;

    @Mock
    private BloodTestIndicatorRepository indicatorRepository;

    @Mock
    private IndicatorDeviationCalculator deviationCalculator;

    @Mock
    private CriticalIndicatorService criticalIndicatorService;

    private BloodTestAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new BloodTestAnalysisService(
                bloodTestRepository,
                indicatorRepository,
                deviationCalculator,
                criticalIndicatorService
        );
    }

    private void mockBloodTest(String status) {
        BloodTest bloodTest = mock(BloodTest.class);

        when(bloodTestRepository.findById(TEST_ID))
                .thenReturn(Optional.of(bloodTest));

        when(bloodTest.getStatus()).thenReturn(status);
    }

    private BloodTestIndicator baseIndicator(
            String value,
            String reference
    ) {
        BloodTestIndicator indicator =
                mock(BloodTestIndicator.class);

        when(indicator.getRawValue()).thenReturn(value);
        when(indicator.getReferenceInterval())
                .thenReturn(reference);

        return indicator;
    }

    private BloodTestIndicator abnormalIndicator(
            Long id,
            String name,
            String value,
            String unit,
            String reference
    ) {
        BloodTestIndicator indicator =
                baseIndicator(value, reference);

        when(indicator.getId()).thenReturn(id);
        when(indicator.getName()).thenReturn(name);
        when(indicator.getUnit()).thenReturn(unit);

        return indicator;
    }

    private IndicatorDeviationCalculator.DeviationResult deviation(
            IndicatorDeviationCalculator.Status status,
            String percentage
    ) {
        var result = mock(
                IndicatorDeviationCalculator.DeviationResult.class
        );

        when(result.status()).thenReturn(status);

        if (percentage != null) {
            when(result.percentage())
                    .thenReturn(new BigDecimal(percentage));
        }

        return result;
    }

    @Test
    void rejectsNonexistentBloodTest() {
        when(bloodTestRepository.findById(TEST_ID))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.prepare(TEST_ID)
        );

        assertEquals(
                "Tyrimas nerastas.",
                exception.getMessage()
        );

        verifyNoInteractions(
                indicatorRepository,
                deviationCalculator,
                criticalIndicatorService
        );
    }

    @Test
    void rejectsUnconfirmedBloodTest() {
        mockBloodTest("PROCESSING");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.prepare(TEST_ID)
        );

        assertEquals(
                "Analizuoti galima tik patvirtintą tyrimą.",
                exception.getMessage()
        );

        verifyNoInteractions(
                indicatorRepository,
                deviationCalculator,
                criticalIndicatorService
        );
    }

    @Test
    void returnsEmptyAnalysisWhenNoIndicatorsExist() {
        mockBloodTest("CONFIRMED");

        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(TEST_ID))
                .thenReturn(List.of());

        var result = service.prepare(TEST_ID);

        assertEquals(TEST_ID, result.bloodTestId());
        assertEquals(0, result.normalCount());

        assertTrue(result.forAi().isEmpty());
        assertTrue(result.critical().isEmpty());
        assertTrue(result.unknown().isEmpty());

        verifyNoInteractions(
                deviationCalculator,
                criticalIndicatorService
        );
    }

    @Test
    void countsNormalIndicatorsWithoutSendingThemForAnalysis() {
        mockBloodTest("CONFIRMED");

        BloodTestIndicator normal =
                baseIndicator("135", "120-160");

        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(TEST_ID))
                .thenReturn(List.of(normal));

        doReturn(deviation(
                IndicatorDeviationCalculator.Status.NORMAL,
                null
        )).when(deviationCalculator)
                .calculate("135", "120-160");

        var result = service.prepare(TEST_ID);

        assertEquals(1, result.normalCount());

        assertTrue(result.forAi().isEmpty());
        assertTrue(result.critical().isEmpty());
        assertTrue(result.unknown().isEmpty());

        verifyNoInteractions(criticalIndicatorService);
    }

    @Test
    void sendsBelowAndAboveRangeIndicatorsForAi() {
        mockBloodTest("CONFIRMED");

        BloodTestIndicator low = abnormalIndicator(
                21L, "HGB", "104", "g/l", "117-153"
        );

        BloodTestIndicator high = abnormalIndicator(
                22L, "RDW", "20.5", "%", "11.5-13.7"
        );

        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(TEST_ID))
                .thenReturn(List.of(low, high));

        doReturn(deviation(
                IndicatorDeviationCalculator.Status.BELOW,
                "11.1"
        )).when(deviationCalculator)
                .calculate("104", "117-153");

        doReturn(deviation(
                IndicatorDeviationCalculator.Status.ABOVE,
                "49.6"
        )).when(deviationCalculator)
                .calculate("20.5", "11.5-13.7");

        when(criticalIndicatorService.assess("HGB", "104", "g/l"))
                .thenReturn(
                        CriticalIndicatorService.CriticalStatus.NOT_ASSESSED
                );

        when(criticalIndicatorService.assess("RDW", "20.5", "%"))
                .thenReturn(
                        CriticalIndicatorService.CriticalStatus.NOT_ASSESSED
                );

        var result = service.prepare(TEST_ID);

        assertEquals(2, result.forAi().size());
        assertEquals(0, result.normalCount());

        assertTrue(result.critical().isEmpty());
        assertTrue(result.unknown().isEmpty());

        var first = result.forAi().get(0);

        assertEquals(21L, first.indicatorId());
        assertEquals("HGB", first.name());
        assertEquals("104", first.rawValue());
        assertEquals("g/l", first.unit());
        assertEquals("117-153", first.referenceInterval());

        assertEquals(
                IndicatorDeviationCalculator.Status.BELOW,
                first.status()
        );

        assertEquals(
                new BigDecimal("11.1"),
                first.deviationPercent()
        );

        var second = result.forAi().get(1);

        assertEquals(22L, second.indicatorId());
        assertEquals("RDW", second.name());

        assertEquals(
                IndicatorDeviationCalculator.Status.ABOVE,
                second.status()
        );

        assertEquals(
                new BigDecimal("49.6"),
                second.deviationPercent()
        );
    }

    @Test
    void placesUnknownIndicatorsInUnknownGroup() {
        mockBloodTest("CONFIRMED");

        BloodTestIndicator indicator = abnormalIndicator(
                23L, "Nežinomas", "?", "mg/l", "nenurodyta"
        );

        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(TEST_ID))
                .thenReturn(List.of(indicator));

        doReturn(deviation(
                IndicatorDeviationCalculator.Status.UNKNOWN,
                null
        )).when(deviationCalculator)
                .calculate("?", "nenurodyta");

        when(criticalIndicatorService.assess(
                "Nežinomas", "?", "mg/l"
        )).thenReturn(
                CriticalIndicatorService.CriticalStatus.NOT_ASSESSED
        );

        var result = service.prepare(TEST_ID);

        assertEquals(1, result.unknown().size());

        var unknown = result.unknown().get(0);

        assertEquals(23L, unknown.indicatorId());
        assertEquals(
                IndicatorDeviationCalculator.Status.UNKNOWN,
                unknown.status()
        );
        assertNull(unknown.deviationPercent());

        assertTrue(result.forAi().isEmpty());
        assertTrue(result.critical().isEmpty());
    }

    @Test
    void placesCriticalIndicatorsInCriticalGroup() {
        mockBloodTest("CONFIRMED");

        BloodTestIndicator indicator = abnormalIndicator(
                24L, "Rodiklis", "3", "mmol/l", "4-10"
        );

        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(TEST_ID))
                .thenReturn(List.of(indicator));

        doReturn(deviation(
                IndicatorDeviationCalculator.Status.BELOW,
                "25.0"
        )).when(deviationCalculator)
                .calculate("3", "4-10");

        when(criticalIndicatorService.assess(
                "Rodiklis", "3", "mmol/l"
        )).thenReturn(
                CriticalIndicatorService.CriticalStatus.CRITICAL
        );

        var result = service.prepare(TEST_ID);

        assertEquals(1, result.critical().size());

        assertTrue(result.forAi().isEmpty());
        assertTrue(result.unknown().isEmpty());

        var critical = result.critical().get(0);

        assertEquals(24L, critical.indicatorId());

        assertEquals(
                CriticalIndicatorService.CriticalStatus.CRITICAL,
                critical.criticalStatus()
        );

        assertEquals(
                new BigDecimal("25.0"),
                critical.deviationPercent()
        );
    }

    @Test
    void criticalStatusTakesPriorityOverUnknownDeviation() {
        mockBloodTest("CONFIRMED");

        BloodTestIndicator indicator = abnormalIndicator(
                25L, "Rodiklis", "?", "mg/l", "nenurodyta"
        );

        when(indicatorRepository.findByBloodTestIdOrderByIdAsc(TEST_ID))
                .thenReturn(List.of(indicator));

        doReturn(deviation(
                IndicatorDeviationCalculator.Status.UNKNOWN,
                null
        )).when(deviationCalculator)
                .calculate("?", "nenurodyta");

        when(criticalIndicatorService.assess(
                "Rodiklis", "?", "mg/l"
        )).thenReturn(
                CriticalIndicatorService.CriticalStatus.CRITICAL
        );

        var result = service.prepare(TEST_ID);

        assertEquals(1, result.critical().size());
        assertTrue(result.unknown().isEmpty());
        assertTrue(result.forAi().isEmpty());

        assertEquals(
                CriticalIndicatorService.CriticalStatus.CRITICAL,
                result.critical().get(0).criticalStatus()
        );
    }
}
