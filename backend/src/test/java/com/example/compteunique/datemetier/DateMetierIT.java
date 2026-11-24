package com.example.compteunique.datemetier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// CU-380 contre un vrai PostgreSQL : la migration V25 crée le schéma date_metier, les entités lui correspondent
// (ddl-auto: validate), et un changement de date est conservé avec sa trace.
@SpringBootTest(properties = "app.test.date-metier-modifiable=true")
@Testcontainers
class DateMetierIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private DateMetier dateMetier;

	@Test
	void unChangementEstConserveEnBaseAvecSaTrace() {
		dateMetier.changer(LocalDateTime.of(2026, 12, 31, 11, 0), "Alice");

		assertThat(dateMetier.aujourdhui()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
		assertThat(dateMetier.journal()).first().satisfies(trace -> {
			assertThat(trace.getId()).isNotNull();
			assertThat(trace.getAuteur()).isEqualTo("Alice");
		});

		dateMetier.revenirALaDateReelle("Alice");

		assertThat(dateMetier.etat().simulee()).isFalse();
		assertThat(dateMetier.journal()).hasSize(2);
	}

}
