package lt.reviv.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-10 integracinis testas: katalogo API tik administratoriui, tikra PostgreSQL su demonstraciniais duomenimis. */
class SupplementApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("g-admin").claim("email", "admin@gmail.com").claim("name", "Admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor regularUser() {
        return jwt().jwt(j -> j.subject("g-user").claim("email", "naudotojas@gmail.com").claim("name", "Naudotojas"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static String uniqueName(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String json(String name, String dose, String maxDose, String distributorUrl) {
        String distributors = distributorUrl == null
                ? ""
                : "{\"name\":\"Testine parduotuve\",\"url\":\"" + distributorUrl + "\"}";
        return """
                {"name":"%s","activeSubstance":"Testine medziaga","form":"CAPSULE","doseAmount":%s,"doseUnit":"mg",
                 "maxDailyDose":%s,"missedDoseRule":"Nedvigubinti dozes","distributors":[%s]}
                """.formatted(name, dose, maxDose, distributors);
    }

    private long createSupplement(String name) throws Exception {
        String body = mvc.perform(post("/api/supplements").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, "10", "50", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void TA56_requestWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/supplements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void TA56_regularUserCannotUseCatalogApi() throws Exception {
        mvc.perform(get("/api/supplements").with(regularUser()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/supplements").with(regularUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uniqueName("Neleistinas"), "10", "50", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void TA57_adminSeesSeededCatalogWithInteractions() throws Exception {
        mvc.perform(get("/api/supplements").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(15)))
                .andExpect(jsonPath("$[?(@.name == 'Kalcis')].interactions[0].type").isNotEmpty());
    }

    @Test
    void TA57_adminCreatesReadsAndUpdatesSupplement() throws Exception {
        String name = uniqueName("Naujas");
        String body = mvc.perform(post("/api/supplements").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, "10", "50", "https://example.com/naujas")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.distributors[0].url").value("https://example.com/naujas"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(get("/api/supplements/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));

        mvc.perform(put("/api/supplements/" + id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(name, "20", "80", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doseAmount").value(20))
                .andExpect(jsonPath("$.distributors.length()").value(0));
    }

    @Test
    void TA42_duplicateNameReturns409WithFieldError() throws Exception {
        mvc.perform(post("/api/supplements").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("kalcis", "10", "50", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void TA42_zeroDoseAndMissingNameReturn400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/supplements").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "0", "50", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.doseAmount").exists());
    }

    @Test
    void TA55_invalidDistributorUrlReturns400() throws Exception {
        mvc.perform(post("/api/supplements").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(uniqueName("Su nuoroda"), "10", "50", "ne-nuoroda")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['distributors[0].url']").exists());
    }

    @Test
    void TA54_interactionIsVisibleOnBothSupplementsAndCanBeRemoved() throws Exception {
        String nameA = uniqueName("Saveika A");
        String nameB = uniqueName("Saveika B");
        long idA = createSupplement(nameA);
        long idB = createSupplement(nameB);

        String body = mvc.perform(post("/api/supplements/" + idA + "/interactions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherSupplementId\":" + idB + ",\"type\":\"Testas\",\"recommendation\":\"Vartoti skirtingu metu\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interactions[0].otherSupplementName").value(nameB))
                .andReturn().getResponse().getContentAsString();
        long interactionId = ((Number) JsonPath.read(body, "$.interactions[0].id")).longValue();

        mvc.perform(get("/api/supplements/" + idB).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interactions[0].otherSupplementName").value(nameA));

        mvc.perform(post("/api/supplements/" + idB + "/interactions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherSupplementId\":" + idA + ",\"type\":\"Testas\",\"recommendation\":\"Kitas tekstas\"}"))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/supplements/" + idB + "/interactions/" + interactionId).with(admin()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/supplements/" + idA).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interactions").isEmpty());
    }

    @Test
    void TA54_selfInteractionReturns400() throws Exception {
        long id = createSupplement(uniqueName("Pats su savimi"));

        mvc.perform(post("/api/supplements/" + id + "/interactions").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherSupplementId\":" + id + ",\"type\":\"Testas\",\"recommendation\":\"Tekstas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.otherSupplementId").exists());
    }

    @Test
    void TA43_deleteUnusedSupplementReturns204AndRemovesIt() throws Exception {
        long id = createSupplement(uniqueName("Trinamas"));

        mvc.perform(delete("/api/supplements/" + id).with(admin()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/supplements/" + id).with(admin()))
                .andExpect(status().isNotFound());
    }
}