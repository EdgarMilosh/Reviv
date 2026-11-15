package lt.reviv.ai;

import lt.reviv.bloodtests.ai.rec.OllamaProperties;
import lt.reviv.bloodtests.ai.rec.OllamaRecommendationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Unit testai HTTP lygyje; Ollama nereikia paleisti. */
@ExtendWith(MockitoExtension.class)
class OllamaRecommendationClientTest {
    @Mock
    OllamaProperties properties;
    OllamaRecommendationClient client;
    MockRestServiceServer server;
    JsonMapper mapper;

    @BeforeEach
    void setup() {
        mapper = JsonMapper.builder().build();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OllamaRecommendationClient(builder.build(), properties, mapper);
    }

    private List<OllamaRecommendationClient.IndicatorInput> indicators() {
        return List.of(new OllamaRecommendationClient.IndicatorInput(
                16L, "HGB", "105", "g/L", "120-150", "BELOW_RANGE"));
    }

    private List<OllamaRecommendationClient.SupplementInput> catalog() {
        return List.of(new OllamaRecommendationClient.SupplementInput(
                5L, "Geležis", "Geležies bisglicinatas", "CAPSULE",
                new BigDecimal("14"), "mg", new BigDecimal("28")));
    }

    private List<OllamaRecommendationClient.Proposal> suggest(
            List<OllamaRecommendationClient.IndicatorInput> indicators,
            List<OllamaRecommendationClient.SupplementInput> catalog) {
        return client.suggest(indicators, catalog, List.of(),
                new OllamaRecommendationClient.ProfileInput(22, List.of(), List.of()));
    }

    private void enabled() { when(properties.enabled()).thenReturn(true); }

