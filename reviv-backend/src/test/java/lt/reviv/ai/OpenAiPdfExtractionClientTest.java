package lt.reviv.ai;

import lt.reviv.bloodtests.ai.ExtractedReport;
import lt.reviv.bloodtests.ai.OpenAiPdfExtractionClient;
import lt.reviv.bloodtests.ai.OpenAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiPdfExtractionClientTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private OpenAiPdfExtractionClient client;

    private static final byte[] PDF = "%PDF-FAKE-FOR-UNIT-TEST".getBytes(StandardCharsets.UTF_8);
    private static final String EXTRACTION = """
            {"laboratory":"DEMO LAB","testDate":"2026-11-14","indicators":[
              {"name":"HGB","rawValue":"104","unit":"g/L","referenceInterval":"117-145"},
              {"name":"WBC","rawValue":"8,93","unit":"10^9/L","referenceInterval":"4-9.8"}
            ]}
            """;
    private static final String CORRECTIONS = """
            {"corrections":[{"targetId":16,"name":"HGB","rawValue":"120",
            "unit":"g/L","referenceInterval":"117-145"}]}
            """;

    private OpenAiPdfExtractionClient create(OpenAiProperties options) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenAiPdfExtractionClient(builder.build(), options, mapper);
    }
    private static OpenAiProperties options(boolean enabled, String key, int maxBytes) {
        return new OpenAiProperties(enabled, key, "gpt-5-nano", 5, maxBytes, 2, 4000);
    }
    @BeforeEach
    void init() {
        client = create(options(true, "TEST-ONLY-KEY", 3_000_000));
    }
    private String response(String json) {
        return "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":"
                + mapper.writeValueAsString(json) + "}]}]}";
    }
    private void expectJsonResponse(String json) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-ONLY-KEY"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    private static ExtractedReport.CorrectionTarget target(Long id) {
        return new ExtractedReport.CorrectionTarget(id, "HGB", "104", "g/L", "117-145", "VALUE", null);
    }

    @Test void extractsStructuredResultsAndPreservesComma() {
        expectJsonResponse(response(EXTRACTION));
        ExtractedReport report = client.extract(PDF);
        assertEquals("DEMO LAB", report.laboratory());
        assertEquals("2026-11-14", report.testDate());
        assertEquals(2, report.indicators().size());
        assertEquals("8,93", report.indicators().get(1).rawValue());
        server.verify();
    }
    @Test void correctionReturnsOnlyRequestedIds() {
        expectJsonResponse(response(CORRECTIONS));
        var corrected = client.correct(PDF, List.of(target(16L)));
        assertEquals(1, corrected.size());
        assertEquals(16L, corrected.getFirst().targetId());
        assertEquals("120", corrected.getFirst().rawValue());
        server.verify();
    }
    @Test void emptyCorrectionTargetsSkipHttp() {
        assertEquals(List.of(), client.correct(PDF, List.of()));
        assertEquals(List.of(), client.correct(PDF, null));
        server.verify();
    }
    @Test void disabledIntegrationRejectsBeforeHttp() {
        var disabled = create(options(false, "KEY", 3_000_000));
        assertThrows(IllegalStateException.class, () -> disabled.extract(PDF));
        server.verify();
    }
    @Test void blankApiKeyRejectsBeforeHttp() {
        var noKey = create(options(true, " ", 3_000_000));
        assertThrows(IllegalStateException.class, () -> noKey.extract(PDF));
        server.verify();
    }
    @Test void missingApiKeyRejectsBeforeHttp() {
        var noKey = create(options(true, null, 3_000_000));
        assertThrows(IllegalStateException.class, () -> noKey.extract(PDF));
    }
    @Test void invalidAndOversizedPdfAreRejectedWithoutHttp() {
        assertThrows(IllegalArgumentException.class, () -> client.extract(null));
        assertThrows(IllegalArgumentException.class, () -> client.extract(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> client.extract("HELLO WORLD".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> client.extract("%PDA-123".getBytes()));
        var smallLimit = create(options(true, "TEST-ONLY-KEY", 4));
        assertThrows(IllegalArgumentException.class, () -> smallLimit.extract(PDF));
        server.verify();
    }
    @Test void refusesCompletedReplyWithoutIndicators() {
        expectJsonResponse(response("{\"laboratory\":null,\"testDate\":null,\"indicators\":[]}"));
        assertThrows(IllegalStateException.class, () -> client.extract(PDF));
        server.verify();
    }
    @Test void incompleteApiResponseProvidesReasonAndTokenCounts() {
        expectJsonResponse("""
                {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},
                 "usage":{"output_tokens":4000,"output_tokens_details":{"reasoning_tokens":2500}},"output":[]}
                """);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> client.extract(PDF));
        assertTrue(exception.getMessage().contains("max_output_tokens"));
        assertTrue(exception.getMessage().contains("4000"));
        server.verify();
    }
    @Test void nullHttpBodyIsRejected() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThrows(RuntimeException.class, () -> client.extract(PDF));
        server.verify();
    }
    @Test void missingOutputJsonIsRejected() {
        expectJsonResponse("{\"status\":\"completed\",\"output\":[]}");
        assertThrows(IllegalStateException.class, () -> client.extract(PDF));
        server.verify();
    }
    @Test void skipsNonMessageOutputAndNonTextContent() {
        expectJsonResponse("{\"status\":\"completed\",\"output\":["
                + "{\"type\":\"reasoning\",\"content\":[]},"
                + "{\"type\":\"message\",\"content\":[{\"type\":\"other\"},"
                + "{\"type\":\"output_text\",\"text\":" + mapper.writeValueAsString(EXTRACTION) + "}]}]}");
        assertEquals(2, client.extract(PDF).indicators().size());
        server.verify();
    }
    @Test void malformedOutputTextIsRejected() {
        expectJsonResponse(response("NOT VALID JSON"));
        assertThrows(IllegalStateException.class, () -> client.extract(PDF));
        server.verify();
    }
    @Test void httpErrorIsWrappedWithoutLeakingBody() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("SECRET_API_ERROR"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.extract(PDF));
        assertTrue(error.getMessage().contains("401"));
        assertFalse(error.getMessage().contains("SECRET_API_ERROR"));
        server.verify();
    }
    @Test void correctionRejectsUnknownTargetId() {
        expectJsonResponse(response("""
                {"corrections":[{"targetId":999,"name":"HGB","rawValue":"120",
                  "unit":"g/L","referenceInterval":"117-145"}]}
                """));
        assertThrows(IllegalStateException.class, () -> client.correct(PDF, List.of(target(16L))));
        server.verify();
    }
    @Test void correctionRejectsMissingTarget() {
        expectJsonResponse(response("{\"corrections\":[]}"));
        assertThrows(IllegalStateException.class, () -> client.correct(PDF, List.of(target(16L))));
        server.verify();
    }
    @Test void correctionRejectsDuplicateTargetId() {
        expectJsonResponse(response("""
                {"corrections":[
                  {"targetId":16,"name":"HGB","rawValue":"120","unit":"g/L","referenceInterval":"117-145"},
                  {"targetId":16,"name":"HGB","rawValue":"121","unit":"g/L","referenceInterval":"117-145"}
                ]}
                """));
        assertThrows(IllegalStateException.class, () -> client.correct(PDF, List.of(target(16L))));
        server.verify();
    }
    @Test void correctionRejectsMissingCorrectionsArray() {
        expectJsonResponse(response("{\"corrections\":null}"));
        assertThrows(IllegalStateException.class, () -> client.correct(PDF, List.of(target(16L))));
        server.verify();
    }
}
