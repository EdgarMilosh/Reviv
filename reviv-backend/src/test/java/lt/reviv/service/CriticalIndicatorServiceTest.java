package lt.reviv.service;

import lt.reviv.bloodtests.service.analysis.CriticalIndicatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static lt.reviv.bloodtests.service.analysis
        .CriticalIndicatorService.CriticalStatus.*;

class CriticalIndicatorServiceTest {

    private CriticalIndicatorService service;

    @BeforeEach
    void setUp() {
        service = new CriticalIndicatorService(List.of(
                new CriticalIndicatorService.CriticalRule(
                        "TEST_A",
                        "unitA",
                        new BigDecimal("10"),
                        new BigDecimal("100")
                ),
                new CriticalIndicatorService.CriticalRule(
                        "TEST_B",
                        "unitB",
                        new BigDecimal("20"),
                        null
                )
        ));
    }

    @Test
    void shouldDetectCriticallyLowValue() {
        var result = service.assess(
                "TEST_A", "5", "unitA"
        );

        assertEquals(CRITICAL, result);
    }

    @Test
    void shouldDetectCriticallyHighValue() {
        var result = service.assess(
                "TEST_A", "150", "unitA"
        );

        assertEquals(CRITICAL, result);
    }

    @Test
    void shouldAcceptValueBetweenCriticalLimits() {
        var result = service.assess(
                "TEST_A", "50", "unitA"
        );

        assertEquals(NOT_CRITICAL_BY_RULE, result);
    }

    @Test
    void shouldAcceptExactCriticalBoundary() {
        assertEquals(
                NOT_CRITICAL_BY_RULE,
                service.assess("TEST_A", "10", "unitA")
        );

        assertEquals(
                NOT_CRITICAL_BY_RULE,
                service.assess("TEST_A", "100", "unitA")
        );
    }

    @Test
    void shouldSupportDecimalCommas() {
        var result = service.assess(
                "TEST_A", "5,5", "unitA"
        );

        assertEquals(CRITICAL, result);
    }

    @Test
    void shouldRejectUnknownIndicator() {
        var result = service.assess(
                "UNKNOWN", "5", "unitA"
        );

        assertEquals(NOT_ASSESSED, result);
    }

    @Test
    void shouldRejectIncorrectUnit() {
        var result = service.assess(
                "TEST_A", "5", "unitB"
        );

        assertEquals(NOT_ASSESSED, result);
    }

    @Test
    void shouldRejectInvalidValue() {
        var result = service.assess(
                "TEST_A", "invalid", "unitA"
        );

        assertEquals(NOT_ASSESSED, result);
    }

    @Test
    void shouldSupportOneSidedCriticalRule() {
        assertEquals(
                CRITICAL,
                service.assess("TEST_B", "15", "unitB")
        );

        assertEquals(
                NOT_CRITICAL_BY_RULE,
                service.assess("TEST_B", "50", "unitB")
        );
    }

    @Test
    void shouldRejectDuplicateRules() {
        var duplicateService = new CriticalIndicatorService(
                List.of(
                        new CriticalIndicatorService.CriticalRule(
                                "TEST_A", "unitA",
                                BigDecimal.TEN, null
                        ),
                        new CriticalIndicatorService.CriticalRule(
                                "TEST_A", "unitA",
                                BigDecimal.ONE, null
                        )
                )
        );

        assertEquals(
                NOT_ASSESSED,
                duplicateService.assess(
                        "TEST_A", "5", "unitA"
                )
        );
    }
}
