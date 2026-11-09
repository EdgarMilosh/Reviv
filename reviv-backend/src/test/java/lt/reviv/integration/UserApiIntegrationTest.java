package lt.reviv.integration;

import lt.reviv.users.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-1 integracinis testas: Spring Security taisyklės + tikra PostgreSQL (Docker, Testcontainers).
 * Reikia veikiančio Docker Desktop. Duomenų bazė kuriama kiekvieną kartą nauja, todėl
 * jūsų kūrimo duomenys (docker compose) nepaliečiami.
 */
@SpringBootTest(properties = {
        "DB_USER=test",
        "DB_PASSWORD=test",
        "reviv.admin-emails=admin@gmail.com"
})
@AutoConfigureMockMvc
@Testcontainers
class UserApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    /** Imituoja Google ID žetoną, kurį serveris jau patikrino. */
    private static RequestPostProcessor googleUser(String sub, String email, String name) {
        return jwt().jwt(j -> j.subject(sub).claim("email", email).claim("name", name));
    }

    @Test
    void TA01_meForUnregisteredUserReturns404() throws Exception {
        mvc.perform(get("/api/users/me").with(googleUser("g-new", "naujas@gmail.com", "Naujas")))
                .andExpect(status().isNotFound());
    }

    @Test
    void TA02_TA06_registerWithConsentCreatesUserAndMeReturnsIt() throws Exception {
        mvc.perform(post("/api/users/register")
                        .with(googleUser("g-1", "Jonas@Gmail.com", "Jonas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jonas@gmail.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        mvc.perform(get("/api/users/me").with(googleUser("g-1", "jonas@gmail.com", "Jonas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jonas"));

        assertEquals(1, repository.count());
    }

    @Test
    void TA03_registerWithoutConsentReturns400() throws Exception {
        mvc.perform(post("/api/users/register")
                        .with(googleUser("g-1", "jonas@gmail.com", "Jonas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.consent").exists());
        assertEquals(0, repository.count());
    }

    @Test
    void TA04_emailFromAdminListGetsAdminRole() throws Exception {
        mvc.perform(post("/api/users/register")
                        .with(googleUser("g-admin", "Admin@Gmail.com", "Admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void TA05_registeringTwiceKeepsSingleAccount() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/users/register")
                            .with(googleUser("g-1", "jonas@gmail.com", "Jonas"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"consent\":true}"))
                    .andExpect(status().isOk());
        }
        assertEquals(1, repository.count());
    }

    @Test
    void TA08_meWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void TA09_unregisteredUserCannotCallProtectedApi() throws Exception {
        mvc.perform(get("/api/tyrimai").with(googleUser("g-new", "naujas@gmail.com", "Naujas")))
                .andExpect(status().isForbidden());
    }

    @Test
    void TA13_healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}