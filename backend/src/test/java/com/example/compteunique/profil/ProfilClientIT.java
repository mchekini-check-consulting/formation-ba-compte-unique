package com.example.compteunique.profil;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// CU-10 contre un vrai PostgreSQL : la migration V26 crée le schéma profil_client, les entités lui correspondent
// (ddl-auto: validate), et chaque validation est conservée comme une version avec ses réponses.
@SpringBootTest
@Testcontainers
class ProfilClientIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ProfilKycRepository profils;

	@Autowired
	private ConsentementRepository consentements;

	@Test
	void lesVersionsEtLesConsentementsSontConservesEnBase() {
		UUID cliente = UUID.randomUUID();
		OffsetDateTime le = OffsetDateTime.parse("2026-11-24T10:00:00+01:00");
		Reponses reponses = new Reponses("PACSE", "SALARIE", "PLUS_100K", "AV_EUROS", "RETRAITE", "PLUS_8_ANS",
				"ATTENDRE", "DE_10_A_30", "AUCUNE", 52_000L, 2026, 41, 2050);
		profils.save(new ProfilKyc(cliente, 1, "V1", le, reponses, "EQUILIBRE", 2, "V1"));
		profils.save(new ProfilKyc(cliente, 2, "V1", le.plusDays(1), reponses, "EQUILIBRE", 2, "V1"));
		consentements.save(new Consentement(cliente, "UTILISATION_REPONSES", true, "V1", le));

		assertThat(profils.findByCustomerIdOrderByVersionDesc(cliente)).extracting(ProfilKyc::getVersion)
				.containsExactly(2, 1);
		assertThat(profils.findFirstByCustomerIdOrderByVersionDesc(cliente)).get().satisfies(p -> {
			assertThat(p.getReponses()).isEqualTo(reponses);
			assertThat(p.getRevaliderLe().toInstant()).isEqualTo(le.plusDays(1).plusMonths(12).toInstant());
		});
		assertThat(consentements.findAll()).singleElement().satisfies(c -> {
			assertThat(c.getType()).isEqualTo("UTILISATION_REPONSES");
			assertThat(c.isChoix()).isTrue();
		});
	}

}
