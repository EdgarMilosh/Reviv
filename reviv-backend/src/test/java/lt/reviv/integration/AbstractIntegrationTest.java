package lt.reviv.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Bendra integracinių testų bazė: visa programa su tikra PostgreSQL (Testcontainers).
 * Reikia veikiančio Docker Desktop. Duomenų bazė nauja, todėl docker compose duomenys nepaliečiami.
 */
@SpringBootTest(properties = {
        "DB_USER=test",
        "DB_PASSWORD=test",
        "reviv.admin-emails=admin@gmail.com"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}