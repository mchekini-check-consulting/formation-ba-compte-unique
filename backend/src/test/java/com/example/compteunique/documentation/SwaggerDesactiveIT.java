package com.example.compteunique.documentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

// preprod et prod (SWAGGER_ACTIF=false, helm.yaml) : les adresses de Swagger n'existent pas.
@SpringBootTest(properties = "SWAGGER_ACTIF=false")
@AutoConfigureMockMvc
@Testcontainers
class SwaggerDesactiveIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private MockMvc mvc;

	@Test
	void swaggerEstAbsent() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}

}
