package com.example.bouchonsidentite.documentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Swagger dans l'application démarrée : toutes les adresses sont décrites, avec l'adresse
// publique du gateway. make test-integration ; Docker requis.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SwaggerIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private MockMvc mvc;

	@Test
	void lesAdressesDuBackendSontDecrites() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("bouchons-identite"))
				.andExpect(jsonPath("$.paths['/sanctions-pep/controles']").exists());
	}

	@Test
	void derriereLeGatewayLesEssaisVisentLAdressePublique() throws Exception {
		mvc.perform(get("/v3/api-docs")
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-Host", "gateway.projet.dev.exemple.net")
				.header("X-Forwarded-Prefix", "/bouchons-identite"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.servers[0].url")
						.value("https://gateway.projet.dev.exemple.net/bouchons-identite"));
	}

	@Test
	void laPageSwaggerEstAccessible() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

}