    private void ollamaResult(String innerJson) throws Exception {
        when(properties.model()).thenReturn("qwen3:14b");
        String response = mapper.writeValueAsString(Map.of(
                "done", true, "message", Map.of("content", innerJson)));
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private static final String SUGGESTED = """
            {"proposals":[{"status":"SUGGESTED", "indicatorId":16,
                "supplementId":5, "reason":"Susijęs su rodikliu",
                "proposedDailyDose":14, "doseUnit":"mg", "durationDays":30,
                "riskWarnings":[],"needsSpecialistReview":false}]}
            """;

    @Test
    void rejectsDisabledOllama() {
        when(properties.enabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
        server.verify();
    }

    @Test
    void emptyIndicatorOrCatalogSkipsNetworkRequest() {
        enabled();
        assertEquals(List.of(), suggest(List.of(), catalog()));
        assertEquals(List.of(), suggest(indicators(), List.of()));
        server.verify();
    }

    @Test
    void rejectsIndicatorsOutsideBelowRange() {
        enabled();
        var input = List.of(new OllamaRecommendationClient.IndicatorInput(
                16L, "HGB", "140", "g/L", "120-150", "WITHIN_RANGE"));
        assertThrows(IllegalArgumentException.class, () -> suggest(input, catalog()));
        server.verify();
    }

    @Test
    void rejectsTooManyIndicators() {
        enabled();
        var many = new ArrayList<OllamaRecommendationClient.IndicatorInput>();
        for (long i = 1; i <= 21; i++) {
            many.add(new OllamaRecommendationClient.IndicatorInput(i, "HGB", "100", "g/L", "120-150", "BELOW_RANGE"));
        }
        assertThrows(IllegalArgumentException.class, () -> suggest(many, catalog()));
    }

    @Test
    void rejectsTooManyCatalogSupplements() {
        enabled();
        var many = new ArrayList<OllamaRecommendationClient.SupplementInput>();
        for (long i = 1; i <= 201; i++) {
            many.add(new OllamaRecommendationClient.SupplementInput(i, "Supplement " + i,
                    "X", "CAPSULE", BigDecimal.ONE, "mg", BigDecimal.TEN));
        }
        assertThrows(IllegalArgumentException.class, () -> suggest(indicators(), many));
    }

    @Test
    void acceptsValidSuggestedProposal() throws Exception {
        enabled(); ollamaResult(SUGGESTED);
        var proposals = suggest(indicators(), catalog());
        assertEquals(1, proposals.size());
        assertEquals("SUGGESTED", proposals.getFirst().status());
        assertEquals(16L, proposals.getFirst().indicatorId());
        assertEquals(5L, proposals.getFirst().supplementId());
        assertEquals(new BigDecimal("14"), proposals.getFirst().proposedDailyDose());
        server.verify();
    }

    @Test
    void acceptsValidNotRecommendedDecision() throws Exception {
        enabled();
        ollamaResult("""
            {"proposals":[{"status":"NOT_RECOMMENDED","indicatorId":16,
              "supplementId":null,"reason":"Nėra tinkamo papildo",
              "proposedDailyDose":null,"doseUnit":null,"durationDays":null,
              "riskWarnings":[],"needsSpecialistReview":false}]}
            """);
        var result = suggest(indicators(), catalog());
        assertEquals("NOT_RECOMMENDED", result.getFirst().status());
        assertNull(result.getFirst().supplementId());
        server.verify();
    }

    @Test
    void acceptsNeedsReviewWithExplicitFlag() throws Exception {
        enabled();
        ollamaResult("""
            {"proposals":[{"status":"NEEDS_REVIEW","indicatorId":16,
              "supplementId":null,"reason":"Reikia specialisto",
              "proposedDailyDose":null,"doseUnit":null,"durationDays":null,
              "riskWarnings":["Neaiški sąveika"],"needsSpecialistReview":true}]}
            """);
        var result = suggest(indicators(), catalog());
        assertTrue(result.getFirst().needsSpecialistReview());
        server.verify();
    }

    @Test
    void rejectsNonexistentSupplementId() throws Exception {
        enabled(); ollamaResult(SUGGESTED.replace("\"supplementId\":5", "\"supplementId\":999"));
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsUnknownIndicatorId() throws Exception {
        enabled(); ollamaResult(SUGGESTED.replace("\"indicatorId\":16", "\"indicatorId\":999"));
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsDuplicateIndicatorDecisions() throws Exception {
        enabled();
        var duplicate = SUGGESTED.replace("]}", "," + SUGGESTED.substring(
                SUGGESTED.indexOf('[') + 1, SUGGESTED.lastIndexOf(']')) + "]}");
        ollamaResult(duplicate);
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsMissingDecisionForOneIndicator() throws Exception {
        enabled(); ollamaResult("{\"proposals\":[]}");
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsUnknownDecisionStatus() throws Exception {
        enabled(); ollamaResult(SUGGESTED.replace("SUGGESTED", "UNKNOWN"));
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsNeedsReviewWithoutSpecialistFlag() throws Exception {
        enabled();
        ollamaResult("""
            {"proposals":[{"status":"NEEDS_REVIEW","indicatorId":16,
              "supplementId":null,"reason":"Reikia specialisto",
              "proposedDailyDose":null,"doseUnit":null,"durationDays":null,
              "riskWarnings":[],"needsSpecialistReview":false}]}
            """);
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsDoseOnNotRecommendedItem() throws Exception {
        enabled();
        ollamaResult("""
            {"proposals":[{"status":"NOT_RECOMMENDED","indicatorId":16,
              "supplementId":null,"reason":"Netinka",
              "proposedDailyDose":14,"doseUnit":null,"durationDays":null,
              "riskWarnings":[],"needsSpecialistReview":false}]}
            """);
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsEmptyOrIncompleteResponse() throws Exception {
        enabled(); when(properties.model()).thenReturn("qwen3:14b");
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess("{\"done\":false}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsMissingProposalList() throws Exception {
        enabled(); ollamaResult("{\"notProposals\":[]}");
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void rejectsBlankResponseMessage() throws Exception {
        enabled(); ollamaResult("  ");
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }

    @Test
    void handlesOllamaHttpFailure() {
        enabled(); when(properties.model()).thenReturn("qwen3:14b");
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withServerError());
        assertThrows(IllegalStateException.class, () -> suggest(indicators(), catalog()));
    }
}
