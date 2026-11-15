package lt.reviv.service;

import lt.reviv.push.service.PushMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-12: the JSON payload that is encrypted and sent to the browser. */
class PushMessageTest {

    @Test
    void TA49_jsonEscapesQuotesBackslashesAndNewlines() {
        PushMessage message = new PushMessage("Say \"hi\"", "line1\nline2 \\ end", "/x");

        assertEquals("{\"title\":\"Say \\\"hi\\\"\",\"body\":\"line1\\nline2 \\\\ end\",\"url\":\"/x\"}",
                message.toJson());
    }

    @Test
    void TA49_jsonEscapesControlCharacters() {
        String json = new PushMessage("a\u0001b", "tab\there", "/").toJson();

        assertTrue(json.contains("a\\u0001b"));
        assertTrue(json.contains("tab\\there"));
    }

    @Test
    void TA49_jsonKeepsLithuanianLetters() {
        String json = new PushMessage("Laikas išgerti Ąžuolas", "po valgio", "/plans/1").toJson();

        assertTrue(json.contains("Laikas išgerti Ąžuolas"));
    }
}