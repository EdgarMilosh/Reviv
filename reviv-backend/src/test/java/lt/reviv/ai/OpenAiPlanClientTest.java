package lt.reviv.ai;

import lt.reviv.bloodtests.ai.OpenAiProperties;
import lt.reviv.bloodtests.ai.plan.OpenAiPlanClient;
import lt.reviv.bloodtests.ai.plan.PlanProperties;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** US-11 OpenAI responses are mocked; no network, tokens or API key required. */
class OpenAiPlanClientTest {
    private MockRestServiceServer server;
    private JsonMapper mapper;
    private OpenAiPlanClient client;
    private RestClient restClient;

    @BeforeEach
    void setup() {
        mapper = JsonMapper.builder().build();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = create(new PlanProperties(true, "gpt-6-luna", 6500), "test-only-key");
    }

    private OpenAiPlanClient create(PlanProperties config, String key) {
        return new OpenAiPlanClient(restClient,
                new OpenAiProperties(true, key, "not-used", 3, 1024, 1, 1000), config, mapper);
    }

    private List<OpenAiPlanClient.AcceptedInput> accepted() {
        return List.of(new OpenAiPlanClient.AcceptedInput(5L, "Geležis", "Geležies bisglicinatas",
                new BigDecimal("14"), "mg", 30,
                List.of(new OpenAiPlanClient.IndicatorInput(11L, "HGB", "100", "g/L", "120-155",
                        "BELOW_RANGE", new BigDecimal("16.7"))), List.of("Tik juodraštis")));
    }

    private static final String PLAN = """
            {"items":[{"supplementId":5,"dayPart":"MORNING","mealRelation":"AFTER_MEAL",
            "breakDays":null,"notes":"Informacinis tvarkaraštis"}]}
            """;

    private void respond(String json) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void output(String planJson) {
        respond(mapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of("type", "output_text", "text", planJson))
                )))));
    }

    private void incomplete(String reason) {
        respond(mapper.writeValueAsString(Map.of(
                "status", "incomplete", "incomplete_details", Map.of("reason", reason),
                "output", List.of())));
    }

    private void assertHttp(HttpStatus status, Runnable request) {
        var exception = assertThrows(ResponseStatusException.class, request::run);
        assertEquals(status, exception.getStatusCode());
    }

    @Test
    void generatesScheduleForAcceptedSupplement() {
        output(PLAN);
        var items = client.suggest(accepted(), List.of());
        assertEquals(1, items.size());
        assertEquals(5L, items.getFirst().supplementId());
        assertEquals(PlanEntry.DayPart.MORNING, items.getFirst().dayPart());
        assertEquals(PlanEntry.MealRelation.AFTER_MEAL, items.getFirst().mealRelation());
        assertNull(items.getFirst().breakDays());
        server.verify();
    }

    @Test
    void skipsReasoningOutputAndUnrelatedContent() {
        respond("""
                {"status":"completed","output":[
                {"type":"reasoning","content":[]},
                {"type":"message","content":[{"type":"refusal","text":""},
                {"type":"output_text","text":"{\\"items\\":[{\\"supplementId\\":5,\\"dayPart\\":\\"NOON\\",\\"mealRelation\\":\\"UNKNOWN\\",\\"breakDays\\":7,\\"notes\\":\\"Peržiūrai\\"}]}"}]}
                ]}
                """);
        var result = client.suggest(accepted(), List.of());
        assertEquals(PlanEntry.DayPart.NOON, result.getFirst().dayPart());
        assertEquals(7, result.getFirst().breakDays());
        server.verify();
    }

    @Test
    void disabledClientNeverCallsOpenAi() {
        assertHttp(HttpStatus.SERVICE_UNAVAILABLE,
                () -> create(new PlanProperties(false, "gpt-6-luna", 6500), "key")
                        .suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void missingApiKeyIsRejectedLocally() {
        assertHttp(HttpStatus.SERVICE_UNAVAILABLE,
                () -> create(new PlanProperties(true, "gpt-6-luna", 6500), " ")
                        .suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void nullOrEmptySupplementsAreRejectedLocally() {
        assertHttp(HttpStatus.BAD_REQUEST, () -> client.suggest(null, List.of()));
        assertHttp(HttpStatus.BAD_REQUEST, () -> client.suggest(List.of(), List.of()));
        server.verify();
    }

    @Test
    void tooManySupplementsAreRejectedLocally() {
        var twentyOne = new java.util.ArrayList<OpenAiPlanClient.AcceptedInput>();
        for (int i = 0; i < 21; i++) twentyOne.add(accepted().getFirst());
        assertHttp(HttpStatus.BAD_REQUEST, () -> client.suggest(twentyOne, List.of()));
        server.verify();
    }

    @Test
    void missingModelNameIsRejectedLocally() {
        assertHttp(HttpStatus.SERVICE_UNAVAILABLE,
                () -> create(new PlanProperties(true, " ", 6500), "key").suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void responseWithMissingItemsIsRejected() {
        output("{\"wrong\":[]}");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void unrecognizedSupplementIdIsRejected() {
        output(PLAN.replace("\"supplementId\":5", "\"supplementId\":999"));
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void duplicateSupplementIdIsRejected() {
        String one = PLAN.substring(PLAN.indexOf("{\"supplementId\""), PLAN.indexOf("}]") + 1);
        // Two valid entries for the same supplement are not allowed.
        output("{\"items\":[" + one + "," + one + "]}");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void absentAcceptedSupplementIsRejected() {
        output("{\"items\":[]}");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void tooLongOrNegativeBreakIsRejected() {
        output(PLAN.replace("\"breakDays\":null", "\"breakDays\":-1"));
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        output(PLAN.replace("\"breakDays\":null", "\"breakDays\":366"));
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void invalidTextJsonIsRejectedWithoutLeakingResponse() {
        output("not valid json");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void responseMissingOutputTextIsRejected() {
        respond("{\"status\":\"completed\",\"output\":[]}");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void incompleteWithMaxOutputTokensRetriesOnceAndSucceeds() {
        incomplete("max_output_tokens");
        output(PLAN);
        var retrying = create(new PlanProperties(true, "gpt-6-luna", 3500), "test-only-key");
        assertEquals(1, retrying.suggest(accepted(), List.of()).size());
        server.verify();
    }

    @Test
    void incompleteWithOtherReasonReturnsGatewayError() {
        incomplete("content_filter");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void incompleteAtTokenLimitIsNotRetried() {
        incomplete("max_output_tokens");
        var capped = create(new PlanProperties(true, "gpt-6-luna", 8000), "test-only-key");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> capped.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void nonCompletedStatusIsRejected() {
        respond("{\"status\":\"queued\",\"output\":[]}");
        assertHttp(HttpStatus.BAD_GATEWAY, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }

    @Test
    void httpUnauthorizedBecomesBadGatewayWithoutSecretBody() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"secret\"}}"));
        var error = assertThrows(ResponseStatusException.class, () -> client.suggest(accepted(), List.of()));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(error.getReason().contains("401"));
        assertFalse(error.getReason().contains("secret"));
        server.verify();
    }

    @Test
    void httpRateLimitIsPreservedAs429() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{}"));
        assertHttp(HttpStatus.TOO_MANY_REQUESTS, () -> client.suggest(accepted(), List.of()));
        server.verify();
    }
}
